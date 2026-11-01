package com.unkittered.api.interaction;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.chat.ChatService;
import com.unkittered.api.chat.MessageModeration;
import com.unkittered.api.pet.Pet;
import com.unkittered.api.pet.PetRepository;
import com.unkittered.api.subscription.Allowances;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.messaging.MatchCreatedEvent;
import com.unkittered.api.messaging.MatchEventPublisher;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.push.PushService;
import com.unkittered.api.safety.BlockRepository;
import com.unkittered.api.realtime.RealtimeGateway;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InteractionService {

    /** Free-tier daily like allowance (mirrors the Flutter Entitlements). */

    private final LikeRepository likes;
    private final PassRepository passes;
    private final MatchRepository matches;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileMapper mapper;
    private final MatchEventPublisher matchPublisher;
    private final DiscoverService discoverService;
    private final RedisTemplate<String, Object> redis;
    private final RealtimeGateway realtime;
    private final PushService push;
    private final BlockRepository blocks;
    private final ChatService chat;
    private final PetRepository pets;

    /** At most one "someone wagged" push per member in this window; the rest just update the count. */
    static final Duration WAG_PUSH_GAP = Duration.ofHours(1);

    public InteractionService(LikeRepository likes, PassRepository passes, MatchRepository matches,
                              UserRepository users, ProfileRepository profiles, ProfileMapper mapper,
                              MatchEventPublisher matchPublisher, DiscoverService discoverService,
                              RedisTemplate<String, Object> redis, RealtimeGateway realtime, PushService push,
                              BlockRepository blocks, ChatService chat, PetRepository pets) {
        this.pets = pets;
        this.chat = chat;
        this.blocks = blocks;
        this.push = push;
        this.likes = likes;
        this.passes = passes;
        this.matches = matches;
        this.users = users;
        this.profiles = profiles;
        this.mapper = mapper;
        this.matchPublisher = matchPublisher;
        this.discoverService = discoverService;
        this.redis = redis;
        this.realtime = realtime;
    }

    /** The result of a (super)like. */
    public record LikeOutcome(boolean isMatch, boolean alreadyMatched) { }

    /**
     * Records a (super)like. Wagging someone you already liked or matched
     * with (say, from their reel) is a no-op and costs nothing.
     */
    @Transactional
    public LikeOutcome like(UUID likerId, UUID likeeId, boolean superLike) {
        return like(likerId, likeeId, superLike, null, null, null);
    }

    /** Longest pet note a super-like can carry. */
    static final int MAX_NOTE = 300;

    /**
     * A (super)like, optionally with a pet note. The note is checked like a
     * chat message now (so a refusal costs nothing) and posted as the sender's
     * first message when they match, straight away if this makes the match.
     */
    @Transactional
    public LikeOutcome like(UUID likerId, UUID likeeId, boolean superLike, String rawNote,
                            String fromPet, String toPet) {
        validateTarget(likerId, likeeId);
        String note = rawNote == null || rawNote.isBlank() ? null : rawNote.trim();
        if (note != null) {
            if (note.length() > MAX_NOTE) throw ApiException.badRequest("Keep your note under " + MAX_NOTE + " characters.");
            MessageModeration.Flag flag = MessageModeration.check(note,
                    "gold".equalsIgnoreCase(users.findById(likerId).map(User::getSubscriptionTier).orElse("free")));
            if (flag != MessageModeration.Flag.CLEAN) throw ApiException.badRequest(MessageModeration.reason(flag));
        }
        if (matches.existsByUserLowAndUserHigh(Match.low(likerId, likeeId), Match.high(likerId, likeeId))) {
            return new LikeOutcome(true, true);
        }

        boolean fresh = !likes.existsByLikerIdAndLikeeId(likerId, likeeId);
        if (fresh) {
            enforceDailyLimit(likerId, superLike);
            Like like = new Like(likerId, likeeId, superLike);
            like.setNote(note);
            if (note != null) like.setNotePets(petName(fromPet, likerId), petName(toPet, likeeId));
            likes.save(like);
        }

        boolean isMatch = false;
        // Mutual like? The other person already liked us.
        if (likes.existsByLikerIdAndLikeeId(likeeId, likerId)) {
            isMatch = createMatchIfAbsent(likerId, likeeId, superLike);
        } else if (fresh) {
            // A bare ping so the "N wagged at you" count updates live. It names
            // nobody: who it was stays behind the who-wagged entitlement.
            realtime.sendToUser(likeeId, Map.of("type", "wag"));
            notifyWag(likeeId);
        }

        discoverService.evict(likerId);
        return new LikeOutcome(isMatch, false);
    }

    /**
     * "Someone wagged at you", for when the app is closed (the "Likes" setting turns
     * it off). Like the live ping it names nobody, and it's sent at most once per
     * {@link #WAG_PUSH_GAP} so a popular profile isn't buzzed for every wag.
     */
    /**
     * The pet a note is from or to: what the app sent, else the person's first
     * animal, else their first name (people can send notes without pets).
     */
    private String petName(String given, UUID owner) {
        String name = given == null ? "" : given.trim();
        if (name.isEmpty()) {
            name = pets.findByOwnerIdOrderByPositionAsc(owner).stream().map(Pet::getName)
                    .filter(n -> n != null && !n.isBlank()).findFirst()
                    .orElseGet(() -> profiles.findById(owner).map(p -> p.getName() == null ? "" : p.getName().trim().split("\\s+")[0])
                            .filter(n -> !n.isBlank()).orElse("A friend"));
        }
        return name.length() > 30 ? name.substring(0, 30) : name;
    }

    private void notifyWag(UUID likeeId) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent("wagpush:" + likeeId, 1, WAG_PUSH_GAP);
            if (!Boolean.TRUE.equals(first)) return;
        } catch (Exception e) {
            return;   // no Redis, no push: never fail the wag over a notification
        }
        push.sendToUser(likeeId, "Someone wagged at you 🐾", "Wag back and if it's mutual, you can start chatting.",
                Map.of("type", "like"));
    }

    @Transactional
    public void pass(UUID passerId, UUID passeeId) {
        validateTarget(passerId, passeeId);
        if (!passes.existsByPasserIdAndPasseeId(passerId, passeeId)) {
            passes.save(new Pass(passerId, passeeId));
        }
        discoverService.evict(passerId);
    }

    @Transactional(readOnly = true)
    /**
     * Who liked the viewer. Members whose tier includes it get real profiles;
     * everyone else gets anonymous teasers, so nothing identifying is ever sent
     * just to be blurred on the phone.
     */
    public List<ProfileDto> whoLikedMe(UUID viewerId) {
        boolean entitled = users.findById(viewerId)
                .map(u -> Allowances.seesWhoLiked(u.getSubscriptionTier()))
                .orElse(false);
        return profiles.findWhoLiked(viewerId).stream()
                .map(p -> entitled ? mapper.toDto(p, null, 10.0) : mapper.teaser(p))
                .toList();
    }

    // ── internals ────────────────────────────────────────────────────────────

    private boolean createMatchIfAbsent(UUID a, UUID b, boolean superLike) {
        UUID low = Match.low(a, b);
        UUID high = Match.high(a, b);
        if (matches.existsByUserLowAndUserHigh(low, high)) {
            return true;
        }
        Match match = matches.save(Match.of(a, b));
        // Pet notes either of them left come first in the chat, oldest first.
        likes.findByLikerIdAndLikeeId(b, a).filter(l -> l.getNote() != null)
                .ifPresent(l -> chat.deliverPetNote(match.getId(), b, l.getNoteFromPet(), l.getNoteToPet(), l.getNote()));
        likes.findByLikerIdAndLikeeId(a, b).filter(l -> l.getNote() != null)
                .ifPresent(l -> chat.deliverPetNote(match.getId(), a, l.getNoteFromPet(), l.getNoteToPet(), l.getNote()));
        discoverService.evict(b);
        matchPublisher.publish(new MatchCreatedEvent(
                match.getId(), a, b, superLike, match.getCreatedAt()));
        return true;
    }

    private void validateTarget(UUID actor, UUID target) {
        if (actor.equals(target)) {
            throw ApiException.badRequest("You cannot interact with your own profile");
        }
        // Blocked either way: as far as they are concerned, the profile is gone.
        if (!profiles.existsById(target) || blocks.existsBetween(actor, target)) {
            throw ApiException.notFound("Profile not found");
        }
    }

    /**
     * Server-side quota enforcement — the authoritative backstop behind the
     * client's gating. Normal likes: free tier is capped daily, paid tiers are
     * unlimited. Super-likes: a weekly allowance that scales with tier.
     */
    private void enforceDailyLimit(UUID likerId, boolean superLike) {
        User user = users.findById(likerId)
                .orElseThrow(() -> ApiException.unauthorized("Account not found"));
        String tier = user.getSubscriptionTier() == null
                ? "free" : user.getSubscriptionTier().toLowerCase();

        if (superLike) {
            enforceSuperLikeWeekly(likerId, tier);
            return;
        }
        if (!"free".equals(tier)) {
            return; // paid tiers have unlimited likes
        }
        String key = Allowances.dailyLikesKey(likerId);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofDays(2));
        }
        if (count != null && count > Allowances.FREE_DAILY_LIKES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily like limit reached. Upgrade to Unkittered Plus for unlimited likes.");
        }
    }

    /** Weekly super-like allowance: free 1, Plus 5, Gold 10. */
    private void enforceSuperLikeWeekly(UUID likerId, String tier) {
        int limit = Allowances.weeklySuperLikes(tier);
        String key = Allowances.weeklySuperLikesKey(likerId);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofDays(8));
        }
        if (count != null && count > limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "You've used all your Super Likes this week. Upgrade for more.");
        }
    }
}

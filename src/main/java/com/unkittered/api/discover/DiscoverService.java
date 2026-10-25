package com.unkittered.api.discover;

import com.unkittered.api.pet.Pet;
import com.unkittered.api.pet.PetRepository;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

@Service
public class DiscoverService {

    private static final String CACHE_PREFIX = "discover:";
    private static final TypeReference<List<ProfileDto>> DECK = new TypeReference<>() { };

    private final ProfileRepository profiles;
    private final ProfileMapper mapper;
    private final DiscoveryPrefsRepository prefsRepo;
    private final PetRepository pets;
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper json;
    private final int deckSize;
    private final Duration cacheTtl;

    public DiscoverService(ProfileRepository profiles, ProfileMapper mapper,
                           DiscoveryPrefsRepository prefsRepo, PetRepository pets,
                           RedisTemplate<String, Object> redis, ObjectMapper json,
                           @Value("${unkittered.discover.deck-size}") int deckSize,
                           @Value("${unkittered.discover.cache-ttl-seconds}") long cacheTtlSeconds) {
        this.profiles = profiles;
        this.mapper = mapper;
        this.prefsRepo = prefsRepo;
        this.pets = pets;
        this.redis = redis;
        this.json = json;
        this.deckSize = deckSize;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    @Transactional(readOnly = true)
    public List<ProfileDto> deckFor(UUID viewerId) {
        String key = CACHE_PREFIX + viewerId;

        Object cached = redis.opsForValue().get(key);
        if (cached instanceof List<?> list) {
            // The cache stores untyped JSON, so entries come back as maps.
            List<ProfileDto> deck = json.convertValue(list, DECK);
            // Someone who paused since this deck was built disappears right away.
            Set<UUID> paused = new HashSet<>(profiles.findPausedAmong(
                    deck.stream().map(p -> UUID.fromString(p.id())).toList()));
            return paused.isEmpty() ? deck
                    : deck.stream().filter(p -> !paused.contains(UUID.fromString(p.id()))).toList();
        }

        Profile viewer = profiles.findById(viewerId).orElse(null);
        String viewerMode = viewer != null ? viewer.getConnectionMode() : "dating";
        DiscoveryPrefs prefs = prefsRepo.findById(viewerId).orElse(null);
        List<ProfileDto> deck = profiles.findDeckFor(viewerId, java.time.Instant.now()).stream()
                .filter(candidate -> !candidate.isPaused())
                .filter(candidate -> intentMatches(viewerMode, candidate.getConnectionMode()))
                .filter(candidate -> DiscoveryFilter.matches(prefs, facts(viewer, candidate, prefs)))
                .limit(deckSize)
                .map(candidate -> mapper.toDto(
                        candidate,
                        compatibility(viewer, candidate),
                        distanceKm(viewer, candidate)))
                .toList();

        // Never cache an empty deck: a user who has exhausted their deck must
        // re-query each time so that newly-joined people surface immediately
        // (otherwise they'd wait out the full cache TTL to see anyone new).
        if (!deck.isEmpty()) {
            redis.opsForValue().set(key, deck, cacheTtl);
        }
        return deck;
    }

    /** One profile as this viewer sees it (compatibility, distance). */
    @Transactional(readOnly = true)
    public ProfileDto viewOf(UUID viewerId, Profile candidate) {
        Profile viewer = profiles.findById(viewerId).orElse(null);
        return mapper.toDto(candidate, compatibility(viewer, candidate), distanceKm(viewer, candidate));
    }

    /** What the filters need to know about a candidate. Pets are only loaded
     *  when a filter actually looks at species. */
    private DiscoveryFilter.Candidate facts(Profile viewer, Profile candidate, DiscoveryPrefs prefs) {
        boolean needsSpecies = prefs != null && (!prefs.getSpecies().isEmpty() || prefs.isHasPetsOnly());
        List<String> species = needsSpecies
                ? pets.findByOwnerIdOrderByPositionAsc(candidate.getUserId()).stream().map(Pet::getSpecies).toList()
                : List.of();
        return new DiscoveryFilter.Candidate(
                candidate.getAge(),
                candidate.isHideDistance() ? -1 : distanceKm(viewer, candidate),
                candidate.isVerified(),
                candidate.isSterilisationDeclared(),
                species,
                candidate.getInterests());
    }

    /** A person on the map, at a deliberately blurred position. */
    public record MapPin(ProfileDto profile, double latitude, double longitude) { }

    public record MapView(Double latitude, Double longitude, List<MapPin> people) { }

    /** Grid the map snaps to (~2 km), plus a fixed per-person offset within it. */
    static final double GRID = 0.02;

    /**
     * People from the viewer's deck, placed on a ~2 km grid with a fixed
     * per-person offset — so repeated looks can't be averaged into an exact
     * spot. Anyone who hides their distance stays off the map entirely.
     */
    @Transactional(readOnly = true)
    public MapView map(UUID viewerId) {
        Profile viewer = profiles.findById(viewerId).orElse(null);
        List<ProfileDto> deck = deckFor(viewerId);
        Map<UUID, Profile> byId = new HashMap<>();
        profiles.findAllById(deck.stream().map(p -> UUID.fromString(p.id())).toList())
                .forEach(p -> byId.put(p.getUserId(), p));
        List<MapPin> pins = new ArrayList<>();
        for (ProfileDto dto : deck) {
            Profile p = byId.get(UUID.fromString(dto.id()));
            if (p == null || p.isHideDistance() || p.getLatitude() == null || p.getLongitude() == null) continue;
            pins.add(new MapPin(dto, fuzz(p.getLatitude(), p.getUserId(), 1), fuzz(p.getLongitude(), p.getUserId(), 2)));
        }
        return new MapView(
                viewer == null || viewer.getLatitude() == null ? null : fuzz(viewer.getLatitude(), viewerId, 1),
                viewer == null || viewer.getLongitude() == null ? null : fuzz(viewer.getLongitude(), viewerId, 2),
                pins);
    }

    static double fuzz(double value, UUID id, int salt) {
        double cell = Math.floor(value / GRID) * GRID;
        // SplitMix64 over the id: every bit of it moves the offset.
        long x = id.getMostSignificantBits() ^ (id.getLeastSignificantBits() * 0x9E3779B97F4A7C15L) ^ salt;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        double unit = (x >>> 11) * 0x1.0p-53; // 0..1, fixed for this person
        return cell + GRID * (0.1 + 0.8 * unit);
    }

    /** Invalidate a viewer's cached deck (e.g. after a like/pass changes it). */
    public void evict(UUID viewerId) {
        redis.delete(CACHE_PREFIX + viewerId);
    }

    /**
     * Reciprocal intent: show a candidate only when what the viewer wants
     * overlaps what the candidate wants. "both" overlaps with everyone.
     * <pre>
     *   viewer\cand  dating  friends  both
     *   dating         ✓        ✗      ✓
     *   friends        ✗        ✓      ✓
     *   both           ✓        ✓      ✓
     * </pre>
     */
    private static boolean intentMatches(String viewerMode, String candidateMode) {
        String v = viewerMode == null ? "dating" : viewerMode;
        String c = candidateMode == null ? "dating" : candidateMode;
        boolean viewerWantsDating = !"friends".equals(v);   // dating or both
        boolean viewerWantsFriends = !"dating".equals(v);   // friends or both
        boolean candWantsDating = !"friends".equals(c);
        boolean candWantsFriends = !"dating".equals(c);
        return (viewerWantsDating && candWantsDating) || (viewerWantsFriends && candWantsFriends);
    }

    /** 0-100 score from shared interests and lifestyle tags, plus small bonuses. */
    private Integer compatibility(Profile viewer, Profile candidate) {
        if (viewer == null) return null;
        int score = 55;
        score += 8 * overlap(viewer.getInterests(), candidate.getInterests());
        score += 6 * overlap(viewer.getLifestyleTags(), candidate.getLifestyleTags());
        if (!candidate.getPets().isEmpty() && !viewer.getPets().isEmpty()) score += 6;
        if (candidate.isVerified()) score += 4;
        return Math.min(99, score);
    }

    private int overlap(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> set = new HashSet<>(a);
        int count = 0;
        for (String s : b) if (set.contains(s)) count++;
        return count;
    }

    /** Haversine when both have coordinates; otherwise a sensible default. */
    private double distanceKm(Profile viewer, Profile candidate) {
        if (viewer == null
                || viewer.getLatitude() == null || viewer.getLongitude() == null
                || candidate.getLatitude() == null || candidate.getLongitude() == null) {
            return 10.0;
        }
        double r = 6371.0;
        double dLat = Math.toRadians(candidate.getLatitude() - viewer.getLatitude());
        double dLon = Math.toRadians(candidate.getLongitude() - viewer.getLongitude());
        double lat1 = Math.toRadians(viewer.getLatitude());
        double lat2 = Math.toRadians(candidate.getLatitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double d = 2 * r * Math.asin(Math.sqrt(h));
        return Math.round(d * 10.0) / 10.0;
    }
}

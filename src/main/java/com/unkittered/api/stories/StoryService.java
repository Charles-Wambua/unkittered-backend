package com.unkittered.api.stories;

import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.stories.StoryDtos.StoryDto;
import com.unkittered.api.stories.StoryDtos.StoryItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Builds the grouped Stories feed and stores new story frames. */
@Service
public class StoryService {

    /** Stories live for 24h. */
    private static final Duration TTL = Duration.ofHours(24);

    private final StoryRepository stories;
    private final ProfileRepository profiles;

    public StoryService(StoryRepository stories, ProfileRepository profiles) {
        this.stories = stories;
        this.profiles = profiles;
    }

    /**
     * Live stories grouped by user. The viewer's own ring (if any) sorts first;
     * everyone else follows, most-recently-active first.
     */
    @Transactional(readOnly = true)
    public List<StoryDto> feedFor(UUID viewerId) {
        Instant cutoff = Instant.now().minus(TTL);

        // Preserve per-user grouping order from the query (grouped by user_id).
        Map<UUID, List<Story>> byUser = new LinkedHashMap<>();
        for (Story s : stories.findLiveFor(viewerId, cutoff)) {
            byUser.computeIfAbsent(s.getUserId(), k -> new ArrayList<>()).add(s);
        }

        List<StoryDto> result = new ArrayList<>();
        for (var entry : byUser.entrySet()) {
            Profile p = profiles.findById(entry.getKey()).orElse(null);
            if (p == null) continue;

            List<StoryItemDto> items = entry.getValue().stream()
                    .map(s -> new StoryItemDto(
                            s.getId().toString(),
                            s.getImageUrl(),
                            s.getBody(),
                            s.getCreatedAt()))
                    .toList();

            result.add(new StoryDto(
                    p.getUserId().toString(),
                    p.getName(),
                    avatarFor(p),
                    p.getImageUrl(),
                    items,
                    false,                       // seen is tracked client-side
                    p.getUserId().equals(viewerId)));
        }

        // Own ring first, then others by their newest frame (desc).
        result.sort(Comparator
                .comparing((StoryDto d) -> d.isMine() ? 0 : 1)
                .thenComparing(d -> newestAt(d), Comparator.reverseOrder()));
        return result;
    }

    /** Post a new frame. At least one of an image URL or text must be present. */
    @Transactional
    public Story add(UUID userId, String imageUrl, String text) {
        return stories.save(new Story(userId, imageUrl, text));
    }

    /** Remove all of the user's story frames. */
    @Transactional
    public void deleteMine(UUID userId) {
        stories.deleteByUserId(userId);
    }

    private static Instant newestAt(StoryDto d) {
        Instant newest = Instant.EPOCH;
        for (StoryItemDto i : d.items()) {
            if (i.createdAt() != null && i.createdAt().isAfter(newest)) newest = i.createdAt();
        }
        return newest;
    }

    /** A ring emoji: the user's first pet token, else a paw. */
    private static String avatarFor(Profile p) {
        List<String> pets = p.getPets();
        if (pets != null && !pets.isEmpty()) {
            String first = pets.get(0).trim();
            int space = first.indexOf(' ');
            return space > 0 ? first.substring(0, space) : first;
        }
        return "🐾";
    }
}

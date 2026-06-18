package com.unkittered.api.discover;

import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

@Service
public class DiscoverService {

    private static final String CACHE_PREFIX = "discover:";

    private final ProfileRepository profiles;
    private final ProfileMapper mapper;
    private final RedisTemplate<String, Object> redis;
    private final int deckSize;
    private final Duration cacheTtl;

    public DiscoverService(ProfileRepository profiles, ProfileMapper mapper,
                           RedisTemplate<String, Object> redis,
                           @Value("${unkittered.discover.deck-size}") int deckSize,
                           @Value("${unkittered.discover.cache-ttl-seconds}") long cacheTtlSeconds) {
        this.profiles = profiles;
        this.mapper = mapper;
        this.redis = redis;
        this.deckSize = deckSize;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ProfileDto> deckFor(UUID viewerId) {
        String key = CACHE_PREFIX + viewerId;

        Object cached = redis.opsForValue().get(key);
        if (cached instanceof List<?> list) {
            return (List<ProfileDto>) list;
        }

        Profile viewer = profiles.findById(viewerId).orElse(null);
        List<ProfileDto> deck = profiles.findDeckFor(viewerId).stream()
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

    /** Invalidate a viewer's cached deck (e.g. after a like/pass changes it). */
    public void evict(UUID viewerId) {
        redis.delete(CACHE_PREFIX + viewerId);
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

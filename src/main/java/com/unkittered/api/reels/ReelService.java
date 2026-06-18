package com.unkittered.api.reels;

import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.reels.ReelDtos.ReelDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds the Reels feed and stores uploaded video profiles. */
@Service
public class ReelService {

    /** Default distance shown on reel cards when coordinates aren't available. */
    private static final double DEFAULT_DISTANCE_KM = 10.0;

    private final ReelRepository reels;
    private final ProfileRepository profiles;
    private final ProfileMapper mapper;

    public ReelService(ReelRepository reels, ProfileRepository profiles, ProfileMapper mapper) {
        this.reels = reels;
        this.profiles = profiles;
        this.mapper = mapper;
    }

    /**
     * Other people's latest reels for the discover feed. Compatibility is left
     * null — the client computes its own "Why You Match" view locally.
     */
    @Transactional(readOnly = true)
    public List<ReelDto> feedFor(UUID viewerId) {
        List<ReelDto> feed = new ArrayList<>();

        // The viewer's own reel leads the feed (so they can preview & manage it).
        reels.findFirstByUserIdOrderByCreatedAtDesc(viewerId).ifPresent(mine -> {
            Profile me = profiles.findById(viewerId).orElse(null);
            if (me != null) {
                feed.add(new ReelDto(
                        mapper.toDto(me, null, 0.0, false),
                        mine.getVideoUrl(),
                        mine.getPosterUrl(),
                        true));
            }
        });

        for (Reel reel : reels.findFeedFor(viewerId)) {
            Profile p = profiles.findById(reel.getUserId()).orElse(null);
            if (p == null) continue; // profile gone — skip the orphaned reel
            feed.add(new ReelDto(
                    mapper.toDto(p, null, DEFAULT_DISTANCE_KM),
                    reel.getVideoUrl(),
                    reel.getPosterUrl(),
                    false));
        }
        return feed;
    }

    /** Replace the user's active reel with a freshly-stored clip. */
    @Transactional
    public Reel replace(UUID userId, String videoUrl, String posterUrl) {
        reels.deleteByUserId(userId);
        return reels.save(new Reel(userId, videoUrl, posterUrl));
    }

    /** Delete the user's reel(s). */
    @Transactional
    public void deleteFor(UUID userId) {
        reels.deleteByUserId(userId);
    }
}

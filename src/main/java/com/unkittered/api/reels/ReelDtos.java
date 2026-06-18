package com.unkittered.api.reels;

import com.unkittered.api.profile.ProfileDto;

/** Wire payloads for the Reels feed. */
public final class ReelDtos {

    private ReelDtos() { }

    /** A video profile: the person plus their looping clip. Matches Flutter {@code Reel.fromJson}. */
    public record ReelDto(
            ProfileDto profile,
            String videoUrl,
            String posterUrl,
            boolean isMine) {
    }

    /** Response after uploading a reel. */
    public record UploadResponse(String videoUrl, String posterUrl) {
    }
}

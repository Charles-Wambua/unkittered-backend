package com.unkittered.api.reels;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.reels.ReelDtos.ReelDto;
import com.unkittered.api.reels.ReelDtos.UploadResponse;
import com.unkittered.api.security.CurrentUser;
import com.unkittered.api.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The Reels video-profile feed, and upload of the current user's own reel. */
@RestController
@RequestMapping("/v1")
@Tag(name = "Reels")
@SecurityRequirement(name = "bearerAuth")
public class ReelController {

    private final ReelService reels;
    private final StorageService storage;

    public ReelController(ReelService reels, StorageService storage) {
        this.reels = reels;
        this.storage = storage;
    }

    @Operation(summary = "The video-profile feed (other people's reels)")
    @GetMapping("/reels")
    public Map<String, List<ReelDto>> feed() {
        return Map.of("reels", reels.feedFor(CurrentUser.id()));
    }

    @Operation(summary = "Upload/replace the current user's reel (mp4/mov/webm)")
    @PostMapping(value = "/me/reel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "poster", required = false) MultipartFile poster) {
        UUID userId = CurrentUser.id();

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No video provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("video/")) {
            throw ApiException.badRequest("Only video uploads are allowed");
        }
        try {
            String videoUrl = storage.store(file.getBytes(), contentType, file.getOriginalFilename());
            String posterUrl = "";
            if (poster != null && !poster.isEmpty()) {
                String pType = poster.getContentType();
                if (pType != null && pType.toLowerCase().startsWith("image/")) {
                    posterUrl = storage.store(poster.getBytes(), pType, poster.getOriginalFilename());
                }
            }
            reels.replace(userId, videoUrl, posterUrl);
            return new UploadResponse(videoUrl, posterUrl);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
    }

    @Operation(summary = "Delete the current user's reel")
    @DeleteMapping("/me/reel")
    public void delete() {
        reels.deleteFor(CurrentUser.id());
    }
}

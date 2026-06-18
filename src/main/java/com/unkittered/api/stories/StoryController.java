package com.unkittered.api.stories;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.security.CurrentUser;
import com.unkittered.api.stories.StoryDtos.StoryDto;
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

/** The Stories row feed, and posting a new story frame (photo or text). */
@RestController
@RequestMapping("/v1")
@Tag(name = "Stories")
@SecurityRequirement(name = "bearerAuth")
public class StoryController {

    private final StoryService stories;
    private final StorageService storage;

    public StoryController(StoryService stories, StorageService storage) {
        this.stories = stories;
        this.storage = storage;
    }

    @Operation(summary = "Live stories (yours + others'), grouped by person")
    @GetMapping("/stories")
    public Map<String, List<StoryDto>> feed() {
        return Map.of("stories", stories.feedFor(CurrentUser.id()));
    }

    @Operation(summary = "Post a story frame: a photo (with optional caption) or text")
    @PostMapping(value = "/me/stories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> create(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text) {
        UUID userId = CurrentUser.id();

        String imageUrl = null;
        if (file != null && !file.isEmpty()) {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                throw ApiException.badRequest("Story photos must be images");
            }
            try {
                imageUrl = storage.store(file.getBytes(), contentType, file.getOriginalFilename());
            } catch (IOException e) {
                throw ApiException.badRequest("Could not read the uploaded file");
            }
        }

        boolean hasText = text != null && !text.isBlank();
        if (imageUrl == null && !hasText) {
            throw ApiException.badRequest("A story needs a photo or some text");
        }

        Story saved = stories.add(userId, imageUrl, hasText ? text.trim() : null);
        return Map.of("id", saved.getId().toString());
    }

    @Operation(summary = "Delete all of the current user's stories")
    @DeleteMapping("/me/stories")
    public void delete() {
        stories.deleteMine(CurrentUser.id());
    }
}

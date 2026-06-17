package com.unkittered.api.me;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.security.CurrentUser;
import com.unkittered.api.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Photo upload for the signed-in user. Returns a URL to drop into the profile. */
@RestController
@RequestMapping("/v1/me/photos")
@Tag(name = "Me")
@SecurityRequirement(name = "bearerAuth")
public class PhotoController {

    private final StorageService storage;

    public PhotoController(StorageService storage) {
        this.storage = storage;
    }

    public record UploadResponse(String url) { }

    @Operation(summary = "Upload a photo; returns its public URL")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        // Touch the current user so an unauthenticated call is rejected up front.
        CurrentUser.id();

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw ApiException.badRequest("Only image uploads are allowed");
        }
        try {
            String url = storage.store(file.getBytes(), contentType, file.getOriginalFilename());
            return new UploadResponse(url);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
    }
}

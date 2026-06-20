package com.unkittered.api.verification;

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
import java.util.Map;
import java.util.UUID;

/** Photo verification: submit a selfie, and read review status. */
@RestController
@RequestMapping("/v1/me/verification")
@Tag(name = "Verification")
@SecurityRequirement(name = "bearerAuth")
public class VerificationController {

    private final VerificationService verification;
    private final StorageService storage;

    public VerificationController(VerificationService verification, StorageService storage) {
        this.verification = verification;
        this.storage = storage;
    }

    @Operation(summary = "Current verification status: none | pending | verified | rejected")
    @GetMapping
    public Map<String, String> status() {
        return Map.of("status", verification.statusFor(CurrentUser.id()));
    }

    @Operation(summary = "Submit a selfie for verification review")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> submit(@RequestParam("file") MultipartFile file) {
        UUID userId = CurrentUser.id();
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No selfie provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw ApiException.badRequest("Verification selfie must be an image");
        }
        try {
            String url = storage.store(file.getBytes(), contentType, file.getOriginalFilename());
            return Map.of("status", verification.submit(userId, url));
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
    }
}

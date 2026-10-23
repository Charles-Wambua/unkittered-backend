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
    private final SterilisationService sterilisation;
    private final VouchService vouches;

    public VerificationController(VerificationService verification, StorageService storage,
                                  SterilisationService sterilisation, VouchService vouches) {
        this.verification = verification;
        this.storage = storage;
        this.sterilisation = sterilisation;
        this.vouches = vouches;
    }

    @Operation(summary = "Identity (selfie) and sterilisation-proof status, each "
            + "none | pending | verified | rejected; vouchStatus is none | verified")
    @GetMapping
    public Map<String, String> status() {
        UUID me = CurrentUser.id();
        return Map.of(
                "status", verification.statusFor(me),
                "sterilisationStatus", sterilisation.statusFor(me),
                "vouchStatus", vouches.statusFor(me));
    }

    @Operation(summary = "Submit sterilisation proof (image or PDF) for human review")
    @PostMapping(value = "/sterilisation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> submitSterilisation(@RequestParam("file") MultipartFile file) {
        UUID userId = CurrentUser.id();
        if (file == null || file.isEmpty()) throw ApiException.badRequest("No document provided");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!type.startsWith("image/") && !type.equals("application/pdf")) {
            throw ApiException.badRequest("Proof must be an image or a PDF");
        }
        try {
            String url = storage.store(file.getBytes(), type, file.getOriginalFilename());
            return Map.of("sterilisationStatus", sterilisation.submit(userId, url));
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
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

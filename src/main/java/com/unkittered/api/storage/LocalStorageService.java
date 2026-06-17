package com.unkittered.api.storage;

import com.unkittered.api.common.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Disk-backed storage for self-hosted deployments. Writes under
 * {@code unkittered.storage.local.dir} and serves files at {@code /files/**}
 * (see {@link LocalStorageWebConfig}). The returned URL is absolute, built from
 * {@code unkittered.storage.public-base-url} — set that to the server's reachable
 * address (a phone can't reach {@code localhost}).
 */
@Service
@ConditionalOnProperty(name = "unkittered.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    /** Allowed image MIME types → file extension. */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif",
            "image/heic", ".heic",
            "image/heif", ".heif");

    static final String URL_PREFIX = "/files/";

    private final Path root;
    private final String publicBaseUrl;

    public LocalStorageService(
            @Value("${unkittered.storage.local.dir}") String dir,
            @Value("${unkittered.storage.public-base-url}") String publicBaseUrl) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        // Strip any trailing slash so we can join cleanly.
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
            log.info("Local storage ready at {} (served at {})", root, publicBaseUrl + URL_PREFIX);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create storage dir " + root, e);
        }
    }

    Path root() {
        return root;
    }

    @Override
    public String store(byte[] bytes, String contentType, String originalFilename) {
        String ext = EXTENSIONS.get(contentType == null ? "" : contentType.toLowerCase());
        if (ext == null) {
            throw ApiException.badRequest("Unsupported image type: " + contentType);
        }
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
            // Defensive: a crafted name must never escape the storage root.
            throw ApiException.badRequest("Invalid file name");
        }
        try {
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file", e);
        }
        return publicBaseUrl + URL_PREFIX + name;
    }
}

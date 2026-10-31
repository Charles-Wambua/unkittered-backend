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
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /** Allowed image & video MIME types → file extension. */
    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/jpg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/heic", ".heic"),
            Map.entry("image/heif", ".heif"),
            Map.entry("video/mp4", ".mp4"),
            Map.entry("video/quicktime", ".mov"),
            Map.entry("video/webm", ".webm"));

    static final String URL_PREFIX = "/files/";
    static final String PRIVATE_PREFIX = "/private/";
    static final String PRIVATE_DIR = "private";
    /** Names this service generates: 32 hex chars and an extension. */
    static final Pattern STORED_NAME = Pattern.compile("[0-9a-f]{32}\\.[a-z0-9]{2,5}");

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

    /** Sensitive files: under the root but never served by {@link LocalStorageWebConfig}. */
    Path privateRoot() {
        return root.resolve(PRIVATE_DIR);
    }

    @Override
    public String storePrivate(byte[] bytes, String contentType, String originalFilename) {
        String type = contentType == null ? "" : contentType.toLowerCase();
        String ext = "application/pdf".equals(type) ? ".pdf" : EXTENSIONS.get(type);
        if (ext == null || type.startsWith("video/")) {
            throw ApiException.badRequest("Unsupported file type: " + contentType);
        }
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            Files.createDirectories(privateRoot());
            Files.write(privateRoot().resolve(name), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file", e);
        }
        return PRIVATE_PREFIX + name;
    }

    @Override
    public Optional<PrivateFile> readPrivate(String name) {
        if (name == null || !STORED_NAME.matcher(name).matches()) return Optional.empty();
        Path file = privateRoot().resolve(name);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(new PrivateFile(Files.readAllBytes(file), contentTypeOf(name)));
        } catch (IOException e) {
            log.warn("Could not read private file {}: {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    static String contentTypeOf(String name) {
        String ext = name.substring(name.lastIndexOf('.')).toLowerCase();
        if (".pdf".equals(ext)) return "application/pdf";
        return EXTENSIONS.entrySet().stream()
                .filter(e -> e.getValue().equals(ext))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("application/octet-stream");
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

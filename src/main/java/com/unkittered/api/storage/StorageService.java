package com.unkittered.api.storage;

import java.util.Optional;

/**
 * Stores binary blobs (profile photos, etc.) and returns a publicly-fetchable
 * URL. Implementations are selected by {@code unkittered.storage.provider}:
 * {@code local} (disk, the default for self-hosting) is wired today; an
 * {@code s3} provider can be added later behind the same flag without touching
 * callers.
 */
public interface StorageService {

    /**
     * Persist the given bytes and return an absolute URL the client can load.
     *
     * @param bytes            file contents
     * @param contentType      MIME type (used to pick an extension; must be image/*)
     * @param originalFilename original name (informational only)
     */
    String store(byte[] bytes, String contentType, String originalFilename);

    /**
     * Persist a sensitive file (verification selfie, sterilisation document)
     * that must never be publicly reachable. Returns a {@code /private/<name>}
     * reference that only admins can read, through {@link #readPrivate}.
     * Images and PDFs are accepted.
     */
    String storePrivate(byte[] bytes, String contentType, String originalFilename);

    /** A private file's bytes and MIME type, by its file name; empty if missing or invalid. */
    Optional<PrivateFile> readPrivate(String name);

    record PrivateFile(byte[] bytes, String contentType) { }
}

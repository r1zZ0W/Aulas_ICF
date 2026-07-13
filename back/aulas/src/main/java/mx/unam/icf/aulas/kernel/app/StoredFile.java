package mx.unam.icf.aulas.kernel.app;

import java.time.Instant;

/**
 * Metadata of one stored file, as returned by {@link FileStorageService#list(String)}.
 *
 * <p>A storage-agnostic value object is used instead of exposing {@code java.nio.file.Path}
 * through the port: {@code Path} would couple every caller to the local-filesystem adapter,
 * while an object-storage implementation can populate these fields directly from its listing
 * response. Bundling {@code lastModified} with the name also lets the adapter read file
 * attributes during the directory traversal itself, so callers never issue their own
 * per-file metadata syscalls.</p>
 *
 * @param filename     file name within the listed folder (no path components)
 * @param lastModified last-modification timestamp as reported by the storage backend
 *
 * @author Ithera
 * @version 1.0
 */
public record StoredFile(String filename, Instant lastModified) {}

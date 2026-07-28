package mx.unam.icf.aulas.kernel.infrastructure.services;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.app.FileStorageService;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.FileStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import mx.unam.icf.aulas.kernel.app.StoredFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@code java.nio.file}-based implementation of {@link FileStorageService}.
 *
 * <p>Stores every file under a single, system-wide root directory
 * ({@code app.storage.base-dir}), agnostic of any calling module. Each caller supplies
 * its own {@code folder} (e.g. {@code "student-lists"}) to segregate its files; the
 * effective path is always {@code <base-dir>/<folder>/<filename>}.</p>
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path rootLocation;

    // No default: app.storage.base-dir is a bare placeholder in application.properties
    // (${STORAGE_BASE_DIR}), so a missing value already fails startup before this bean is
    // even constructed. A local "./data" default here would silently resolve against
    // whatever the process's working directory happens to be in production.
    public LocalFileStorageService(@Value("${app.storage.base-dir}") String baseDir) {
        this.rootLocation = Path.of(baseDir);
    }

    /**
     * Ensures the storage root exists and is writable before the service handles any request.
     *
     * <p>{@code createDirectories} is a no-op if the directory already exists, so it alone
     * cannot detect a root that exists but is read-only for the service's OS user (e.g. a
     * misconfigured {@code chown} on the systemd deployment target). The explicit
     * {@link Files#isWritable} check below catches that case and fails startup with the path
     * in the message, instead of failing silently on the first roster upload weeks later.</p>
     */
    @PostConstruct
    void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_ERROR, "Could not initialize storage root: " + rootLocation, e);
        }

        if (!Files.isWritable(rootLocation))
            throw new FileStorageException(ErrorCode.FILE_STORAGE_ERROR, "Storage root exists but is not writable: " + rootLocation
                    + " — check ownership/permissions for the service's OS user.");
    }

    @Override
    public void store(String folder, String filename, byte[] content) {
        try {
            Path targetFolder = rootLocation.resolve(folder).normalize();
            Files.createDirectories(targetFolder);

            Path destination = targetFolder.resolve(filename).normalize();
            Files.write(destination, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_ERROR, "Failed to store file: " + folder + "/" + filename, e);
        }
    }

    @Override
    public Optional<byte[]> load(String folder, String filename) {
        Path target = rootLocation.resolve(folder).resolve(filename).normalize();
        if (!Files.exists(target))
            return Optional.empty();

        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_ERROR, "Failed to read file: " + folder + "/" + filename, e);
        }
    }

    @Override
    public boolean exists(String folder, String filename) {
        return Files.exists(rootLocation.resolve(folder).resolve(filename).normalize());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation notes: nothing that can throw sits between {@code Files.list} and the
     * {@code return} — if it did, the directory handle would leak because the caller never
     * receives the stream to close it. {@code .map()} preserves the pipeline's close handlers,
     * so closing the returned stream closes the underlying {@code DirectoryStream}. Attribute
     * reads inside the mapping wrap their {@link IOException} in {@link UncheckedIOException}
     * so a single unreadable file surfaces per-element (callers sweeping a folder catch it
     * per file instead of aborting the whole listing).</p>
     */
    @Override
    public Stream<StoredFile> list(String folder) {
        Path targetFolder = rootLocation.resolve(folder).normalize();
        if (!Files.isDirectory(targetFolder))
            return Stream.empty();

        try {
            return Files.list(targetFolder)
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            BasicFileAttributes attrs =
                                    Files.readAttributes(path, BasicFileAttributes.class);
                            return new StoredFile(
                                    path.getFileName().toString(),
                                    attrs.lastModifiedTime().toInstant());
                        } catch (IOException e) {
                            throw new UncheckedIOException(
                                    "Failed to read attributes of: " + path.getFileName(), e);
                        }
                    });
        } catch (IOException e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_ERROR, "Failed to list folder: " + folder, e);
        }
    }

    @Override
    public void delete(String folder, String filename) {
        Path target = rootLocation.resolve(folder).resolve(filename).normalize();
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_ERROR, "Failed to delete file: " + folder + "/" + filename, e);
        }
    }
}

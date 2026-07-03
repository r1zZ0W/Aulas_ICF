package mx.unam.icf.aulas.kernel.infrastructure.services;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.app.FileStorageService;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.FileStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

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

    public LocalFileStorageService(@Value("${app.storage.base-dir:./data}") String baseDir) {
        this.rootLocation = Path.of(baseDir);
    }

    /** Ensures the storage root exists before the service handles any request. */
    @PostConstruct
    void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new FileStorageException("Could not initialize storage root: " + rootLocation, e);
        }
    }

    @Override
    public void store(String folder, String filename, byte[] content) {
        try {
            Path targetFolder = rootLocation.resolve(folder).normalize();
            Files.createDirectories(targetFolder);

            Path destination = targetFolder.resolve(filename).normalize();
            Files.write(destination, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file: " + folder + "/" + filename, e);
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
            throw new FileStorageException("Failed to read file: " + folder + "/" + filename, e);
        }
    }

    @Override
    public boolean exists(String folder, String filename) {
        return Files.exists(rootLocation.resolve(folder).resolve(filename).normalize());
    }
}

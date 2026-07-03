package mx.unam.icf.aulas.kernel.app;

import java.util.Optional;

/**
 * Contract for storing and retrieving raw file content on behalf of any bounded context.
 *
 * <p>This port is intentionally generic and agnostic of any specific module: every
 * operation receives an explicit {@code folder} so the kernel never needs to know about
 * module-specific configuration namespaces (e.g. {@code app.reservations.*}). Callers own
 * the meaning of {@code folder} and {@code filename}; a typical caller resolves both from
 * its own {@code @ConfigurationProperties} component and a domain identifier (UUID).</p>
 *
 * @author Ithera
 * @version 1.0
 */
public interface FileStorageService {

    /**
     * Persists {@code content} under {@code folder/filename}, creating the folder if needed
     * and overwriting any existing file with the same name.
     *
     * @param folder   subfolder relative to the configured storage root (e.g. {@code "student-lists"})
     * @param filename target file name within {@code folder} (e.g. {@code "{uuid}.xlsx"})
     * @param content  raw file bytes to write
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.FileStorageException when the write fails
     */
    void store(String folder, String filename, byte[] content);

    /**
     * Loads the raw bytes of {@code folder/filename}, if present.
     *
     * @param folder   subfolder relative to the configured storage root
     * @param filename target file name within {@code folder}
     * @return the file content, or {@link Optional#empty()} if it does not exist
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.FileStorageException when the read fails
     */
    Optional<byte[]> load(String folder, String filename);

    /**
     * Checks whether {@code folder/filename} exists without reading its content.
     *
     * @param folder   subfolder relative to the configured storage root
     * @param filename target file name within {@code folder}
     * @return {@code true} if the file exists
     */
    boolean exists(String folder, String filename);
}

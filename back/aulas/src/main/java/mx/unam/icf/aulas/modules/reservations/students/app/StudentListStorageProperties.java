package mx.unam.icf.aulas.modules.reservations.students.app;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for where student roster {@code .xlsx} files live within the kernel's
 * global storage root.
 *
 * <p>Shares the {@code app.reservations} prefix with
 * {@link mx.unam.icf.aulas.modules.reservations.slots.app.ReservationSlotProperties} —
 * each component binds only the fields it declares, so multiple bounded-context
 * property holders can coexist under the same namespace.</p>
 *
 * <p>{@link #storageDir} is a <em>subfolder name</em> only (e.g. {@code "student-lists"}),
 * not an absolute path: it is resolved by the kernel's
 * {@link mx.unam.icf.aulas.kernel.app.FileStorageService} against the system-wide
 * {@code app.storage.base-dir} root. This keeps the kernel agnostic of any
 * reservations-specific configuration.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.reservations.storage-dir")
public class StudentListStorageProperties {

    /** Subfolder (under {@code app.storage.base-dir}) where roster files are stored. */
    private String storageDir = "student-lists";
}

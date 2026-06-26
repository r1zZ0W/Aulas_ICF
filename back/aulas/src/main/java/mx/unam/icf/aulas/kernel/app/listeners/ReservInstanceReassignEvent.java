package mx.unam.icf.aulas.kernel.app.listeners;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.events.reservations.reassigns.ReservInstanceReassignEventDTO;
import mx.unam.icf.aulas.kernel.infrastructure.services.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Application event listener that reacts to reservation reassignment events.
 *
 * <p>This listener fires <em>after</em> the reassigning transaction commits
 * ({@link TransactionPhase#AFTER_COMMIT}), which guarantees that notification emails
 * are never sent for operations that are subsequently rolled back.</p>
 *
 * <p>The actual email delivery is delegated to {@link NotificationService}, whose
 * methods carry {@code @Async} so the HTTP response thread is released immediately
 * once the listener returns.</p>
 */
@Component
@RequiredArgsConstructor
public class ReservInstanceReassignEvent {

    private final NotificationService notificationService;

    /**
     * Handles a reservation reassignment event and dispatches the reassignment
     * notifications to the teacher and all active administrators.
     *
     * @param dto event payload containing teacher info, old/new classroom, date, and admin list
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservReassign(ReservInstanceReassignEventDTO dto) {
        notificationService.notifyReservationReassigned(dto);
    }
}

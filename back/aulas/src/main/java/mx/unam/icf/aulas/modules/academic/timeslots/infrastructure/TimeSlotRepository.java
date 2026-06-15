package mx.unam.icf.aulas.modules.academic.timeslots.infrastructure;

import mx.unam.icf.aulas.modules.academic.timeslots.domain.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Integer> {}

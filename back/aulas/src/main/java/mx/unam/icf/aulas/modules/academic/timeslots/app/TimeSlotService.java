package mx.unam.icf.aulas.modules.academic.timeslots.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.modules.academic.timeslots.app.dtos.TimeSlotDTO;
import mx.unam.icf.aulas.modules.academic.timeslots.app.mappers.TimeSlotMapper;
import mx.unam.icf.aulas.modules.academic.timeslots.infrastructure.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service providing read access to the pre-seeded time slot catalog.
 *
 * <p>Time slots are fixed 30-minute blocks (IDs 1–24) covering the academic day
 * from 07:00 to 19:00. They are managed at the database level and are not created
 * or modified through this API.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class TimeSlotService {

    private final TimeSlotRepository repository;
    private final TimeSlotMapper mapper;

    /**
     * Returns all available time slots ordered by their pre-seeded ID (chronological order).
     * GET /api/v1/timeslots
     */
    @Transactional(readOnly = true)
    public List<TimeSlotDTO> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}

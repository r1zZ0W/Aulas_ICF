package mx.unam.icf.aulas.modules.reservations.instances.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.academic.timeslots.app.dtos.TimeSlotDTO;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceResponseDTO;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservationTimeframeRule;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for converting between {@link ReservInstance} entities and DTOs.
 *
 * <p>Field-name differences between the entity and the DTOs are bridged with explicit
 * {@code @Mapping} annotations:
 * <ul>
 *   <li>{@code entity.reason}       ↔ {@code dto.motivo}</li>
 *   <li>{@code entity.attendeeCount} ↔ {@code dto.numAsistentes}</li>
 * </ul>
 * The {@code toDto} direction also projects {@code classroom.name} into
 * {@code classroomName} and converts the ordered {@code slots} collection into
 * a flat {@code List<TimeSlotDTO>}.</p>
 *
 * <p>{@code timeframe} is a derived concept that requires a reference date supplied by the
 * caller (the current date resolved from the application {@code Clock} in the service layer).
 * The base {@link #toDto(ReservInstance)} leaves it {@code null}; callers must use
 * {@link #toDto(ReservInstance, LocalDate)} or {@link #toDtoList(List, LocalDate)} to get a
 * fully populated response. This mirrors {@code SemesterMapper}'s pattern and keeps the mapper
 * free of {@code LocalDate.now()} calls, so the date logic stays trivially unit-testable.</p>
 *
 * @author Ithera
 * @version 3.1
 */
@Mapper(componentModel = "spring")
public interface ReservInstanceMapper extends BaseMapper<ReservInstance, ReservInstanceRequestDTO, ReservInstanceResponseDTO> {

    /**
     * Base mapping from entity to response DTO. {@code timeframe} is intentionally left
     * {@code null} because its computation requires a reference date unknown at this level.
     * Prefer {@link #toDto(ReservInstance, LocalDate)} whenever a fully populated DTO is needed.
     */
    @Override
    @Mapping(target = "groupUuid",      source = "group.uuid")
    @Mapping(target = "userUuid",       source = "group.user.uuid")
    @Mapping(target = "userFullName",   source = "group.user", qualifiedByName = "userToFullName")
    @Mapping(target = "userUsername",   source = "group.user.username")
    @Mapping(target = "classroomUuid",  source = "classroom.uuid")
    @Mapping(target = "classroomName",  source = "classroom.name")
    @Mapping(target = "timeSlots",      source = "slots", qualifiedByName = "slotsToTimeSlotDtos")
    @Mapping(target = "timeframe",      ignore = true)
    ReservInstanceResponseDTO toDto(ReservInstance entity);

    /**
     * Date-aware mapping. Produces a fully populated {@link ReservInstanceResponseDTO} by
     * computing {@code timeframe} against the supplied {@code today} reference date.
     *
     * <p>This is the preferred overload for all service-layer calls.</p>
     *
     * @param entity the reservation instance entity to convert
     * @param today  reference date used to classify the timeframe (no system-clock coupling)
     * @return a response DTO with {@code timeframe} correctly set
     */
    default ReservInstanceResponseDTO toDto(ReservInstance entity, LocalDate today) {
        ReservInstanceResponseDTO base = toDto(entity);
        return new ReservInstanceResponseDTO(
                base.uuid(), base.groupUuid(), base.userUuid(), base.userFullName(),
                base.userUsername(), base.classroomUuid(), base.classroomName(), base.date(),
                base.status(), base.attendeeCount(), base.timeSlots(), base.createdAt(),
                base.reassigned(), base.title(),
                ReservationTimeframeRule.of(entity.getDate(), today)
        );
    }

    /**
     * Date-aware list mapping. Delegates to {@link #toDto(ReservInstance, LocalDate)} for each
     * element so every DTO in the list carries a correctly computed {@code timeframe}.
     *
     * @param entities list of reservation instance entities
     * @param today    shared reference date applied to all elements
     * @return list of fully populated response DTOs
     */
    default List<ReservInstanceResponseDTO> toDtoList(List<ReservInstance> entities, LocalDate today) {
        return entities.stream().map(e -> toDto(e, today)).toList();
    }

    @Override
    @Mapping(target = "uuid",         ignore = true)
    @Mapping(target = "group",        ignore = true)
    @Mapping(target = "classroom",    ignore = true)
    @Mapping(target = "status",       ignore = true)
    @Mapping(target = "slots",        ignore = true)
    ReservInstance toEntity(ReservInstanceRequestDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "uuid",         ignore = true)
    @Mapping(target = "group",        ignore = true)
    @Mapping(target = "classroom",    ignore = true)
    @Mapping(target = "status",       ignore = true)
    @Mapping(target = "slots",        ignore = true)
    void updateEntityFromDto(ReservInstanceRequestDTO dto, @MappingTarget ReservInstance entity);

    /**
     * Converts a list of {@link ReservSlot} entities into a flat list of {@link TimeSlotDTO}s.
     * The input list is assumed to already be ordered by {@code time_slot_id ASC}
     * (enforced by {@code @OrderBy} on {@link ReservInstance#getSlots()}).
     *
     * @param slots the ordered slot collection; {@code null} returns an empty list
     * @return list of time-slot DTOs in chronological order
     */
    @Named("slotsToTimeSlotDtos")
    default List<TimeSlotDTO> slotsToTimeSlotDtos(List<ReservSlot> slots) {
        if (slots == null || slots.isEmpty()) return List.of();
        return slots.stream()
                .map(s -> new TimeSlotDTO(
                        s.getTimeSlot().getId(),
                        s.getTimeSlot().getStartTime(),
                        s.getTimeSlot().getEndTime()))
                .collect(Collectors.toList());
    }

    /**
     * Extracts the full name from a {@link User} entity.
     * Returns the result of {@code user.getFullName()} which concatenates
     * firstName and lastNames with proper whitespace handling.
     *
     * @param user the user entity; {@code null} returns {@code null}
     * @return the user's full name, or {@code null} if user is {@code null}
     */
    @Named("userToFullName")
    default String userToFullName(User user) {
        return user != null ? user.getFullName() : null;
    }
}

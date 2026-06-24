package mx.unam.icf.aulas.modules.reservations.instances.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.academic.timeslots.app.dtos.TimeSlotDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceResponseDTO;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

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
 * @author Ithera
 * @version 3.0
 */
@Mapper(componentModel = "spring")
public interface ReservInstanceMapper extends BaseMapper<ReservInstance, ReservInstanceRequestDTO, ReservInstanceResponseDTO> {

    @Override
    @Mapping(target = "groupUuid",      source = "group.uuid")
    @Mapping(target = "classroomUuid",  source = "classroom.uuid")
    @Mapping(target = "classroomName",  source = "classroom.name")
    @Mapping(target = "timeSlots",      source = "slots", qualifiedByName = "slotsToTimeSlotDtos")
    ReservInstanceResponseDTO toDto(ReservInstance entity);

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
}

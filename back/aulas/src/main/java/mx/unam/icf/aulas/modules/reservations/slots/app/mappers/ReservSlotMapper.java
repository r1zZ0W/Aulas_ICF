package mx.unam.icf.aulas.modules.reservations.slots.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.reservations.slots.app.dtos.ReservSlotRequestDTO;
import mx.unam.icf.aulas.modules.reservations.slots.app.dtos.ReservSlotResponseDTO;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for converting between {@link ReservSlot} entities and DTOs.
 *
 * @author Ithera
 * @version 2.0
 */
@Mapper(componentModel = "spring")
public interface ReservSlotMapper extends BaseMapper<ReservSlot, ReservSlotRequestDTO, ReservSlotResponseDTO> {

    @Override
    @Mapping(target = "instanceUuid", source = "instance.uuid")
    @Mapping(target = "timeSlotId", source = "id.timeSlotId")
    @Mapping(target = "startTime", source = "timeSlot.startTime")
    @Mapping(target = "endTime", source = "timeSlot.endTime")
    ReservSlotResponseDTO toDto(ReservSlot entity);

    @Override
    @Mapping(target = "id.instanceId", source = "instanceId")
    @Mapping(target = "id.timeSlotId", source = "timeSlotId")
    @Mapping(target = "instance", ignore = true)
    @Mapping(target = "timeSlot", ignore = true)
    ReservSlot toEntity(ReservSlotRequestDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "instance", ignore = true)
    @Mapping(target = "timeSlot", ignore = true)
    void updateEntityFromDto(ReservSlotRequestDTO dto, @MappingTarget ReservSlot entity);
}

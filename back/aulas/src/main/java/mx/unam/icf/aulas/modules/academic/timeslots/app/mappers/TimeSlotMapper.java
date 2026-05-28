package mx.unam.icf.aulas.modules.academic.timeslots.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.academic.timeslots.app.dtos.TimeSlotDTO;
import mx.unam.icf.aulas.modules.academic.timeslots.domain.TimeSlot;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for converting between {@link TimeSlot} entities and {@link TimeSlotDTO}s.
 *
 * @author Ithera
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface TimeSlotMapper extends BaseMapper<TimeSlot, TimeSlotDTO, TimeSlotDTO> {

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(TimeSlotDTO dto, @MappingTarget TimeSlot entity);
}

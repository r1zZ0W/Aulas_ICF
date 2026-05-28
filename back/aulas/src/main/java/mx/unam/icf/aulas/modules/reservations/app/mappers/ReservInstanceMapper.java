package mx.unam.icf.aulas.modules.reservations.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.reservations.app.dtos.ReservInstanceRequestDTO;
import mx.unam.icf.aulas.modules.reservations.app.dtos.ReservInstanceResponseDTO;
import mx.unam.icf.aulas.modules.reservations.domain.ReservInstance;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for converting between {@link ReservInstance} entities and DTOs.
 *
 * @author Ithera
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface ReservInstanceMapper extends BaseMapper<ReservInstance, ReservInstanceRequestDTO, ReservInstanceResponseDTO> {

    @Override
    @Mapping(target = "groupUuid", source = "group.uuid")
    @Mapping(target = "classroomUuid", source = "classroom.uuid")
    ReservInstanceResponseDTO toDto(ReservInstance entity);

    @Override
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "classroom", ignore = true)
    @Mapping(target = "slots", ignore = true)
    ReservInstance toEntity(ReservInstanceRequestDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "classroom", ignore = true)
    @Mapping(target = "slots", ignore = true)
    void updateEntityFromDto(ReservInstanceRequestDTO dto, @MappingTarget ReservInstance entity);
}

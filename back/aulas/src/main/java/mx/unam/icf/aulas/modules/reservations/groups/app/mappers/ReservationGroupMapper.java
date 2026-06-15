package mx.unam.icf.aulas.modules.reservations.groups.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.reservations.groups.app.dtos.ReservationGroupRequestDTO;
import mx.unam.icf.aulas.modules.reservations.groups.app.dtos.ReservationGroupResponseDTO;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for converting between {@link ReservationGroup} entities and DTOs.
 *
 * @author Ithera
 * @version 2.0
 */
@Mapper(componentModel = "spring")
public interface ReservationGroupMapper extends BaseMapper<ReservationGroup, ReservationGroupRequestDTO, ReservationGroupResponseDTO> {

    @Override
    @Mapping(target = "userUuid", source = "user.uuid")
    @Mapping(target = "semesterName", source = "semester.name")
    ReservationGroupResponseDTO toDto(ReservationGroup entity);

    @Override
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "semester", ignore = true)
    @Mapping(target = "instances", ignore = true)
    ReservationGroup toEntity(ReservationGroupRequestDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "semester", ignore = true)
    @Mapping(target = "instances", ignore = true)
    void updateEntityFromDto(ReservationGroupRequestDTO dto, @MappingTarget ReservationGroup entity);
}

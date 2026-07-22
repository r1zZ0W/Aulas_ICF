package mx.unam.icf.aulas.modules.resources.equipment.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceRequestDTO;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceResponseDTO;
import mx.unam.icf.aulas.modules.resources.equipment.domain.Resource;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for converting between {@link Resource} entities and DTOs.
 *
 * @author Ithera
 * @version 2.0
 */
@Mapper(componentModel = "spring")
public interface ResourceMapper extends BaseMapper<Resource, ResourceRequestDTO, ResourceResponseDTO> {

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    Resource toEntity(ResourceRequestDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    void updateEntityFromDto(ResourceRequestDTO dto, @MappingTarget Resource entity);
}

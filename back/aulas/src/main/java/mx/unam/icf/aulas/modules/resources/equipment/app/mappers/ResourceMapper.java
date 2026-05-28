package mx.unam.icf.aulas.modules.resources.equipment.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceDTO;
import mx.unam.icf.aulas.modules.resources.equipment.domain.Resource;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for converting between {@link Resource} entities and {@link ResourceDTO}s.
 *
 * @author Ithera
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface ResourceMapper extends BaseMapper<Resource, ResourceDTO, ResourceDTO> {

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(ResourceDTO dto, @MappingTarget Resource entity);
}

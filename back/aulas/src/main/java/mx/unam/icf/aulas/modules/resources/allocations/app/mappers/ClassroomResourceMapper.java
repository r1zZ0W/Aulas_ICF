package mx.unam.icf.aulas.modules.resources.allocations.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.resources.allocations.app.dtos.ClassroomResourceRequestDTO;
import mx.unam.icf.aulas.modules.resources.allocations.app.dtos.ClassroomResourceResponseDTO;
import mx.unam.icf.aulas.modules.resources.allocations.domain.ClassroomResource;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for converting between {@link ClassroomResource} entities and DTOs.
 *
 * @author Ithera
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface ClassroomResourceMapper extends BaseMapper<ClassroomResource, ClassroomResourceRequestDTO, ClassroomResourceResponseDTO> {

    @Override
    @Mapping(target = "classroomUuid", source = "classroom.uuid")
    @Mapping(target = "resourceId", source = "resource.id")
    @Mapping(target = "resourceName", source = "resource.name")
    ClassroomResourceResponseDTO toDto(ClassroomResource entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "classroom", ignore = true)
    @Mapping(target = "resource", ignore = true)
    ClassroomResource toEntity(ClassroomResourceRequestDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "classroom", ignore = true)
    @Mapping(target = "resource", ignore = true)
    void updateEntityFromDto(ClassroomResourceRequestDTO dto, @MappingTarget ClassroomResource entity);
}

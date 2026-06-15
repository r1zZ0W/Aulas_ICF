package mx.unam.icf.aulas.modules.resources.classrooms.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomRequestDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomResponseDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface ClassroomMapper extends BaseMapper<Classroom, ClassroomRequestDTO, ClassroomResponseDTO> {

    @Override
    @Mapping(target = "linkedRoomUuid", source = "linkedRoom.uuid")
    ClassroomResponseDTO toDto(Classroom entity);

    @Override
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "linkedRoom", ignore = true)
    @Mapping(target = "classroomResources", ignore = true)
    Classroom toEntity(ClassroomRequestDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "linkedRoom", ignore = true)
    @Mapping(target = "classroomResources", ignore = true)
    void updateEntityFromDto(ClassroomRequestDTO dto, @MappingTarget Classroom entity);
}

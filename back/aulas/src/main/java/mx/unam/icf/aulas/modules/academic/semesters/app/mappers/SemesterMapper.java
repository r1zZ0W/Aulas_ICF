package mx.unam.icf.aulas.modules.academic.semesters.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterDTO;
import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for converting between {@link Semester} entities and {@link SemesterDTO}s.
 *
 * @author Ithera
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface SemesterMapper extends BaseMapper<Semester, SemesterDTO, SemesterDTO> {}

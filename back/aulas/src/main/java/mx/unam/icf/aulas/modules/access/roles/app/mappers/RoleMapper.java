package mx.unam.icf.aulas.modules.access.roles.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.access.roles.app.dtos.RoleDTO;
import mx.unam.icf.aulas.modules.access.roles.domain.Role;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for converting between {@link Role} entities and {@link RoleDTO}s.
 *
 * @author Ithera
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface RoleMapper extends BaseMapper<Role, RoleDTO, RoleDTO> {}

package mx.unam.icf.aulas.modules.access.users.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserResponseDTO;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for converting between {@link User} entities and DTOs.
 *
 * @author Ithera
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface UserMapper extends BaseMapper<User, UserRequestDTO, UserResponseDTO> {

    @Override
    @Mapping(target = "roleName", source = "role.name")
    UserResponseDTO toDto(User entity);

    @Override
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    User toEntity(UserRequestDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    void updateEntityFromDto(UserRequestDTO dto, @MappingTarget User entity);
}

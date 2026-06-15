package mx.unam.icf.aulas.modules.resources.classrooms.app.dtos;

import mx.unam.icf.aulas.modules.resources.classrooms.domain.ClassroomType;

import java.util.UUID;

public record ClassroomResponseDTO(
        UUID uuid,
        String name,
        Long capacity,
        ClassroomType type,
        String description,
        UUID linkedRoomUuid,
        Boolean isActive
) {}

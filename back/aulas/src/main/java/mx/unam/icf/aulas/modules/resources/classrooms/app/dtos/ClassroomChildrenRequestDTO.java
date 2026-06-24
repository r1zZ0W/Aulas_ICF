package mx.unam.icf.aulas.modules.resources.classrooms.app.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for the bulk children-assignment endpoint
 * {@code PUT /api/v1/classrooms/{uuid}/children}.
 *
 * <p>{@code childUuids} is the <em>desired full set</em> of direct child classrooms for the
 * parent. The service diffs it against the current state:
 * classrooms in the list are linked to the parent; classrooms currently linked but absent
 * from the list are unlinked ({@code linkedRoom = null}). Passing an empty list removes all
 * children. Duplicate UUIDs within the list are silently deduplicated.</p>
 *
 * @param childUuids desired set of child classroom public UUIDs (may be empty, never {@code null})
 */
public record ClassroomChildrenRequestDTO(
        @NotNull(message = "childUuids is required (pass an empty list to remove all children)")
        List<UUID> childUuids
) {}

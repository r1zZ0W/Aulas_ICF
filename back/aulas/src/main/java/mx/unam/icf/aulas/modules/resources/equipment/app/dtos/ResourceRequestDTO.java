package mx.unam.icf.aulas.modules.resources.equipment.app.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating or updating an equipment resource in the global catalog.
 *
 * @param name        unique resource name (e.g., "Proyector Epson")
 * @param description optional human-readable description of the resource
 * @param quantity    total number of units of this equipment type in the catalog
 *
 * @author Ithera
 * @version 1.0
 */
public record ResourceRequestDTO(

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(max = 50, message = "FIELD_OUT_OF_RANGE")
        String name,

        @Size(max = 255, message = "FIELD_OUT_OF_RANGE")
        String description,

        @NotNull(message = "FIELD_REQUIRED")
        @Min(value = 1, message = "FIELD_OUT_OF_RANGE")
        Integer quantity
) {}

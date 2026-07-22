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

        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "Name must be at most 50 characters")
        String name,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity
) {}

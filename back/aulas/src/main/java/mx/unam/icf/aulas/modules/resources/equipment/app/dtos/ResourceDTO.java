package mx.unam.icf.aulas.modules.resources.equipment.app.dtos;

/**
 * Payload for the {@code Resource} (equipment) catalog entity.
 *
 * <p>Used for both request and response payloads.
 * The {@code id} field is auto-generated on creation and should be omitted in create requests.</p>
 *
 * @param id          internal auto-generated identifier; ignored on create requests
 * @param name        unique equipment name (e.g., PROYECTOR, COMPUTADORA)
 * @param description optional human-readable description of the equipment
 *
 * @author Ithera
 * @version 2.0
 */
public record ResourceDTO(
        Integer id,
        String name,
        String description
) {}

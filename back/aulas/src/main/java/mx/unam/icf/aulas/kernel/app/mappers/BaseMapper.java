package mx.unam.icf.aulas.kernel.app.mappers;

import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Generic interface for mapping between entities and DTOs.
 * This interface defines the standard contract for converting between
 * domain models (entities) and request/response DTOs.
 *
 * @param <E>   The type of the entity.
 * @param <REQ> The type of the request DTO (handles both create and update).
 * @param <RES> The type of the response DTO.
 */
public interface BaseMapper<E, REQ, RES> {

    /**
     * Transforms an entity to DTO form.
     * @param entity The entity to be transformed.
     * @return A response DTO of the entity transformed.
     */
    RES toDto(E entity);

    /**
     * Transforms a request DTO to entity form.
     * @param dto The request DTO to be transformed.
     * @return An entity of the DTO transformed.
     */
    E toEntity(REQ dto);

    /**
     * Updates an existing entity using a request DTO.
     * @param dto The request DTO to be applied.
     * @param entity The target entity to update.
     */
    void updateEntityFromDto(REQ dto, @MappingTarget E entity);

    /**
     * Transforms a List of entities into a List of response DTOs.
     * @param entityList The list of entities to be transformed
     * @return A List of response DTOs of the entities transformed.
     */
    List<RES> toDtoList(List<E> entityList);

    /**
     * Transforms a List of request DTOs into a List of entities.
     * @param dtoList The list of request DTOs to be transformed.
     * @return A List of entities of the DTOs transformed.
     */
    List<E> toEntityList(List<REQ> dtoList);
}

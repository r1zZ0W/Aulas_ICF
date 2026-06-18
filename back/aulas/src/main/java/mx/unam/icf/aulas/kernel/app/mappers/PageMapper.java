package mx.unam.icf.aulas.kernel.app.mappers;

import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Utility class for converting a Spring Data {@link Page} into the
 * framework-agnostic {@link PagedResultDTO}.
 *
 * <p>Centralizes the mapping so that each service delegates to a single call
 * instead of repeating the same boilerplate. The conversion function ({@code mapFn})
 * is typically a method reference to the module's MapStruct mapper's
 * {@code toDtoList} method, keeping this class decoupled from concrete mapper types.</p>
 *
 * <p>Usage example in a service:</p>
 * <pre>{@code
 * public PagedResultDTO<UserResponseDTO> findAll(Pageable pageable) {
 *     return PageMapper.toDto(userRepository.findAll(pageable), userMapper::toDtoList);
 * }
 * }</pre>
 *
 * @author Ithera
 * @version 1.0
 * @see PagedResultDTO
 */
public final class PageMapper {

    private PageMapper() {
        // Utility class — not instantiable
    }

    /**
     * Converts a Spring Data {@link Page} of entities into a {@link PagedResultDTO} of DTOs.
     *
     * @param page  the Spring Data page returned by the repository
     * @param mapFn function that converts the list of entities in {@code page.getContent()}
     *              to a list of response DTOs (usually {@code mapper::toDtoList})
     * @param <E>   entity type
     * @param <D>   response DTO type
     * @return a populated {@link PagedResultDTO} preserving all Spring Data pagination metadata
     */
    public static <E, D> PagedResultDTO<D> toDto(Page<E> page, Function<List<E>, List<D>> mapFn) {
        return PagedResultDTO.of(
                mapFn.apply(page.getContent()),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}

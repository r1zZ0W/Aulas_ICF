package mx.unam.icf.aulas.modules.academic.semesters.app.mappers;

import mx.unam.icf.aulas.kernel.app.mappers.BaseMapper;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterRequestDTO;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterResponseDTO;
import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDate;
import java.util.List;

/**
 * MapStruct mapper for converting between {@link Semester} entities and DTOs.
 *
 * <p>{@code isActive} is a derived concept that requires a reference date supplied by the
 * caller (typically the current date obtained in the service layer). The base {@link #toDto}
 * method leaves {@code isActive} as {@code null}; callers must use
 * {@link #toDto(Semester, LocalDate)} or {@link #toDtoList(List, LocalDate)} to get a fully
 * populated response. This design keeps the entity and mapper free of {@code LocalDate.now()}
 * calls, making date logic trivially unit-testable with fixed dates.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Mapper(componentModel = "spring")
public interface SemesterMapper extends BaseMapper<Semester, SemesterRequestDTO, SemesterResponseDTO> {

    /**
     * Base mapping from entity to response DTO. {@code isActive} is intentionally left
     * {@code null} because the computation requires a reference date unknown at this level.
     * Prefer {@link #toDto(Semester, LocalDate)} whenever a fully populated DTO is needed.
     *
     * @param entity the semester entity to convert
     * @return a response DTO with {@code isActive == null}
     */
    @Override
    @Mapping(target = "isActive", ignore = true)
    SemesterResponseDTO toDto(Semester entity);

    /**
     * Date-aware mapping. Produces a fully populated {@link SemesterResponseDTO} by computing
     * {@code isActive} against the supplied {@code today} reference date.
     *
     * <p>This is the preferred overload for all service-layer calls.</p>
     *
     * @param entity the semester entity to convert
     * @param today  reference date used to determine activity (no system-clock coupling)
     * @return a response DTO with {@code isActive} correctly set
     */
    default SemesterResponseDTO toDto(Semester entity, LocalDate today) {
        SemesterResponseDTO base = toDto(entity);
        return new SemesterResponseDTO(
                base.uuid(), base.name(), base.startDate(), base.endDate(),
                isActiveOn(entity, today), base.createdAt(), base.updatedAt()
        );
    }

    /**
     * Date-aware list mapping. Delegates to {@link #toDto(Semester, LocalDate)} for each element
     * so every DTO in the list carries a correctly computed {@code isActive}.
     *
     * @param entities list of semester entities
     * @param today    shared reference date applied to all elements
     * @return list of fully populated response DTOs
     */
    default List<SemesterResponseDTO> toDtoList(List<Semester> entities, LocalDate today) {
        return entities.stream().map(e -> toDto(e, today)).toList();
    }

    /**
     * Pure, testable helper that determines whether a semester is active on a given date.
     *
     * <p>{@code true} when {@code today} falls within the inclusive
     * {@code [startDate, endDate]} range of the semester. Returns {@code false} if either
     * date field is {@code null} (guards against partially-formed entities).</p>
     *
     * @param s     the semester to evaluate
     * @param today reference date
     * @return {@code true} if the semester is active on {@code today}
     */
    static boolean isActiveOn(Semester s, LocalDate today) {
        return s.getStartDate() != null && s.getEndDate() != null
                && !today.isBefore(s.getStartDate())
                && !today.isAfter(s.getEndDate());
    }

    /**
     * Maps a request DTO to a new {@link Semester} entity.
     * System-managed fields ({@code id}, {@code uuid}, {@code createdAt}, {@code updatedAt})
     * are excluded and will be initialised by the entity or the persistence layer.
     *
     * @param dto the creation payload
     * @return a new entity populated with name, startDate, and endDate
     */
    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Semester toEntity(SemesterRequestDTO dto);

    /**
     * Applies a request DTO to an existing {@link Semester} entity in-place.
     * {@code null} values in the DTO leave the corresponding entity fields unchanged.
     * System-managed fields are never overwritten.
     *
     * @param dto    the update payload
     * @param entity the target entity to update
     */
    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(SemesterRequestDTO dto, @MappingTarget Semester entity);
}

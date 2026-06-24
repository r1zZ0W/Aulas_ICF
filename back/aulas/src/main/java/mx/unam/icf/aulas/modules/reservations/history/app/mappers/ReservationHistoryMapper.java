package mx.unam.icf.aulas.modules.reservations.history.app.mappers;

import mx.unam.icf.aulas.modules.reservations.history.app.dtos.ReservationHistoryResponseDTO;
import mx.unam.icf.aulas.modules.reservations.history.domain.ReservationHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for converting {@link ReservationHistory} entities to response DTOs.
 *
 * <p>This mapper intentionally does <strong>not</strong> extend
 * {@link mx.unam.icf.aulas.kernel.app.mappers.BaseMapper BaseMapper} because the history module
 * has no inbound request DTO — rows are created exclusively by internal service calls, never
 * from an HTTP request body. Implementing {@code toEntity} / {@code updateEntityFromDto}
 * here would be meaningless dead code.</p>
 *
 * <p>All nested-source mappings ({@code group.uuid}, {@code instance.uuid},
 * {@code performedBy.uuid}, {@code performedBy.fullName}) are null-safe by
 * MapStruct default: when the association is {@code null} the projected field is also
 * {@code null} in the DTO — no {@code NullPointerException}.</p>
 *
 * <p>{@code performedBy.fullName} is resolved via {@link mx.unam.icf.aulas.modules.access.users.domain.User#getFullName()},
 * a null-safe domain method that collapses whitespace and handles missing name parts.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface ReservationHistoryMapper {

    /**
     * Converts a single {@link ReservationHistory} entity to its response DTO.
     *
     * @param entity the history record to convert
     * @return the corresponding response DTO
     */
    @Mapping(target = "groupUuid",       source = "group.uuid")
    @Mapping(target = "instanceUuid",    source = "instance.uuid")
    @Mapping(target = "performedByUuid", source = "performedBy.uuid")
    @Mapping(target = "performedByName", source = "performedBy.fullName")
    ReservationHistoryResponseDTO toDto(ReservationHistory entity);

    /**
     * Converts a list of {@link ReservationHistory} entities to a list of response DTOs.
     *
     * @param entityList the list of history records to convert
     * @return the corresponding list of response DTOs
     */
    List<ReservationHistoryResponseDTO> toDtoList(List<ReservationHistory> entityList);
}

package mx.unam.icf.aulas.modules.reservations.instances.infrastructure;

import jakarta.persistence.criteria.*;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceFilter;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * JPA Criteria {@link Specification} factory for {@link ReservInstance} server-side filtering.
 *
 * <h3>Join strategy</h3>
 * <p>Navigating via {@code root.get("group").get("user")} would generate implicit INNER JOINs,
 * which (a) override the intended LEFT JOIN semantics and (b) force those INNER JOINs into the
 * count query as well, silently dropping rows with orphaned relations from both the result set
 * and the total count. Instead:</p>
 * <ul>
 *   <li><b>Eager loading</b> ({@code root.fetch(...)}) is applied only to the content query
 *       (skipped when {@code query.getResultType()} is {@code Long/long}), restricting it to
 *       ToOne associations to avoid the {@code HHH000104} in-memory pagination warning.</li>
 *   <li><b>Standard {@code join()} calls</b> provide the {@code Join} objects used to build
 *       predicates. Hibernate 6+ (Spring Boot 4.x) automatically consolidates a {@code join}
 *       and a {@code fetch} on the same attribute into a single {@code LEFT JOIN} in the
 *       generated SQL — no castings between {@code Fetch} and {@code Join} needed.</li>
 * </ul>
 *
 * <h3>Search performance note</h3>
 * <p>The {@code search} predicate uses {@code LIKE %term%} with a leading wildcard on four
 * paths, which cannot use column indexes (full table scan). Callers should require at least
 * three non-blank characters before activating this predicate.</p>
 *
 * @author Ithera
 * @version 1.0
 * @see ReservInstanceFilter
 */
public final class ReservInstanceSpecification {

    private ReservInstanceSpecification() {}

    /**
     * Builds a {@link Specification} from the given filter.
     * All predicates are combined with AND. A {@code null} or blank filter field
     * is silently skipped (no restriction on that dimension).
     *
     * @param f the filter criteria; must not be {@code null}
     * @return a ready-to-use {@link Specification}
     */
    public static Specification<ReservInstance> build(ReservInstanceFilter f) {
        return (root, query, cb) -> {

            Class<?> rt = query.getResultType();
            boolean isCount = (rt == Long.class || rt == long.class);

            // 1. Eager loading — content query only (ToOne only → no HHH000104)
            if (!isCount) {
                root.fetch("group", JoinType.LEFT).fetch("user", JoinType.LEFT);
                root.fetch("classroom", JoinType.LEFT);
            }

            // 2. Standard LEFT joins for predicate construction.
            //    Hibernate 6+ consolidates these with the fetches above into one LEFT JOIN each.
            Join<?, ?> groupJoin     = root.join("group",     JoinType.LEFT);
            Join<?, ?> userJoin      = groupJoin.join("user", JoinType.LEFT);
            Join<?, ?> classroomJoin = root.join("classroom", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();

            // -- search ------------------------------------------------------------------
            // LIKE %term% (case-insensitive) on classroom name, firstName, lastNames,
            // and null-safe CONCAT(firstName, ' ', lastNames).
            // COALESCE guards against NULL columns: CONCAT(NULL, …) → NULL in MySQL,
            // which would silently exclude rows that otherwise match the non-null part.
            if (f.search() != null && !f.search().isBlank()) {
                String term = "%" + f.search().trim().toLowerCase() + "%";

                Expression<String> fullName = cb.lower(cb.concat(
                        cb.concat(
                                cb.coalesce(userJoin.<String>get("firstName"), ""),
                                " "),
                        cb.coalesce(userJoin.<String>get("lastNames"), "")));

                predicates.add(cb.or(
                        cb.like(cb.lower(classroomJoin.<String>get("name")), term),
                        cb.like(cb.lower(userJoin.<String>get("firstName")),  term),
                        cb.like(cb.lower(userJoin.<String>get("lastNames")),  term),
                        cb.like(fullName, term)
                ));
            }

            // -- status ------------------------------------------------------------------
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }

            // -- reassigned --------------------------------------------------------------
            // Direct column on root — no join required.
            // null  → no restriction (returns both reassigned and non-reassigned).
            // true  → only instances flagged as reassigned by an admin (ACTIVE display hint).
            // false → only instances that were never reassigned (clean "Activa" partition).
            if (f.reassigned() != null) {
                predicates.add(cb.equal(root.get("reassigned"), f.reassigned()));
            }

            // -- classroomId -------------------------------------------------------------
            if (f.classroomId() != null) {
                predicates.add(cb.equal(classroomJoin.get("uuid"), f.classroomId()));
            }

            // -- date range --------------------------------------------------------------
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), f.to()));
            }

            // -- userUuid (set internally by the service for per-user scoping) -----------
            if (f.userUuid() != null) {
                predicates.add(cb.equal(userJoin.get("uuid"), f.userUuid()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

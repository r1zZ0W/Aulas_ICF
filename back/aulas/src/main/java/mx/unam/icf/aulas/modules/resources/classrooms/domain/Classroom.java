package mx.unam.icf.aulas.modules.resources.classrooms.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.unam.icf.aulas.kernel.domain.entities.BaseEntity;
import mx.unam.icf.aulas.modules.resources.allocations.domain.ClassroomResource;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "classrooms")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Classroom extends BaseEntity {

    @Column(name = "uuid", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID uuid = UUID.randomUUID();

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "capacity", nullable = false)
    private Long capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ClassroomType type;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_room_id")
    private Classroom linkedRoom;

    @OneToMany(mappedBy = "classroom", fetch = FetchType.LAZY)
    private List<ClassroomResource> classroomResources;

    @Column(name = "is_active")
    private Boolean isActive;
}

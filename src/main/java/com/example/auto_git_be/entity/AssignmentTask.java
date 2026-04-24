package com.example.auto_git_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "assignment_tasks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_assignment_tasks_assignment_task_name", columnNames = {"assignment_id", "task_name"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @Column(name = "task_name", nullable = false, length = 255)
    private String taskName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "order_no")
    private Integer orderNo;

    @Column(name = "bucket", length = 255)
    private String bucket;

    @Column(name = "object_key", length = 500)
    private String objectKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "assignmentTask",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<StudentTaskResult> studentTaskResults = new ArrayList<>();
}
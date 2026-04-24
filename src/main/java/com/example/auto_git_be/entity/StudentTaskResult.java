package com.example.auto_git_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table (name = "student_task_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class StudentTaskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "student_assignment_id", nullable = false)
    private StudentAssignment studentAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "assignment_task_id", nullable = false)
    private AssignmentTask assignmentTask;

    @Column(name = "language")
    private String language;

    @Column(name = "score")
    private Double score;

    @Column(name = "pass")
    private Integer pass;

    @Column(name = "total")
    private Integer total;

    @Column(name = "status")
    private String status;

    @Column(name = "error_message")
    private String errorMessage;
}

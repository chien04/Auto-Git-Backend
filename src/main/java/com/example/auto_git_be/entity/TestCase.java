package com.example.auto_git_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "test_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TestCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "assignment_task_id")
    private AssignmentTask assignmentTask;

    @Column(name = "input_content", columnDefinition = "TEXT")
    private String inputContent;

    @Column(name = "output_content", columnDefinition = "TEXT")
    private String outputContent;

    @Column(name = "input_file_url")
    private String inputFileUrl;

    @Column(name = "output_file_url")
    private String outputFileUrl;

    @Column(name = "is_sample")
    private boolean isSample;

    @Column(name = "ordinal")
    private Integer ordinal;
}

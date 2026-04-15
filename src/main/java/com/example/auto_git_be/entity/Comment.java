package com.example.auto_git_be.entity;

import com.example.auto_git_be.model.CommentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "target_branch", nullable = false)
    private String targetBranch;

    @Column(name = "student_file_path", nullable = false)
    private String studentFilePath;

    @Column(name = "teacher_file_path")
    private String teacherFilePath;

    @Column(name = "start_line", nullable = false)
    private Integer startLine;

    @Column(name = "start_column", nullable = false)
    private Integer startColumn;

    @Column(name = "end_line", nullable = false)
    private Integer endLine;

    @Column(name = "end_column", nullable = false)
    private Integer endColumn;

    @Column(name = "selected_text", columnDefinition = "TEXT")
    private String selectedText;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

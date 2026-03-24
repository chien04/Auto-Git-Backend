package com.example.auto_git_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Stores teacher-specific information for assignments
 * Allows multiple teachers to work on same assignment with different local paths
 */
@Entity
@Table(name = "teacher_assignments", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"teacher_id", "assignment_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TeacherAssignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher; // Main teacher or sub-teacher
    
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;
    
    @Column(name = "local_path", length = 500)
    private String localPath; // Teacher's local workspace path
    
    @Column(name = "role", length = 20)
    private String role; // "MAIN" or "SUB"
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

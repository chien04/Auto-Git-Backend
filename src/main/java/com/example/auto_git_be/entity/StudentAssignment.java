package com.example.auto_git_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAssignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;
    
    @Column(name = "branch_name", nullable = false)
    private String branchName;
    
    // github_token field removed for security - always get from GitHubService (env)
    // @Column(name = "github_token")
    // private String githubToken;
    
    @Column(name = "last_commit_at")
    private LocalDateTime lastCommitAt;
    
    @Column(name = "commit_count")
    private Integer commitCount = 0;
    
    @Column(name = "score")
    private Double score; // Score on scale of 10 (0-10)
    
    @Column(name = "local_path")
    private String localPath;
    
    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

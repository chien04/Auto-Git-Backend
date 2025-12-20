package com.example.auto_git_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "classrooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassRoom {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "class_code", nullable = false, unique = true, length = 8)
    private String classCode;
    
    @Column(name = "repo_url", nullable = false)
    private String repoUrl;
    
    @Column(name = "repo_name", nullable = false)
    private String repoName;
    
    @Column(name = "github_repo_id")
    private Long githubRepoId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;
    
    @OneToMany(mappedBy = "classRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Student> students = new ArrayList<>();
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "local_path")
    private String localPath;
    
    @Column(name = "deadline")
    private LocalDateTime deadline;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

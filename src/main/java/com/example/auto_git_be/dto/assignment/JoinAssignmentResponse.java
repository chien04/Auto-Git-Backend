package com.example.auto_git_be.dto.assignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinAssignmentResponse {
    private String repoUrl;
    private String branch;
    private String token;
    private String studentId;
    private String assignmentTitle;
    private LocalDateTime deadline;
}


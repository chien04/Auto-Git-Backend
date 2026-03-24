package com.example.auto_git_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAssignmentResponse {
    private String assignmentId;
    private String assignmentCode;
    private String title;
    private String token;
    private String repoUrl;
    private LocalDateTime deadline;
}

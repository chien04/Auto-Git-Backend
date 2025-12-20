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
public class CreateClassResponse {
    private String classId;
    private String classCode;
    private String repoUrl;
    private String className;
    private String token;
    private String branch;
    private LocalDateTime deadline;
}

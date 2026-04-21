package com.example.auto_git_be.dto.assignment;

import lombok.Data;

@Data
public class ScoreUpdateRequest {
    private String repoFullName;
    private String branchName;
    private Integer score;
    private Integer passedTests;
    private Integer totalTests;
}


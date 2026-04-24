package com.example.auto_git_be.dto.assignment;

import lombok.Data;

import java.util.List;

@Data
public class ScoreUpdateRequest {
    private String repoFullName;
    private String branchName;
    private Double score;
    private List<TaskDTO> details;

}


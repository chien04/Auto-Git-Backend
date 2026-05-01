package com.example.auto_git_be.dto.assignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskDTO {
    private Long resultId;
    private Integer OrderNo;
    private String language;
    private Double score;
    private Integer pass;
    private Integer total;
    private String status;
    private String errorMessage;
    private String commitHash;
    private String sourceCode;
}

package com.example.auto_git_be.dto.execute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EvaluationResult {
    private int passed;
    private int total;
    private Double maxTime;
    private int maxMemory;
    private String overallStatus;
    private String errorMessage;
}

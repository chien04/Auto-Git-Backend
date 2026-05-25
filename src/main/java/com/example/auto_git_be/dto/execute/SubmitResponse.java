package com.example.auto_git_be.dto.execute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitResponse {
    private Double time;
    private int memory;
    private String errorMessage;
    private double score;
    private Double assignmentScore;
    private int passedTestCases;
    private int totalTestCases;
    private String status;
}

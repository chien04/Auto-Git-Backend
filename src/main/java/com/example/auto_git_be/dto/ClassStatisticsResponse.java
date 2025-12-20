package com.example.auto_git_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassStatisticsResponse {
    private Long classId;
    private String className;
    private String classCode;
    private int totalStudents;
    private int studentsSubmitted;
    private int studentsNotSubmitted;
    private double submittedPercentage;
    private double notSubmittedPercentage;
    private boolean isActive;
}

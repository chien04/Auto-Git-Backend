package com.example.auto_git_be.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDashboardResponse {
    private int totalStudents;
    private int studentsSubmitted;
    private int studentsNotSubmitted;
    private double submittedPercentage;
    private double notSubmittedPercentage;
    private double averageCommitsPerStudent;
    private int totalClasses;
    private int activeClasses;
}


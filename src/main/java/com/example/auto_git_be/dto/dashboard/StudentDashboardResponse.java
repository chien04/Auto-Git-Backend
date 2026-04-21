package com.example.auto_git_be.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDashboardResponse {
    private int totalCommits;
    private LocalDateTime lastCommitAt;
    private int totalClasses;
    private int activeClasses;
}


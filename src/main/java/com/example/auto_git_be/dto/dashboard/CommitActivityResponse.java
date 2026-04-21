package com.example.auto_git_be.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitActivityResponse {
    private Map<LocalDate, Integer> dailyCommits; // date -> number of commits that day
}


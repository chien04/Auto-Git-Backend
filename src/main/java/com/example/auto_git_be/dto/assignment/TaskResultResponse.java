package com.example.auto_git_be.dto.assignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskResultResponse {
    private Integer totalScore;
    private Integer maxTotalScore;
    private List<TaskWithHistoryDTO> tasks;
}

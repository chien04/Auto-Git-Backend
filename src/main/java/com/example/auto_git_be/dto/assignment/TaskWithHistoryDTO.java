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
public class TaskWithHistoryDTO {
    private Long taskId;
    private int taskOrderNo;
    private String taskName;
    private Double bestScore;
    private int maxScore;
    private List<TaskDTO> history;
}

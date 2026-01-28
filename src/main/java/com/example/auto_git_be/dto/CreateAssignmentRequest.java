package com.example.auto_git_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAssignmentRequest {
    private String classCode;
    private String title;
    private String description;
    private LocalDateTime deadline;
}

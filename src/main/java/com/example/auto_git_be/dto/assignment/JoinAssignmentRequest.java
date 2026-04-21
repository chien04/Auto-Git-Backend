package com.example.auto_git_be.dto.assignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinAssignmentRequest {
    private String assignmentCode;
    private String localPath;
}


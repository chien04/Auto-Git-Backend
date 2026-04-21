package com.example.auto_git_be.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceUploadRequest {
    private String assignmentCode;
    private List<StudentAssignmentDTO> studentAssignments;
}

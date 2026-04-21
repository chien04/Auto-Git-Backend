package com.example.auto_git_be.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentAssignmentDTO {
    private String studentName;
    private List<FileDTO> files;
}

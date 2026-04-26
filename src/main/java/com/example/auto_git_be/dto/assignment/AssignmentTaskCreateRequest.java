package com.example.auto_git_be.dto.assignment;

import com.example.auto_git_be.dto.execute.TestCaseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentTaskCreateRequest {
    private Integer orderNo;
    private String taskName;
    private String description;
    List<TestCaseDTO> sampleTestCases;
}
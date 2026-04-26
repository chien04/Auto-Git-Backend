package com.example.auto_git_be.dto.execute;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteRequest {
    private String sourceCode;
    private String language;
    private String assignmentCode;
    private Integer taskOrderNo;
}


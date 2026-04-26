package com.example.auto_git_be.dto.execute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubmitRequest {
    private String assignmentCode;
    private Integer taskOrderNo;
    private String language;
    private String filePath;
    private String sourceCode;
}

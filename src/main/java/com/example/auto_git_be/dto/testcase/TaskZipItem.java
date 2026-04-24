package com.example.auto_git_be.dto.testcase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskZipItem {
    private String taskName;
    private String fileName;
    private String fileContent;
}
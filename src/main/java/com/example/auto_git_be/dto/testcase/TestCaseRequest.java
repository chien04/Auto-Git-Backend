package com.example.auto_git_be.dto.testcase;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseRequest {
    private String assignmentCode;
    private Map<String, List<TestCaseData>> exercises;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseData {
        private String input;
        private String output;
        private Integer timeLimit;  // milliseconds (default: 5000)
        private Integer memoryLimit; // MB (default: 256)
    }
}


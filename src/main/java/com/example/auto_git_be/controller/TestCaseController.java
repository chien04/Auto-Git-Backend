package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.testcase.UploadTaskZipsRequest;
import com.example.auto_git_be.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/test-cases")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;
    
    @Value("${backend.url:http://localhost:8080}")
    private String backendUrl;

    @PostMapping("/upload-task-zips")
    public ResponseEntity<Map<String, Object>> uploadTaskTestCasesZips(@RequestBody UploadTaskZipsRequest request) {
        try {
            if (request == null || request.getAssignmentCode() == null || request.getAssignmentCode().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing assignmentCode"
                ));
            }

            Map<String, Object> result = testCaseService.uploadTaskTestCasesZips(
                    request.getAssignmentCode(),
                    request.getTasks()
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Invalid base64 format in task ZIP payload: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid base64 format: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Upload task ZIPs failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

}

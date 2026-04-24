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

    @GetMapping("/{assignmentCode}/download-urls")
    public ResponseEntity<Map<String, Object>> getDownloadUrls(@PathVariable String assignmentCode) {
        try {
            List<Map<String, Object>> taskLinks = testCaseService.getTaskDownloadLinks(assignmentCode, backendUrl);
            if (taskLinks.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(Map.of(
                    "assignmentCode", assignmentCode,
                    "tasks", taskLinks,
                    "count", taskLinks.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/{assignmentCode}/task/{taskOrderNo}/download")
    public ResponseEntity<InputStreamResource> downloadTaskTestCases(
            @PathVariable String assignmentCode,
            @PathVariable int taskOrderNo
    ) {
        try {
            InputStream fileStream = testCaseService.downloadTaskTestCasesStream(assignmentCode, taskOrderNo);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"test-cases-" + assignmentCode + "-task-" + taskOrderNo + ".zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(fileStream));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }


    @DeleteMapping("/{assignmentCode}")
    public ResponseEntity<Map<String, String>> deleteTestCases(@PathVariable String assignmentCode) {
        try {
            testCaseService.deleteTestCasesByAssignment(assignmentCode);
            return ResponseEntity.ok(Map.of("message", "Test cases deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

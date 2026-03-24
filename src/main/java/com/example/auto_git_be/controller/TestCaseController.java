package com.example.auto_git_be.controller;

import com.example.auto_git_be.entity.TestCase;
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

    @PostMapping("/upload-zip")
    public ResponseEntity<Map<String, Object>> uploadTestCasesZip(@RequestBody Map<String, String> request) {
        try {
            String assignmentCode = request.get("assignmentCode");
            String fileName = request.get("fileName");
            String fileContent = request.get("fileContent");

            if (assignmentCode == null || fileContent == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing assignmentCode or fileContent"
                ));
            }

            byte[] zipBytes = java.util.Base64.getDecoder().decode(fileContent);
            Map<String, Object> result = testCaseService.uploadTestCasesZip(assignmentCode, zipBytes);
            
            log.info("Upload successful! Result: {}", result);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Invalid base64 format: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid base64 format: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get download URL for test cases ZIP file (via backend proxy)
     * Used by GitHub Actions to download test cases
     * Returns backend URL instead of MinIO presigned URL (only need 1 ngrok tunnel)
     */
    @GetMapping("/{assignmentCode}/download-url")
    public ResponseEntity<Map<String, String>> getDownloadUrl(@PathVariable String assignmentCode) {
        try {
            // Verify test cases exist
            if (!testCaseService.testCasesExist(assignmentCode)) {
                return ResponseEntity.notFound().build();
            }
            
            // Return backend URL for proxy download (not MinIO presigned URL)
            String downloadUrl = backendUrl + "/api/test-cases/" + assignmentCode + "/download";
            
            return ResponseEntity.ok(Map.of(
                    "assignmentCode", assignmentCode,
                    "downloadUrl", downloadUrl,
                    "expiresIn", "No expiration (proxied via backend)"
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Download test cases ZIP file (proxy from MinIO)
     * Used by GitHub Actions - backend downloads from MinIO and streams to client
     * This allows using only 1 ngrok tunnel (for backend) instead of 2 (backend + MinIO)
     */
    @GetMapping("/{assignmentCode}/download")
    public ResponseEntity<InputStreamResource> downloadTestCases(@PathVariable String assignmentCode) {
        try {
            // Get file stream from MinIO via service
            InputStream fileStream = testCaseService.downloadTestCasesStream(assignmentCode);
            
            // Stream file to client
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"test-cases-" + assignmentCode + ".zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(fileStream));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all test cases metadata for an assignment
     */
    @GetMapping("/{assignmentCode}")
    public ResponseEntity<List<TestCase>> getTestCases(@PathVariable String assignmentCode) {
        List<TestCase> testCases = testCaseService.getTestCasesByAssignment(assignmentCode);
        
        if (testCases.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(testCases);
    }

    /**
     * Get test cases for a specific exercise
     */
    @GetMapping("/{assignmentCode}/exercise/{exerciseName}")
    public ResponseEntity<List<TestCase>> getTestCasesByExercise(
            @PathVariable String assignmentCode,
            @PathVariable String exerciseName) {
        List<TestCase> testCases = testCaseService.getTestCasesByExercise(assignmentCode, exerciseName);
        
        if (testCases.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(testCases);
    }

    /**
     * Get test case structure (for workflow generation)
     */
    @GetMapping("/{assignmentCode}/structure")
    public ResponseEntity<Map<String, Integer>> getTestCaseStructure(@PathVariable String assignmentCode) {
        Map<String, Integer> structure = testCaseService.getTestCaseStructure(assignmentCode);
        
        if (structure.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(structure);
    }

    /**
     * Check if test cases exist for an assignment
     */
    @GetMapping("/{assignmentCode}/exists")
    public ResponseEntity<Map<String, Boolean>> testCasesExist(@PathVariable String assignmentCode) {
        boolean exists = testCaseService.testCasesExist(assignmentCode);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /**
     * Delete test cases for an assignment
     */
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

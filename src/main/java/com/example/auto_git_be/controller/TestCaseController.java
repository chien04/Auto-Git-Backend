package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.testcase.TaskZipUploadItem;
import com.example.auto_git_be.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/test-cases")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;

    @PostMapping(value = "/upload-task-zips", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadTaskTestCasesZips(
            @RequestParam("assignmentCode") String assignmentCode,
            @RequestParam(value = "taskNames", required = false) List<String> taskNames,
            @RequestPart("files") List<MultipartFile> files
    ) {
        try {
            if (assignmentCode == null || assignmentCode.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing assignmentCode"
                ));
            }

            if (files == null || files.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing files"
                ));
            }

            List<String> safeTaskNames = taskNames == null ? Collections.emptyList() : taskNames;
            List<TaskZipUploadItem> tasks = new ArrayList<>();

            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                if (file == null || file.isEmpty()) {
                    continue;
                }

                String taskName = null;
                if (index < safeTaskNames.size()) {
                    String candidate = safeTaskNames.get(index);
                    if (candidate != null && !candidate.isBlank()) {
                        taskName = candidate;
                    }
                }

                String fileName = file.getOriginalFilename();
                if (fileName == null || fileName.isBlank()) {
                    fileName = "task-" + (index + 1) + ".zip";
                }

                tasks.add(TaskZipUploadItem.builder()
                        .taskName(taskName)
                        .fileName(fileName)
                        .fileContent(file.getBytes())
                        .build());
            }

            if (tasks.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "No valid files uploaded"
                ));
            }

            Map<String, Object> result = testCaseService.uploadTaskTestCasesZips(
                    assignmentCode,
                    tasks
            );
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("Failed to read uploaded task ZIPs: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to read uploaded files: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Upload task ZIPs failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

}

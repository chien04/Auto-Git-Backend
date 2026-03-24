package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.TestCase;
import com.example.auto_git_be.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final MinioService minioService;

    @Transactional
    public Map<String, Object> uploadTestCasesZip(String assignmentCode, byte[] zipBytes) {
        try {
            Map<String, Map<String, Set<String>>> structure = new HashMap<>();

            try (ZipArchiveInputStream zipIn = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipArchiveEntry entry;
                while ((entry = zipIn.getNextZipEntry()) != null) {
                    String path = entry.getName();
                    if (entry.isDirectory()) continue;

                    String[] parts = path.split("/");
                    if (parts.length == 3 && "testcases".equals(parts[0])) {
                        String exerciseName = parts[1];
                        String fileName = parts[2];

                        if (fileName.endsWith(".txt")) {
                            String type = fileName.startsWith("input") ? "input" : (fileName.startsWith("output") ? "output" : null);
                            if (type != null) {
                                String numStr = fileName.replaceAll("[^0-9]", "");
                                structure.computeIfAbsent(exerciseName, k -> new HashMap<>())
                                        .computeIfAbsent(numStr, k -> new HashSet<>())
                                        .add(type);
                            }
                        }
                    }
                }
            }

            List<TestCase> testCasesToSave = new ArrayList<>();
            String zipObjectKey = "test-cases/" + assignmentCode + ".zip";
            Map<String, Integer> finalCounts = new HashMap<>();

            for (var exerciseEntry : structure.entrySet()) {
                String exName = exerciseEntry.getKey();
                int validCount = 0;

                for (var testEntry : exerciseEntry.getValue().entrySet()) {
                    Set<String> types = testEntry.getValue();
                    if (types.contains("input") && types.contains("output")) {
                        int testNum = Integer.parseInt(testEntry.getKey());
                        testCasesToSave.add(TestCase.builder()
                                .assignmentCode(assignmentCode)
                                .exerciseName(exName)
                                .testNumber(testNum)
                                .zipObjectKey(zipObjectKey)
                                .fileSize((long) zipBytes.length)
                                .timeLimitMs(5000)
                                .memoryLimitMb(256)
                                .build());
                        validCount++;
                    } else {
                        log.warn("Test case {} in {} missing input or output", testEntry.getKey(), exName);
                    }
                }
                finalCounts.put(exName, validCount);
            }

            if (testCasesToSave.isEmpty()) {
                throw new RuntimeException("No valid test cases found (must have both input#.txt and output#.txt)");
            }

            minioService.uploadFile(zipObjectKey, zipBytes, "application/zip");
            testCaseRepository.deleteByAssignmentCode(assignmentCode);
            testCaseRepository.saveAll(testCasesToSave);

            return Map.of(
                    "message", "Test cases uploaded successfully",
                    "assignmentCode", assignmentCode,
                    "exercises", finalCounts,
                    "totalValidTestCases", testCasesToSave.size()
            );

        } catch (Exception e) {
            log.error("Upload failed", e);
            throw new RuntimeException("Failed to process ZIP: " + e.getMessage());
        }
    }
    
    /**
     * Download test cases ZIP file as stream (for backend proxy pattern)
     * Used when backend serves as proxy to avoid exposing MinIO directly
     * @param assignmentCode Assignment code
     * @return InputStream of ZIP file
     */
    public java.io.InputStream downloadTestCasesStream(String assignmentCode) {
        List<TestCase> testCases = testCaseRepository.findByAssignmentCode(assignmentCode);
        
        if (testCases.isEmpty()) {
            throw new RuntimeException("No test cases found for assignment: " + assignmentCode);
        }
        
        // All test cases share the same ZIP file
        String zipObjectKey = testCases.get(0).getZipObjectKey();
        return minioService.downloadFile(zipObjectKey);
    }

    /**
     * Get all test cases for an assignment
     */
    public List<TestCase> getTestCasesByAssignment(String assignmentCode) {
        return testCaseRepository.findByAssignmentCode(assignmentCode);
    }

    /**
     * Get test cases for a specific exercise
     */
    public List<TestCase> getTestCasesByExercise(String assignmentCode, String exerciseName) {
        return testCaseRepository.findByAssignmentCodeAndExerciseName(assignmentCode, exerciseName);
    }

    /**
     * Delete all test cases for an assignment
     */
    @Transactional
    public void deleteTestCasesByAssignment(String assignmentCode) {
        List<TestCase> testCases = testCaseRepository.findByAssignmentCode(assignmentCode);
        
        if (!testCases.isEmpty()) {
            // Delete ZIP file from MinIO
            String zipObjectKey = testCases.get(0).getZipObjectKey();
            minioService.deleteFile(zipObjectKey);
            
            // Delete metadata from database
            testCaseRepository.deleteByAssignmentCode(assignmentCode);
            
            System.out.println("Deleted all test cases for assignment: " + assignmentCode);
        }
    }

    /**
     * Check if test cases exist for an assignment
     */
    public boolean testCasesExist(String assignmentCode) {
        return testCaseRepository.existsByAssignmentCode(assignmentCode);
    }

    /**
     * Get test case structure (exercises and test numbers) for workflow generation
     */
    public Map<String, Integer> getTestCaseStructure(String assignmentCode) {
        List<TestCase> testCases = testCaseRepository.findByAssignmentCode(assignmentCode);
        
        Map<String, Integer> structure = new HashMap<>();
        for (TestCase testCase : testCases) {
            structure.merge(testCase.getExerciseName(), 1, Integer::sum);
        }
        
        return structure; // e.g., {"ex1": 3, "ex2": 5, "ex3": 2}
    }
}

package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.testcase.TaskZipUploadItem;
import com.example.auto_git_be.dto.testcase.TestCaseTemp;
import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.AssignmentTask;
import com.example.auto_git_be.entity.TestCase;
import com.example.auto_git_be.repository.AssignmentRepository;
import com.example.auto_git_be.repository.AssignmentTaskRepository;
import com.example.auto_git_be.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentTaskRepository assignmentTaskRepository;
    private final TestCaseRepository testCaseRepository;
    private final MinioService minioService;

    Pattern inputPattern = Pattern.compile("input(\\d+)\\.txt");
    Pattern outputPattern = Pattern.compile("output(\\d+)\\.txt");

    @Transactional
    public Map<String, Object> uploadTaskTestCasesZips(String assignmentCode, List<TaskZipUploadItem> tasks) {
        Assignment assignment = assignmentRepository.findByAssignmentCode(assignmentCode)
                .orElseThrow(() -> new RuntimeException("Assignment not found: " + assignmentCode));

        if (tasks == null || tasks.isEmpty()) {
            throw new RuntimeException("Tasks list is empty");
        }

        List<Map<String, Object>> uploaded = new ArrayList<>();

        for (int index = 0; index < tasks.size(); index++) {
            TaskZipUploadItem item = tasks.get(index);
            if (item == null || item.getFileContent() == null || item.getFileContent().length == 0) {
                continue;
            }

            int taskOrderNo = index + 1;
            byte[] zipBytes = item.getFileContent();
            validateTaskZipStructure(zipBytes, taskOrderNo, item.getFileName());

            String taskName = (item.getTaskName() == null || item.getTaskName().isBlank())
                ? ("Task " + taskOrderNo)
                    : item.getTaskName();

            AssignmentTask assignmentTask = assignmentTaskRepository
                    .findByAssignmentAndOrderNo(assignment, taskOrderNo)
                    .orElseGet(() -> AssignmentTask.builder()
                            .assignment(assignment)
                            .taskName(taskName)
                    .orderNo(taskOrderNo)
                            .build());

            if (assignmentTask.getOrderNo() == null) {
                assignmentTask.setOrderNo(taskOrderNo);
            }
            assignmentTask = assignmentTaskRepository.save(assignmentTask);

            Map<String, TestCaseTemp> testCaseMap = new HashMap<>();

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;

                    String fileName = entry.getName();

                    String testCaseNumber = null;
                    boolean isInput = false;

                    Matcher inputMatcher = inputPattern.matcher(fileName);
                    Matcher outputMatcher = outputPattern.matcher(fileName);

                    if (inputMatcher.matches()) {
                        testCaseNumber = inputMatcher.group(1);
                        isInput = true;
                    } else if (outputMatcher.matches()) {
                        testCaseNumber = outputMatcher.group(1);
                    } else {
                        zis.closeEntry();
                        continue;
                    }

                    byte[] fileContentBytes = zis.readAllBytes();

                    String childObjectKey = String.format("assignments/%s/tasks/%d/testcases/%s",
                            assignmentCode, assignmentTask.getId(), fileName);

                    minioService.uploadFile(childObjectKey, fileContentBytes, "text/plain");

                    TestCaseTemp temp = testCaseMap.getOrDefault(testCaseNumber, new TestCaseTemp());
                    if (isInput) {
                        temp.setInputUrl(childObjectKey);
                    } else {
                        temp.setOutputUrl(childObjectKey);
                    }
                    testCaseMap.put(testCaseNumber, temp);

                    zis.closeEntry();
                }
            } catch (Exception e) {
                throw new RuntimeException("Lỗi khi giải nén và upload test cases cho Task " + taskOrderNo, e);
            }

            testCaseRepository.deleteAllByAssignmentTaskAndIsSampleFalse(assignmentTask);

            List<TestCase> newTestCases = new ArrayList<>();
            for (Map.Entry<String, TestCaseTemp> mapEntry : testCaseMap.entrySet()) {
                TestCaseTemp temp = mapEntry.getValue();

                if (temp.getInputUrl() != null && temp.getOutputUrl() != null) {
                    TestCase tc = new TestCase();
                    tc.setAssignmentTask(assignmentTask);
                    tc.setInputFileUrl(temp.getInputUrl());
                    tc.setOutputFileUrl(temp.getOutputUrl());
                    tc.setSample(false);
                    tc.setOrdinal(Integer.parseInt(mapEntry.getKey()));

                    newTestCases.add(tc);
                }
            }
            testCaseRepository.saveAll(newTestCases);

            Map<String, Object> itemResult = new HashMap<>();
            itemResult.put("taskName", taskName);
            itemResult.put("testCasesCount", newTestCases.size());
            uploaded.add(itemResult);
        }

        if (uploaded.isEmpty()) {
            throw new RuntimeException("No valid task ZIP payloads found");
        }

        return Map.of(
                "message", "Task test cases uploaded successfully",
                "assignmentCode", assignmentCode,
                "uploadedCount", uploaded.size(),
                "items", uploaded
        );
    }

    private void validateTaskZipStructure(byte[] zipBytes, int taskOrderNo, String fileName) {
        Map<Integer, Set<String>> paired = new HashMap<>();

        try (ZipArchiveInputStream zipIn = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipArchiveEntry entry;
            while ((entry = zipIn.getNextZipEntry()) != null) {
                if (entry.isDirectory()) {
                    throw new RuntimeException("ZIP task " + taskOrderNo + " khong hop le: khong duoc chua thu muc");
                }

                String entryName = entry.getName();
                if (entryName.isBlank()) {
                    throw new RuntimeException("ZIP task " + taskOrderNo + " khong hop le: ten file rong");
                }

                if (entryName.contains("/") || entryName.contains("\\")) {
                    throw new RuntimeException("ZIP task " + taskOrderNo + " khong hop le: chi chap nhan file txt o root, khong co folder con");
                }

                Matcher matcher = Pattern.compile("^(input|output)(\\d+)\\.txt$").matcher(entryName.toLowerCase());
                if (!matcher.matches()) {
                    throw new RuntimeException(
                            "ZIP task " + taskOrderNo + " khong hop le: chi chap nhan inputN.txt hoac outputN.txt (file loi: " + entryName + ")"
                    );
                }

                String type = matcher.group(1);
                int number = Integer.parseInt(matcher.group(2));
                paired.computeIfAbsent(number, k -> new HashSet<>()).add(type);
            }
        } catch (IOException e) {
            throw new RuntimeException("Khong the doc ZIP task " + taskOrderNo + ": " + (fileName == null ? "" : fileName), e);
        }

        if (paired.isEmpty()) {
            throw new RuntimeException("ZIP task " + taskOrderNo + " khong hop le: khong co testcase");
        }

        for (Map.Entry<Integer, Set<String>> entry : paired.entrySet()) {
            Set<String> types = entry.getValue();
            if (!types.contains("input") || !types.contains("output")) {
                throw new RuntimeException(
                        "ZIP task " + taskOrderNo + " khong hop le: thieu cap input/output cho testcase #" + entry.getKey()
                );
            }
        }
    }

    private String buildTaskObjectKey(String assignmentCode, String taskName, int fallbackIndex) {
        int taskIndex = extractTaskIndex(taskName, fallbackIndex);
        return assignmentCode + "/task_" + taskIndex + ".zip";
    }

    private int extractTaskIndex(String taskName, int fallbackIndex) {
        if (taskName == null || taskName.isBlank()) {
            return fallbackIndex;
        }

        Matcher matcher = Pattern.compile("(\\d+)").matcher(taskName);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return fallbackIndex;
            }
        }

        return fallbackIndex;
    }

}

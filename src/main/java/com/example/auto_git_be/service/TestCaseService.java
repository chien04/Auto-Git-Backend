package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.testcase.TaskZipItem;
import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.AssignmentTask;
import com.example.auto_git_be.repository.AssignmentRepository;
import com.example.auto_git_be.repository.AssignmentTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentTaskRepository assignmentTaskRepository;
    private final MinioService minioService;

    @Transactional
    public Map<String, Object> uploadTaskTestCasesZips(String assignmentCode, List<TaskZipItem> tasks) {
        Assignment assignment = assignmentRepository.findByAssignmentCode(assignmentCode)
                .orElseThrow(() -> new RuntimeException("Assignment not found: " + assignmentCode));

        if (tasks == null || tasks.isEmpty()) {
            throw new RuntimeException("Tasks list is empty");
        }

        List<Map<String, Object>> uploaded = new ArrayList<>();

        for (int index = 0; index < tasks.size(); index++) {
            TaskZipItem item = tasks.get(index);
            if (item == null || item.getFileContent() == null || item.getFileContent().isBlank()) {
                continue;
            }

            int taskOrderNo = index + 1;
            byte[] zipBytes = Base64.getDecoder().decode(item.getFileContent());
            validateTaskZipStructure(zipBytes, taskOrderNo, item.getFileName());

            String taskName = (item.getTaskName() == null || item.getTaskName().isBlank())
                ? ("Task " + taskOrderNo)
                    : item.getTaskName();

            String objectKey = buildTaskObjectKey(assignmentCode, taskName, taskOrderNo);
            minioService.uploadFile(objectKey, zipBytes, "application/zip");

            AssignmentTask assignmentTask = assignmentTaskRepository
                    .findByAssignmentAndTaskName(assignment, taskName)
                    .orElseGet(() -> AssignmentTask.builder()
                            .assignment(assignment)
                            .taskName(taskName)
                    .orderNo(taskOrderNo)
                            .build());

            assignmentTask.setBucket(minioService.getBucketName());
            assignmentTask.setObjectKey(objectKey);
            if (assignmentTask.getOrderNo() == null) {
                assignmentTask.setOrderNo(taskOrderNo);
            }
            assignmentTaskRepository.save(assignmentTask);

            Map<String, Object> itemResult = new HashMap<>();
            itemResult.put("taskName", taskName);
            itemResult.put("objectKey", objectKey);
            itemResult.put("size", zipBytes.length);
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
                if (entryName == null || entryName.isBlank()) {
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
    
    public InputStream downloadTaskTestCasesStream(String assignmentCode, int taskOrderNo) {
        Assignment assignment = assignmentRepository.findByAssignmentCode(assignmentCode)
                .orElseThrow(() -> new RuntimeException("Assignment not found: " + assignmentCode));

        AssignmentTask assignmentTask = assignmentTaskRepository
                .findByAssignmentAndOrderNo(assignment, taskOrderNo)
                .orElseThrow(() -> new RuntimeException("Task not found for assignment: " + assignmentCode + ", order: " + taskOrderNo));

        if (assignmentTask.getObjectKey() == null || assignmentTask.getObjectKey().isBlank()) {
            throw new RuntimeException("Task has no test cases uploaded: " + assignmentTask.getTaskName());
        }

        return minioService.downloadFile(assignmentTask.getObjectKey());
    }

    public List<Map<String, Object>> getTaskDownloadLinks(String assignmentCode, String backendUrl) {
        Assignment assignment = assignmentRepository.findByAssignmentCode(assignmentCode)
                .orElseThrow(() -> new RuntimeException("Assignment not found: " + assignmentCode));

        List<AssignmentTask> tasks = assignmentTaskRepository.findByAssignmentOrderByOrderNoAscIdAsc(assignment);
        List<Map<String, Object>> result = new ArrayList<>();

        for (AssignmentTask task : tasks) {
            if (task.getObjectKey() == null || task.getObjectKey().isBlank()) {
                continue;
            }

            int orderNo = task.getOrderNo() == null ? 0 : task.getOrderNo();
            String downloadUrl = backendUrl + "/api/test-cases/" + assignmentCode + "/task/" + orderNo + "/download";

            result.add(Map.of(
                    "orderNo", orderNo,
                    "taskName", task.getTaskName() == null ? ("Task " + orderNo) : task.getTaskName(),
                    "downloadUrl", downloadUrl,
                    "bucket", task.getBucket() == null ? "" : task.getBucket(),
                    "objectKey", task.getObjectKey()
            ));
        }

        return result;
    }

    @Transactional
    public void deleteTestCasesByAssignment(String assignmentCode) {
        assignmentRepository.findByAssignmentCode(assignmentCode).ifPresent(assignment -> {
            List<AssignmentTask> tasks = assignmentTaskRepository.findByAssignmentOrderByOrderNoAscIdAsc(assignment);
            for (AssignmentTask task : tasks) {
                if (task.getObjectKey() != null && !task.getObjectKey().isBlank()) {
                    minioService.deleteFile(task.getObjectKey());
                    task.setObjectKey(null);
                    task.setBucket(null);
                    assignmentTaskRepository.save(task);
                }
            }
        });
    }
}

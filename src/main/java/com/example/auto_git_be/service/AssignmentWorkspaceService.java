package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.StudentAssignment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
public class AssignmentWorkspaceService {

    private String sanitizeFileName(String name) {
        if (name == null) {
            return "unknown-student";
        }

        // Remove only invalid path characters for Windows/macOS compatibility
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Windows does not allow trailing dot/space in folder names
        sanitized = sanitized.replaceAll("[. ]+$", "").trim();
        return sanitized.isEmpty() ? "unknown-student" : sanitized;
    }

    private String legacySanitizeFileName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^a-zA-Z0-9-_]", "");
    }

    private String getStudentWorktreePath(String repoPath, String studentName) {
        return repoPath + "/students/" + sanitizeFileName(studentName);
    }

    private void migrateLegacyStudentFolder(String repoPath, String studentName) {
        String newPath = getStudentWorktreePath(repoPath, studentName);
        String legacyName = legacySanitizeFileName(studentName);
        if (legacyName.isEmpty()) {
            return;
        }

        String oldPath = repoPath + "/students/" + legacyName;
        File oldDir = new File(oldPath);
        File newDir = new File(newPath);

        if (oldDir.exists() && !newDir.exists()) {
            oldDir.renameTo(newDir);
        }
    }

    private void executeGitCommand(String workingDir, String... command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Git command failed with exit code " + exitCode + "\nOutput: " + output.toString());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command interrupted", e);
        }
    }

    private void createWorktree(String repoPath, String branchName, String studentName) throws IOException {
        String worktreePath = getStudentWorktreePath(repoPath, studentName);

        File worktreeDir = new File(worktreePath);
        if (worktreeDir.exists()) {
            return;
        }

        executeGitCommand(repoPath, "git", "fetch", "origin", branchName);
        executeGitCommand(repoPath, "git", "worktree", "add", worktreePath, branchName);
    }

    public void syncAssignmentWorkspace(List<StudentAssignment> studentAssignments, String localPath) throws IOException {

        File assignmentDir = new File(localPath);

        if (!assignmentDir.exists()) {
            throw new IOException("Workspace does not exist: " + localPath + ". Please setup workspace first.");
        }

        executeGitCommand(localPath, "git", "fetch", "--all");
        executeGitCommand(localPath, "git", "pull", "origin", "teacher");

        for (StudentAssignment sa : studentAssignments) {
            if (sa.getBranchName() == null || sa.getBranchName().isEmpty()) {
                continue;
            }

            String studentName = sa.getStudent().getStudentName();
            String worktreePath = getStudentWorktreePath(localPath, studentName);
            File worktreeDir = new File(worktreePath);

            if (!worktreeDir.exists()) {
                try {
                    createWorktree(localPath, sa.getBranchName(), studentName);
                } catch (IOException e) {
                    continue;
                }
            }

            executeGitCommand(worktreePath, "git", "pull", "origin", sa.getBranchName());
        }
    }

}

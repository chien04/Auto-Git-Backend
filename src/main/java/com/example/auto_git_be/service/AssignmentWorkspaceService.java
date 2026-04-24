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

    @Value("${workspace.root.path:D:/Assignments}")
    private String workspaceRootPath;

    private String getAssignmentPath(String classCode, String assignmentCode) {
        return workspaceRootPath + "/" + classCode + "-" + assignmentCode;
    }

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

    private void cloneRepository(String repoUrl, String targetPath, String token) throws IOException {
        File targetDir = new File(targetPath);
        if (targetDir.exists()) {
            return;
        }
        
        // Convert HTTPS URL to include token
        String authenticatedUrl = repoUrl;
        if (token != null && !token.isEmpty()) {
            authenticatedUrl = repoUrl.replace("https://", "https://" + token + "@");
        }

        try {
            executeGitCommand(targetDir.getParent(), "git", "clone", authenticatedUrl, targetPath);
        } catch (IOException e) {
            throw e;
        }

        // Add students/ to .gitignore
        addStudentsFolderToGitignore(targetPath);

        // Checkout teacher branch
        executeGitCommand(targetPath, "git", "checkout", "teacher");
    }

    private void addStudentsFolderToGitignore(String repoPath) throws IOException {
        File gitignoreFile = new File(repoPath, ".gitignore");
        String studentsEntry = "students/\n";

        try {
            String content = "";
            if (gitignoreFile.exists()) {
                content = new String(Files.readAllBytes(gitignoreFile.toPath()));
            }

            if (!content.contains("students/")) {
                Files.write(
                    gitignoreFile.toPath(),
                    studentsEntry.getBytes(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
                );
                
                // Auto-commit and push .gitignore
                try {
                    executeGitCommand(repoPath, "git", "add", ".gitignore");
                    executeGitCommand(repoPath, "git", "commit", "-m", "chore: add students/ to .gitignore");
                    executeGitCommand(repoPath, "git", "push", "origin", "teacher");
                } catch (IOException e) {
                    // Don't throw - .gitignore still added locally
                }
            }
        } catch (Exception e) {
        }
    }

    private void createWorktree(String repoPath, String branchName, String studentName) throws IOException {
        migrateLegacyStudentFolder(repoPath, studentName);
        String worktreePath = getStudentWorktreePath(repoPath, studentName);

        File worktreeDir = new File(worktreePath);
        if (worktreeDir.exists()) {
            return;
        }
        
        try {
            executeGitCommand(repoPath, "git", "fetch", "origin", branchName);
        } catch (IOException e) {
        }

        // Create worktree
        try {
            executeGitCommand(repoPath, "git", "worktree", "add", worktreePath, branchName);
        } catch (IOException e) {
            throw e;
        }
    }

    public String setupAssignmentWorkspace(Assignment assignment, List<StudentAssignment> studentAssignments, String token, String localPath) throws IOException {
        if (localPath == null || localPath.isEmpty()) {
            String classCode = assignment.getClassRoom().getClassCode();
            String assignmentCode = assignment.getAssignmentCode();
            localPath = getAssignmentPath(classCode, assignmentCode);
        }
        
        File assignmentDir = new File(localPath);

        File gitDir = new File(assignmentDir, ".git");
        if (!gitDir.exists()) {
            cloneRepository(assignment.getRepoUrl(), localPath, token);
        }

        // Create worktrees for all students who joined
        for (StudentAssignment sa : studentAssignments) {
            if (sa.getBranchName() != null && !sa.getBranchName().isEmpty()) {
                String studentName = sa.getStudent().getStudentName();
                createWorktree(localPath, sa.getBranchName(), studentName);
            }
        }

        return localPath;
    }

    public void syncAssignmentWorkspace(Assignment assignment, List<StudentAssignment> studentAssignments, String localPath) throws IOException {
        if (localPath == null || localPath.isEmpty()) {
            String classCode = assignment.getClassRoom().getClassCode();
            String assignmentCode = assignment.getAssignmentCode();
            localPath = getAssignmentPath(classCode, assignmentCode);
        }
        
        File assignmentDir = new File(localPath);

        if (!assignmentDir.exists()) {
            throw new IOException("Workspace does not exist: " + localPath + ". Please setup workspace first.");
        }

        executeGitCommand(localPath, "git", "fetch", "--all");

        try {
            executeGitCommand(localPath, "git", "pull", "origin", "teacher");
        } catch (IOException e) {
        }

        for (StudentAssignment sa : studentAssignments) {
            if (sa.getBranchName() == null || sa.getBranchName().isEmpty()) {
                continue;
            }

            String studentName = sa.getStudent().getStudentName();
            migrateLegacyStudentFolder(localPath, studentName);
            String worktreePath = getStudentWorktreePath(localPath, studentName);
            File worktreeDir = new File(worktreePath);

            if (!worktreeDir.exists()) {
                try {
                    createWorktree(localPath, sa.getBranchName(), studentName);
                } catch (IOException e) {
                    continue;
                }
            }

            try {
                executeGitCommand(worktreePath, "git", "pull", "origin", sa.getBranchName());
            } catch (IOException e) {
            }
        }
    }

    public void updateAssignmentWorkspaceAndCreateWorktree(Assignment assignment, List<StudentAssignment> studentAssignments, String localPath) {
        if (localPath == null || localPath.isEmpty()) {
            String classCode = assignment.getClassRoom().getClassCode();
            String assignmentCode = assignment.getAssignmentCode();
            localPath = getAssignmentPath(classCode, assignmentCode);
        }

        // Create worktrees for new students
        for (StudentAssignment sa : studentAssignments) {
            if (sa.getBranchName() == null || sa.getBranchName().isEmpty()) {
                continue;
            }

            String studentName = sa.getStudent().getStudentName();
            migrateLegacyStudentFolder(localPath, studentName);
            String worktreePath = getStudentWorktreePath(localPath, studentName);

            if (!new File(worktreePath).exists()) {
                try {
                    createWorktree(localPath, sa.getBranchName(), studentName);
                } catch (IOException e) {
                    throw new RuntimeException(
                            "Create worktree failed for branch " + sa.getBranchName(),
                            e
                    );
                }
            }
        }
    }

    public boolean workspaceExists(String classCode, String assignmentCode) {
        String assignmentPath = getAssignmentPath(classCode, assignmentCode);
        File gitDir = new File(assignmentPath, ".git");
        return gitDir.exists();
    }

    public String getWorkspacePath(String classCode, String assignmentCode) {
        return getAssignmentPath(classCode, assignmentCode);
    }

    public boolean isWorkspaceSetup(String localPath) {
        if (localPath == null || localPath.isEmpty()) {
            return false;
        }
        
        File workspaceDir = new File(localPath);
        
        // Check if workspace folder exists
        if (!workspaceDir.exists() || !workspaceDir.isDirectory()) {
            return false;
        }
        
        // Check if it's a git repository
        File gitDir = new File(workspaceDir, ".git");
        if (!gitDir.exists()) {
            return false;
        }
        
        // Check if students/ folder exists (indicating worktree setup)
        File studentsDir = new File(workspaceDir, "students");
        if (!studentsDir.exists() || !studentsDir.isDirectory()) {
            return false;
        }
        
        return true;
    }
    
}

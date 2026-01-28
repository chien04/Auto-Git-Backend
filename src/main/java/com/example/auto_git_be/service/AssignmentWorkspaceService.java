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

/**
 * Service for managing assignment workspaces with git worktrees
 * Supports multi-student workspace where teacher can view all students' code
 */
@Service
public class AssignmentWorkspaceService {

    @Value("${workspace.root.path:D:/Assignments}")
    private String workspaceRootPath;

    /**
     * Get assignment workspace path
     */
    private String getAssignmentPath(String classCode, String assignmentCode) {
        return workspaceRootPath + "/" + classCode + "-" + assignmentCode;
    }

    /**
     * Sanitize file name (remove special characters)
     */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_]", "");
    }

    /**
     * Execute git command
     */
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

    /**
     * Clone repository with authentication
     */
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

    /**
     * Add students folder to .gitignore and auto-commit if modified
     */
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

    /**
     * Create git worktree for a student branch
     */
    private void createWorktree(String repoPath, String branchName, String studentName) throws IOException {
        String sanitizedName = sanitizeFileName(studentName);
        String worktreePath = repoPath + "/students/" + sanitizedName;

        File worktreeDir = new File(worktreePath);
        if (worktreeDir.exists()) {
            return;
        }
        
        // Fetch the branch first
        try {
            executeGitCommand(repoPath, "git", "fetch", "origin", branchName);
        } catch (IOException e) {
        }

        // Create worktree
        try {
            executeGitCommand(repoPath, "git", "worktree", "add", worktreePath, branchName);
        } catch (IOException e) {
            // If branch doesn't exist remotely yet, try to create worktree anyway
            throw e;
        }
    }

    /**
     * Setup assignment workspace with worktrees for all students
     * Uses teacher's actual localPath from database instead of default path
     */
    public String setupAssignmentWorkspace(Assignment assignment, List<StudentAssignment> studentAssignments, String token, String localPath) throws IOException {
        if (localPath == null || localPath.isEmpty()) {
            // Fallback to old behavior if no localPath provided
            String classCode = assignment.getClassRoom().getClassCode();
            String assignmentCode = assignment.getAssignmentCode();
            localPath = getAssignmentPath(classCode, assignmentCode);
        }
        
        File assignmentDir = new File(localPath);

        // Clone repository if not exists
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

    /**
     * Sync assignment workspace - fetch and pull latest code from all branches
     * Uses teacher's actual localPath from database instead of default path
     */
    public void syncAssignmentWorkspace(Assignment assignment, List<StudentAssignment> studentAssignments, String localPath) throws IOException {
        if (localPath == null || localPath.isEmpty()) {
            // Fallback to old behavior if no localPath provided
            String classCode = assignment.getClassRoom().getClassCode();
            String assignmentCode = assignment.getAssignmentCode();
            localPath = getAssignmentPath(classCode, assignmentCode);
        }
        
        File assignmentDir = new File(localPath);

        if (!assignmentDir.exists()) {
            throw new IOException("Workspace does not exist: " + localPath + ". Please setup workspace first.");
        }

        // Fetch all branches
        executeGitCommand(localPath, "git", "fetch", "--all");

        // Pull teacher branch
        try {
            executeGitCommand(localPath, "git", "pull", "origin", "teacher");
        } catch (IOException e) {
        }

        // Pull each student worktree
        for (StudentAssignment sa : studentAssignments) {
            if (sa.getBranchName() == null || sa.getBranchName().isEmpty()) {
                continue;
            }

            String studentName = sa.getStudent().getStudentName();
            String sanitizedName = sanitizeFileName(studentName);
            String worktreePath = localPath + "/students/" + sanitizedName;
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

    /**
     * Update assignment workspace when new students join
     * Uses teacher's actual localPath from database instead of default path
     */
    public void updateAssignmentWorkspace(Assignment assignment, List<StudentAssignment> studentAssignments, String localPath) throws IOException {
        if (localPath == null || localPath.isEmpty()) {
            // Fallback to old behavior if no localPath provided
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
            String sanitizedName = sanitizeFileName(studentName);
            String worktreePath = localPath + "/students/" + sanitizedName;

            if (!new File(worktreePath).exists()) {
                try {
                    createWorktree(localPath, sa.getBranchName(), studentName);
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Check if workspace exists
     */
    public boolean workspaceExists(String classCode, String assignmentCode) {
        String assignmentPath = getAssignmentPath(classCode, assignmentCode);
        File gitDir = new File(assignmentPath, ".git");
        return gitDir.exists();
    }

    /**
     * Get workspace path
     */
    public String getWorkspacePath(String classCode, String assignmentCode) {
        return getAssignmentPath(classCode, assignmentCode);
    }
    
    /**
     * Check if workspace is set up with worktree structure
     * A workspace is considered "set up" if:
     * 1. Main folder exists
     * 2. It's a git repository (.git exists)
     * 3. students/ folder exists (worktree structure)
     */
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
    
    /**
     * Check if workspace is set up for an assignment
     */
    public boolean isWorkspaceSetup(Assignment assignment, String localPath) {
        return isWorkspaceSetup(localPath);
    }
}

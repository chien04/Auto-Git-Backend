package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated This service is deprecated with the new architecture.
 * With the new architecture, workspaces should be created per assignment, not per class.
 * This service assumes ClassRoom has repository and Student has branchName,
 * which is no longer true. These fields now belong to Assignment and StudentAssignment.
 * 
 * TODO: Create AssignmentWorkspaceService that works with Assignment repositories
 * and StudentAssignment branches instead.
 */
@Deprecated
@Service
public class WorkspaceService {

    @Value("${workspace.root.path:D:/ClassRooms}")
    private String workspaceRootPath;

    /**
     * Setup workspace with worktrees for all students
     * @deprecated ClassRoom no longer has repository. Use assignment-based workspace instead.
     */
    @Deprecated
    public String setupClassroomWorkspace(ClassRoom classroom, List<Student> students) throws IOException {
        throw new UnsupportedOperationException(
                "setupClassroomWorkspace is no longer supported. " +
                "With the new architecture, classes don't have repositories. " +
                "Use AssignmentWorkspaceService (to be created) instead."
        );
        /*
        String classroomPath = getClassroomPath(classroom);
        File classroomDir = new File(classroomPath);

        // Create classroom directory if not exists
        if (!classroomDir.exists()) {
            classroomDir.mkdirs();
        }

        // Initialize git repo if not exists
        File gitDir = new File(classroomDir, ".git");
        if (!gitDir.exists()) {
            cloneRepository(classroom.getRepoUrl(), classroomPath);
        }

        // Create worktrees for all students
        for (Student student : students) {
            createWorktree(classroomPath, student.getBranchName(), student.getStudentName());
        }

        // Generate VS Code workspace file
        String workspaceFilePath = generateWorkspaceFile(classroom, students, classroomPath);

        return workspaceFilePath;
        */
    }

    /**
     * Clone repository to local path
     */
    private void cloneRepository(String repoUrl, String targetPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("git", "clone", repoUrl, targetPath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Git clone failed with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git clone interrupted", e);
        }

        // Add students/ to .gitignore to prevent pushing worktrees
        addStudentsFolderToGitignore(targetPath);
        
        // Checkout teacher branch
        executeGitCommand(targetPath, "git", "checkout", "-b", "teacher", "origin/teacher");
    }

    /**
     * Add students folder to .gitignore
     */
    private void addStudentsFolderToGitignore(String repoPath) throws IOException {
        File gitignoreFile = new File(repoPath, ".gitignore");
        String studentsEntry = "students/\n";
        
        try {
            // Read existing .gitignore
            String content = "";
            if (gitignoreFile.exists()) {
                content = new String(java.nio.file.Files.readAllBytes(gitignoreFile.toPath()));
            }
            
            // Check if students/ already in .gitignore
            if (!content.contains("students/")) {
                // Append students/ to .gitignore
                java.nio.file.Files.write(
                    gitignoreFile.toPath(), 
                    studentsEntry.getBytes(), 
                    java.nio.file.StandardOpenOption.CREATE, 
                    java.nio.file.StandardOpenOption.APPEND
                );
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

        // Fetch all branches first
        executeGitCommand(repoPath, "git", "fetch", "origin");

        // Create worktree
        executeGitCommand(repoPath, "git", "worktree", "add", worktreePath, branchName);
    }

    /**
     * Execute git command
     */
    private void executeGitCommand(String workingDir, String... command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Git command failed: " + String.join(" ", command));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command interrupted", e);
        }
    }

    /**
     * Generate VS Code workspace file
     */
    private String generateWorkspaceFile(ClassRoom classroom, List<Student> students, String classroomPath) throws IOException {
        StringBuilder workspaceContent = new StringBuilder();
        workspaceContent.append("{\n");
        workspaceContent.append("  \"folders\": [\n");

        // Add main folder (teacher)
        workspaceContent.append("    {\n");
        workspaceContent.append("      \"name\": \"📚 ").append(classroom.getName()).append(" (Teacher)\",\n");
        workspaceContent.append("      \"path\": \".\"\n");
        workspaceContent.append("    }");

        // Add student folders
        for (Student student : students) {
            String sanitizedName = sanitizeFileName(student.getStudentName());
            workspaceContent.append(",\n");
            workspaceContent.append("    {\n");
            workspaceContent.append("      \"name\": \"👤 ").append(student.getStudentName()).append("\",\n");
            workspaceContent.append("      \"path\": \"students/").append(sanitizedName).append("\"\n");
            workspaceContent.append("    }");
        }

        workspaceContent.append("\n  ],\n");
        workspaceContent.append("  \"settings\": {\n");
        workspaceContent.append("    \"files.exclude\": {\n");
        workspaceContent.append("      \"**/.git\": true\n");
        workspaceContent.append("    }\n");
        workspaceContent.append("  }\n");
        workspaceContent.append("}\n");

        // Write workspace file
        String workspaceFileName = classroom.getName() + "-" + classroom.getClassCode() + ".code-workspace";
        String workspaceFilePath = classroomPath + "/" + workspaceFileName;
        
        Path workspacePath = Paths.get(workspaceFilePath);
        Files.write(workspacePath, workspaceContent.toString().getBytes());

        return workspaceFilePath;
    }

    /**
     * Get classroom workspace path
     * @deprecated ClassRoom.getLocalPath() no longer exists
     */
    @Deprecated
    public String getClassroomPath(ClassRoom classroom) {
        throw new UnsupportedOperationException(
                "getClassroomPath is no longer supported. " +
                "ClassRoom.getLocalPath() field has been removed."
        );
        // Use saved localPath if available, otherwise fallback to default
        // if (classroom.getLocalPath() != null && !classroom.getLocalPath().isEmpty()) {
        //     return classroom.getLocalPath();
        // }
        // return workspaceRootPath + "/" + classroom.getName() + "-" + classroom.getClassCode();
    }

    /**
     * Get workspace file path
     */
    public String getWorkspaceFilePath(ClassRoom classroom) {
        String classroomPath = getClassroomPath(classroom);
        String workspaceFileName = classroom.getName() + "-" + classroom.getClassCode() + ".code-workspace";
        return classroomPath + "/" + workspaceFileName;
    }

    /**
     * Sanitize file name (remove special characters)
     */
    private String sanitizeFileName(String name) {
        return name.toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Check if workspace exists
     * @deprecated Use assignment-based workspace instead
     */
    @Deprecated
    public boolean workspaceExists(ClassRoom classroom) {
        return false; // Always return false since feature is deprecated
    }

    /**
     * Update workspace with new students
     * @deprecated Use assignment-based workspace instead
     */
    @Deprecated
    public void updateWorkspace(ClassRoom classroom, List<Student> students) throws IOException {
        throw new UnsupportedOperationException(
                "updateWorkspace is no longer supported. " +
                "Use assignment-based workspace instead."
        );
    }

    /**
     * Sync workspace - fetch and pull latest code from all branches
     * @deprecated Use assignment-based workspace instead
     */
    @Deprecated
    public void syncWorkspace(ClassRoom classroom, List<Student> students) throws IOException {
        throw new UnsupportedOperationException(
                "syncWorkspace is no longer supported. " +
                "Use assignment-based workspace instead."
        );
    }
}

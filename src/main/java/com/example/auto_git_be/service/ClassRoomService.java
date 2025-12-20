package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.CreateClassResponse;
import com.example.auto_git_be.dto.JoinClassResponse;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.ClassRoomRepository;
import com.example.auto_git_be.repository.StudentRepository;
import org.kohsuke.github.GHRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class ClassRoomService {

    @Autowired
    private ClassRoomRepository classRoomRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private GitHubService gitHubService;

    @Autowired
    private WorkspaceService workspaceService;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();

    /**
     * Create a new classroom with GitHub repository
     */
    @Transactional
    public CreateClassResponse createClass(String className, String localPath, java.time.LocalDateTime deadline, User teacher) {
        try {
            // Generate unique class code
            String classCode = generateUniqueClassCode();

            // Create GitHub repository
            String repoName = sanitizeRepoName(className + "-" + classCode);
            String description = "Classroom repository for " + className;
            
            GHRepository ghRepo = gitHubService.createRepository(repoName, description);
            
            // Create teacher branch
            gitHubService.createBranch(ghRepo.getFullName(), "teacher", ghRepo.getDefaultBranch());
            
            // Create full path: parentPath/className-classCode
            String fullPath = localPath + "/" + className + "-" + classCode;
            
            // Create classroom entity
            ClassRoom classRoom = ClassRoom.builder()
                    .name(className)
                    .classCode(classCode)
                    .repoUrl(ghRepo.getHtmlUrl().toString())
                    .repoName(ghRepo.getFullName())
                    .githubRepoId(ghRepo.getId())
                    .teacher(teacher)
                    .localPath(fullPath)
                    .isActive(true)
                    .deadline(deadline)
                    .build();

            classRoom = classRoomRepository.save(classRoom);

            // Clone repository to teacher's chosen workspace
            try {
                workspaceService.setupClassroomWorkspace(classRoom, List.of());
                System.out.println("Repository cloned to: " + fullPath);
            } catch (Exception e) {
                System.err.println("Warning: Failed to clone repository: " + e.getMessage());
                // Don't fail the whole operation if clone fails
            }

            return CreateClassResponse.builder()
                    .classId(classRoom.getId().toString())
                    .classCode(classRoom.getClassCode())
                    .repoUrl(classRoom.getRepoUrl())
                    .className(classRoom.getName())
                    .token(gitHubService.getToken())
                    .branch("teacher")
                    .deadline(classRoom.getDeadline())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to create classroom: " + e.getMessage(), e);
        }
    }

    /**
     * Student joins a class
     */
    @Transactional
    public JoinClassResponse joinClass(String studentName, String classCode, String localPath, User user) {
        try {
            // Find a classroom
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found with code: " + classCode));

            // Check if a student already enrolled
            if (studentRepository.existsByUserAndClassRoom(user, classRoom)) {
                // Return existing enrollment
                Student existingStudent = studentRepository.findByUserAndClassRoom(user, classRoom)
                        .orElseThrow(() -> new RuntimeException("Student enrollment error"));
                
                return JoinClassResponse.builder()
                        .repoUrl(classRoom.getRepoUrl())
                        .branch(existingStudent.getBranchName())
                        .token(existingStudent.getGithubToken())
                        .studentId(existingStudent.getId().toString())
                        .deadline(classRoom.getDeadline())
                        .deadline(classRoom.getDeadline())
                        .build();
            }

            // Create student branch name - use student name instead of user ID
            String sanitizedName = studentName.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
            String branchName = "student/" + sanitizedName;

            // Create branch in GitHub
            gitHubService.createBranch(classRoom.getRepoName(), branchName, "main");

            // Generate token for student (simplified - use actual token generation in production)
            String studentToken = gitHubService.generateStudentToken(classRoom.getRepoName(), branchName);

            // Create full path: parentPath/classCode-student-studentName
            String fullPath = localPath + "/" + classCode + "-student-" + sanitizedName;

            // Create student enrollment
            Student student = Student.builder()
                    .user(user)
                    .classRoom(classRoom)
                    .studentName(studentName)
                    .branchName(branchName)
                    .githubToken(studentToken)
                    .localPath(fullPath)
                    .commitCount(0)
                    .build();

            student = studentRepository.save(student);

            // Create worktree for this student in teacher's workspace
            try {
                String classroomPath = workspaceService.getClassroomPath(classRoom);
                java.io.File classroomDir = new java.io.File(classroomPath);
                
                if (classroomDir.exists() && new java.io.File(classroomDir, ".git").exists()) {
                    // Teacher's workspace exists, create worktree
                    workspaceService.updateWorkspace(classRoom, List.of(student));
                    System.out.println("Created worktree for student: " + studentName);
                } else {
                    System.out.println("Teacher workspace not found yet, worktree will be created when teacher opens workspace");
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to create worktree: " + e.getMessage());
                // Don't fail student join if worktree creation fails
            }

            return JoinClassResponse.builder()
                    .repoUrl(classRoom.getRepoUrl())
                    .branch(branchName)
                    .token(studentToken)
                    .deadline(classRoom.getDeadline())
                    .studentId(student.getId().toString())
                    .deadline(classRoom.getDeadline())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to join class: " + e.getMessage(), e);
        }
    }

    /**
     * Get all students in a classroom
     */
    public List<Student> getStudentsInClass(String classCode) {
        ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        
        return studentRepository.findByClassRoom(classRoom);
    }

    /**
     * Get all classes taught by a teacher
     */
    public List<ClassRoom> getTeacherClasses(User teacher) {
        return classRoomRepository.findByTeacherAndIsActive(teacher, true);
    }

    /**
     * Get all classes a student is enrolled in
     */
    public List<Student> getStudentEnrollments(User user) {
        return studentRepository.findByUser(user);
    }

    /**
     * Generate unique class code
     */
    private String generateUniqueClassCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (classRoomRepository.existsByClassCode(code));
        return code;
    }

    /**
     * Generate random alphanumeric code
     */
    private String generateRandomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }

    /**
     * Sanitize repository name
     */
    private String sanitizeRepoName(String name) {
        // GitHub repo names: alphanumeric, hyphens, underscores
        return name.toLowerCase()
                .replaceAll("[^a-z0-9-_]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Get classroom by code
     */
    public ClassRoom getClassByCode(String classCode) {
        return classRoomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new RuntimeException("Class not found"));
    }

    /**
     * Get students by class code
     */
    public List<Student> getStudentsByClassCode(String classCode) {
        ClassRoom classRoom = getClassByCode(classCode);
        return studentRepository.findByClassRoom(classRoom);
    }

    /**
     * Get student by user and classroom
     */
    public Student getStudentByUserAndClass(User user, ClassRoom classRoom) {
        return studentRepository.findByUserAndClassRoom(user, classRoom).orElse(null);
    }
    
    /**
     * Student leaves a class - delete branch
     */
    @Transactional
    public void leaveClass(String classCode, User user) {
        try {
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            
            Student student = studentRepository.findByUserAndClassRoom(user, classRoom)
                    .orElseThrow(() -> new RuntimeException("Student not enrolled in this class"));
            
            // Delete branch from GitHub
            gitHubService.deleteBranch(classRoom.getRepoName(), student.getBranchName());
            
            // Delete student enrollment
            studentRepository.delete(student);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to leave class: " + e.getMessage(), e);
        }
    }
    
    /**
     * Teacher removes a student from class
     */
    @Transactional
    public void removeStudentFromClass(String classCode, Long studentId, User teacher) {
        try {
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            
            // Verify teacher ownership
            if (!classRoom.getTeacher().getId().equals(teacher.getId())) {
                throw new RuntimeException("Only the teacher can remove students from their class");
            }
            
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));
            
            // Verify student belongs to this class
            if (!student.getClassRoom().getId().equals(classRoom.getId())) {
                throw new RuntimeException("Student not in this class");
            }
            
            // Delete branch from GitHub
            gitHubService.deleteBranch(classRoom.getRepoName(), student.getBranchName());
            
            // Delete student enrollment
            studentRepository.delete(student);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove student: " + e.getMessage(), e);
        }
    }
    
    /**
     * Teacher deletes a class - delete entire repository
     */
    @Transactional
    public void deleteClass(String classCode, User teacher) {
        try {
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            
            // Verify teacher ownership
            if (!classRoom.getTeacher().getId().equals(teacher.getId())) {
                throw new RuntimeException("Only the teacher who created the class can delete it");
            }
            
            // Delete repository from GitHub
            gitHubService.deleteRepository(classRoom.getRepoName());
            
            // Delete all student enrollments
            List<Student> students = studentRepository.findByClassRoom(classRoom);
            studentRepository.deleteAll(students);
            
            // Mark classroom as inactive (soft delete)
            classRoom.setIsActive(false);
            classRoomRepository.save(classRoom);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete class: " + e.getMessage(), e);
        }
    }

    /**
     * Get commits for a specific branch
     */
    public List<Map<String, Object>> getCommits(String classCode, String branchName) {
        try {
            System.out.println("ClassRoomService.getCommits called with classCode: " + classCode + ", branchName: " + branchName);
            
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            
            System.out.println("Found classroom: " + classRoom.getName() + ", repo: " + classRoom.getRepoName());
            
            List<org.kohsuke.github.GHCommit> ghCommits = gitHubService.getCommits(classRoom.getRepoName(), branchName);
            
            List<Map<String, Object>> commits = new java.util.ArrayList<>();
            for (org.kohsuke.github.GHCommit commit : ghCommits) {
                Map<String, Object> commitInfo = new java.util.HashMap<>();
                commitInfo.put("sha", commit.getSHA1());
                commitInfo.put("message", commit.getCommitShortInfo().getMessage());
                commitInfo.put("author", commit.getCommitShortInfo().getAuthor().getName());
                commitInfo.put("date", commit.getCommitShortInfo().getCommitDate());
                commits.add(commitInfo);
            }
            
            System.out.println("Returning " + commits.size() + " commits");
            return commits;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get commits: " + e.getMessage(), e);
        }
    }

    /**
     * Get commit URL for viewing code
     */
    public String getCommitUrl(String classCode, String branchName, String commitSha) {
        try {
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            
            return gitHubService.getCommitUrl(classRoom.getRepoName(), commitSha);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get commit URL: " + e.getMessage(), e);
        }
    }

    /**
     * Get all classes by teacher
     */
    public List<ClassRoom> getClassesByTeacher(User teacher) {
        return classRoomRepository.findByTeacher(teacher);
    }
}

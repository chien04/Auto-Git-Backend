package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.entity.TeacherAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.AssignmentRepository;
import com.example.auto_git_be.repository.StudentRepository;
import com.example.auto_git_be.repository.StudentAssignmentRepository;
import org.kohsuke.github.GHRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Service
public class AssignmentService {
    
    @Autowired
    private AssignmentRepository assignmentRepository;
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private StudentAssignmentRepository studentAssignmentRepository;
    
    @Autowired
    private GitHubService gitHubService;
    
    @Autowired
    private AssignmentWorkspaceService assignmentWorkspaceService;
    
    @Autowired
    private TeacherAssignmentService teacherAssignmentService;
    
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * Create a new assignment with GitHub repository
     */
    @Transactional
    public Assignment createAssignment(ClassRoom classRoom, String title, String description, 
                                      java.time.LocalDateTime deadline) {
        try {
            // Generate unique assignment code
            String assignmentCode = generateUniqueAssignmentCode();
            
            // Create GitHub repository for this assignment
            String repoName = sanitizeRepoName(classRoom.getName() + "-" + title + "-" + assignmentCode);
            String repoDescription = "Assignment: " + title + " for class " + classRoom.getName();
            
            GHRepository ghRepo = gitHubService.createRepository(repoName, repoDescription);
            
            // Create teacher branch
            gitHubService.createBranch(ghRepo.getFullName(), "teacher", ghRepo.getDefaultBranch());
            
            // Auto-create workflow file in teacher branch
            gitHubService.createWorkflowFile(ghRepo.getFullName(), "teacher");
            
            // Auto-create sample test cases in teacher branch
            gitHubService.createSampleTestCases(ghRepo.getFullName(), "teacher");
            
            // Create assignment entity
            Assignment assignment = Assignment.builder()
                    .classRoom(classRoom)
                    .title(title)
                    .description(description)
                    .assignmentCode(assignmentCode)
                    .repoUrl(ghRepo.getHtmlUrl().toString())
                    .repoName(ghRepo.getFullName())
                    .githubRepoId(ghRepo.getId())
                    .isActive(true)
                    .deadline(deadline)
                    .build();
            
            return assignmentRepository.save(assignment);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create assignment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get assignment by code
     */
    public Assignment getAssignmentByCode(String assignmentCode) {
        return assignmentRepository.findByAssignmentCode(assignmentCode)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));
    }
    
    /**
     * Get all assignments in a classroom
     */
    public List<Assignment> getAssignmentsByClassroom(ClassRoom classRoom) {
        return assignmentRepository.findByClassRoomAndIsActive(classRoom, true);
    }
    
    /**
     * Get all students in an assignment
     */
    public List<StudentAssignment> getStudentsInAssignment(Assignment assignment) {
        return studentAssignmentRepository.findByAssignment(assignment);
    }
    
    /**
     * Delete assignment
     */
    @Transactional
    public void deleteAssignment(String assignmentCode, User teacher) {
        try {
            Assignment assignment = getAssignmentByCode(assignmentCode);
            
            // Verify teacher ownership
            if (!assignment.getClassRoom().getTeacher().getId().equals(teacher.getId())) {
                throw new RuntimeException("Only the teacher who created the class can delete this assignment");
            }
            
            // Delete repository from GitHub
            gitHubService.deleteRepository(assignment.getRepoName());
            
            // Delete all student enrollments
            List<StudentAssignment> studentAssignments = studentAssignmentRepository.findByAssignment(assignment);
            studentAssignmentRepository.deleteAll(studentAssignments);
            
            // Mark assignment as inactive (soft delete)
            assignment.setIsActive(false);
            assignmentRepository.save(assignment);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete assignment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Student joins an assignment (must be enrolled in class first)
     */
    @Transactional
    public com.example.auto_git_be.dto.JoinAssignmentResponse joinAssignment(
            String assignmentCode, String localPath, User user) {
        try {
            // Find assignment
            Assignment assignment = getAssignmentByCode(assignmentCode);
            
            // Find student enrollment in the class
            Student student = studentRepository.findByUserAndClassRoom(user, assignment.getClassRoom())
                    .orElseThrow(() -> new RuntimeException("You must join the class before joining assignments"));
            
            // Check if student already joined this assignment
            if (studentAssignmentRepository.existsByStudentAndAssignment(student, assignment)) {
                // Return existing enrollment
                StudentAssignment existing = studentAssignmentRepository
                        .findByStudentAndAssignment(student, assignment)
                        .orElseThrow(() -> new RuntimeException("Assignment enrollment error"));
                
                // Update localPath if provided and not empty
                if (localPath != null && !localPath.trim().isEmpty()) {
                    existing.setLocalPath(localPath);
                    studentAssignmentRepository.save(existing);
                }
                
                String githubToken = gitHubService.getToken();
                
                return com.example.auto_git_be.dto.JoinAssignmentResponse.builder()
                        .repoUrl(assignment.getRepoUrl())
                        .branch(existing.getBranchName())
                        .token(githubToken) // Always get from env for security
                        .studentId(student.getId().toString())
                        .assignmentTitle(assignment.getTitle())
                        .deadline(assignment.getDeadline())
                        .build();
            }
            
            // Create student branch name
            String sanitizedName = student.getStudentName().replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
            String branchName = "student-" + sanitizedName;  // Changed to "student-" prefix for better pattern matching
            
            // Create branch in GitHub from teacher branch (has test-cases and workflow)
            gitHubService.createBranch(assignment.getRepoName(), branchName, "teacher");
            
            // Generate token for student
            String studentToken = gitHubService.generateStudentToken(assignment.getRepoName(), branchName);
            
            // Create student-assignment enrollment
            StudentAssignment studentAssignment = StudentAssignment.builder()
                    .student(student)
                    .assignment(assignment)
                    .branchName(branchName)
                    // githubToken removed - always get from GitHubService for security
                    .localPath(localPath != null && !localPath.trim().isEmpty() ? localPath : null)
                    .commitCount(0)
                    .build();
            
            studentAssignment = studentAssignmentRepository.save(studentAssignment);
            
            // Auto-create worktree for teacher (if teacher has workspace setup)
            try {
                User teacher = assignment.getClassRoom().getTeacher();
                Optional<TeacherAssignment> teacherAssignment = teacherAssignmentService.getTeacherAssignment(teacher, assignment);
                
                if (teacherAssignment.isPresent() && teacherAssignment.get().getLocalPath() != null) {
                    String teacherLocalPath = teacherAssignment.get().getLocalPath();
                    
                    // Check if workspace is properly set up with worktree structure
                    if (assignmentWorkspaceService.isWorkspaceSetup(teacherLocalPath)) {
                        // Get all students for this assignment
                        List<StudentAssignment> allStudents = studentAssignmentRepository.findByAssignment(assignment);
                        
                        // Update workspace (create worktree for new student) with actual localPath
                        assignmentWorkspaceService.updateAssignmentWorkspace(assignment, allStudents, teacherLocalPath);
                    }
                }
            } catch (Exception workspaceError) {
                // Don't fail student join if worktree creation fails
            }
            
            String githubToken = gitHubService.getToken();
            
            return com.example.auto_git_be.dto.JoinAssignmentResponse.builder()
                    .repoUrl(assignment.getRepoUrl())
                    .branch(branchName)
                    .token(githubToken) // Always get from env for security
                    .studentId(student.getId().toString())
                    .assignmentTitle(assignment.getTitle())
                    .deadline(assignment.getDeadline())
                    .build();
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to join assignment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Update commit count for user's student assignment
     */
    @Transactional
    public void updateCommitCountForUser(String assignmentCode, User user) {
        try {
            Assignment assignment = getAssignmentByCode(assignmentCode);
            
            // Find all Student records for this user
            List<Student> students = studentRepository.findByUser(user);
            
            // Try to find StudentAssignment with any of these student IDs
            StudentAssignment studentAssignment = null;
            for (Student student : students) {
                Optional<StudentAssignment> optional = studentAssignmentRepository.findByStudentAndAssignment(student, assignment);
                if (optional.isPresent()) {
                    studentAssignment = optional.get();
                    break;
                }
            }
            
            if (studentAssignment == null) {
                throw new RuntimeException("StudentAssignment not found for user " + user.getEmail());
            }
            
            // Get commit count from GitHub
            String repoFullName = assignment.getRepoUrl()
                    .replace("https://github.com/", "")
                    .replace(".git", "")
                    .trim();
            String branchName = studentAssignment.getBranchName();
            
            int commitCount = gitHubService.getCommitCount(repoFullName, branchName);
            
            // Update student assignment without an initial commit
            studentAssignment.setCommitCount(commitCount - 1);
            studentAssignment.setLastCommitAt(java.time.LocalDateTime.now());
            studentAssignmentRepository.save(studentAssignment);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to update commit count: " + e.getMessage(), e);
        }
    }
    
    /**
     * Update student score from GitHub Actions
     * Converts score from 0-100 to 0-10 scale
     */
    @Transactional
    public void updateStudentScore(String repoFullName, String branchName, Integer scoreOutOf100) {
        try {
            // Find assignment by repo URL
            String repoUrl = "https://github.com/" + repoFullName;
            Assignment assignment = assignmentRepository.findByRepoUrl(repoUrl)
                    .orElseThrow(() -> new RuntimeException("Assignment not found for repo: " + repoUrl));
            
            // Find student assignment by branch name
            StudentAssignment studentAssignment = studentAssignmentRepository
                    .findByAssignmentAndBranchName(assignment, branchName)
                    .orElseThrow(() -> new RuntimeException("Student assignment not found for branch: " + branchName));
            
            // Convert score from 0-100 to 0-10
            Double scoreOutOf10 = scoreOutOf100 / 10.0;
            
            // Update score
            studentAssignment.setScore(scoreOutOf10);
            studentAssignmentRepository.save(studentAssignment);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to update score: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate unique assignment code
     */
    private String generateUniqueAssignmentCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (assignmentRepository.existsByAssignmentCode(code));
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
        return name.toLowerCase()
                .replaceAll("[^a-z0-9-_]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}

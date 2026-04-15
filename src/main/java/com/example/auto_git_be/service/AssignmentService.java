package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.JoinAssignmentResponse;
import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.entity.TeacherAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.AssignmentRepository;
import com.example.auto_git_be.repository.StudentRepository;
import com.example.auto_git_be.repository.StudentAssignmentRepository;
import com.example.auto_git_be.repository.TeacherAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.kohsuke.github.GHRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AssignmentService {
    
    private final AssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final StudentAssignmentRepository studentAssignmentRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final GitHubService gitHubService;
    private final AssignmentWorkspaceService assignmentWorkspaceService;
    private final TeacherAssignmentService teacherAssignmentService;
    private final NotificationService notificationService;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();

    @Transactional
    public Assignment createAssignment(ClassRoom classRoom, String title, String description, 
                                      LocalDateTime deadline) {
        try {
            String assignmentCode = generateUniqueAssignmentCode();

            String repoName = sanitizeRepoName(title + "-" + assignmentCode);
            String repoDescription = "Assignment: " + title + " for class " + classRoom.getName();
            
            GHRepository ghRepo = gitHubService.createRepository(repoName, repoDescription);

            gitHubService.createBranch(ghRepo.getFullName(), "teacher", ghRepo.getDefaultBranch());
            gitHubService.createWorkflowFile(ghRepo.getFullName(), "teacher", assignmentCode);

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

    public List<StudentAssignment> getStudentsInAssignment(Assignment assignment) {
        return studentAssignmentRepository.findByAssignment(assignment);
    }

    @Transactional(readOnly = true)
    public String getStudentLocalPath(String assignmentCode, User user) {
        Assignment assignment = getAssignmentByCode(assignmentCode);

        Student student = studentRepository.findByUserAndClassRoom(user, assignment.getClassRoom())
                .orElseThrow(() -> new RuntimeException("You must join the class before opening assignments"));

        StudentAssignment studentAssignment = studentAssignmentRepository
                .findByStudentAndAssignment(student, assignment)
                .orElseThrow(() -> new RuntimeException("Student assignment not found"));

        return studentAssignment.getLocalPath();
    }

        @Transactional(readOnly = true)
        public StudentAssignment getStudentAssignmentInfo(String assignmentCode, User user) {
        Assignment assignment = getAssignmentByCode(assignmentCode);

        Student student = studentRepository.findByUserAndClassRoom(user, assignment.getClassRoom())
            .orElseThrow(() -> new RuntimeException("You must join the class before opening assignments"));

        return studentAssignmentRepository
            .findByStudentAndAssignment(student, assignment)
            .orElseThrow(() -> new RuntimeException("Student assignment not found"));
        }

    @Transactional
    public void deleteAssignment(String assignmentCode, User teacher) {
        try {
            Assignment assignment = getAssignmentByCode(assignmentCode);
            
            if (!assignment.getClassRoom().getTeacher().getId().equals(teacher.getId())) {
                throw new RuntimeException("Only the teacher who created the class can delete this assignment");
            }

            gitHubService.deleteRepository(assignment.getRepoName());
            
            List<StudentAssignment> studentAssignments = studentAssignmentRepository.findByAssignment(assignment);
            studentAssignmentRepository.deleteAll(studentAssignments);

            List<TeacherAssignment> teacherAssignments = teacherAssignmentRepository.findByAssignment(assignment);
            teacherAssignmentRepository.deleteAll(teacherAssignments);

            assignment.setIsActive(false);
            assignmentRepository.save(assignment);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete assignment: " + e.getMessage(), e);
        }
    }

    @Transactional
    public JoinAssignmentResponse joinAssignment(
            String assignmentCode, String localPath, User user) {
        try {
            Assignment assignment = getAssignmentByCode(assignmentCode);
            
            Student student = studentRepository.findByUserAndClassRoom(user, assignment.getClassRoom())
                    .orElseThrow(() -> new RuntimeException("You must join the class before joining assignments"));
            
            if (studentAssignmentRepository.existsByStudentAndAssignment(student, assignment)) {
                StudentAssignment existing = studentAssignmentRepository
                        .findByStudentAndAssignment(student, assignment)
                        .orElseThrow(() -> new RuntimeException("Assignment enrollment error"));
                
                if (localPath != null && !localPath.trim().isEmpty()) {
                    existing.setLocalPath(localPath);
                    studentAssignmentRepository.save(existing);
                }

                // Generate GitHub token for a student to push code
                String githubToken = gitHubService.generateStudentToken(
                    assignment.getRepoName(), 
                    existing.getBranchName()
                );

                return JoinAssignmentResponse.builder()
                        .repoUrl(assignment.getRepoUrl())
                        .branch(existing.getBranchName())
                        .token(githubToken)
                        .studentId(student.getId().toString())
                        .assignmentTitle(assignment.getTitle())
                        .deadline(assignment.getDeadline())
                        .build();
            }
            
            String sanitizedName = student.getStudentName().replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
            String branchName = "student-" + sanitizedName;
            
            gitHubService.createBranch(assignment.getRepoName(), branchName, "teacher");

            StudentAssignment studentAssignment = StudentAssignment.builder()
                    .student(student)
                    .assignment(assignment)
                    .branchName(branchName)
                    .localPath(localPath != null && !localPath.trim().isEmpty() ? localPath : null)
                    .commitCount(0)
                    .build();
            
            studentAssignmentRepository.save(studentAssignment);
            
            // Auto-create worktree for teacher
            try {
                User teacher = assignment.getClassRoom().getTeacher();
                Optional<TeacherAssignment> teacherAssignment = teacherAssignmentService.getTeacherAssignment(teacher, assignment);
                
                if (teacherAssignment.isPresent() && teacherAssignment.get().getLocalPath() != null) {
                    String teacherLocalPath = teacherAssignment.get().getLocalPath();
                    
                    if (assignmentWorkspaceService.isWorkspaceSetup(teacherLocalPath)) {
                        List<StudentAssignment> allStudents = studentAssignmentRepository.findByAssignment(assignment);
                        assignmentWorkspaceService.updateAssignmentWorkspaceAndCreateWorktree(assignment, allStudents, teacherLocalPath);
                    }
                }
            } catch (Exception workspaceError) {
                // Don't fail a student joins if worktree creation fails
            }

            // Generate GitHub token for a student to push code
            String githubToken = gitHubService.generateStudentToken(
                assignment.getRepoName(), 
                branchName
            );

            return JoinAssignmentResponse.builder()
                    .repoUrl(assignment.getRepoUrl())
                    .branch(branchName)
                    .token(githubToken)
                    .studentId(student.getId().toString())
                    .assignmentTitle(assignment.getTitle())
                    .deadline(assignment.getDeadline())
                    .build();
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to join assignment: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void updateCommitCountForUser(String assignmentCode, User user) {
        try {
            Assignment assignment = getAssignmentByCode(assignmentCode);
            
            List<Student> students = studentRepository.findByUser(user);
            
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

            studentAssignment.setCommitCount(studentAssignment.getCommitCount() + 1);
            studentAssignment.setLastCommitAt(LocalDateTime.now());
            studentAssignmentRepository.save(studentAssignment);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to update commit count: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void updateStudentScore(String repoFullName, String branchName, Integer scoreOutOf100) {
        try {
            String repoUrl = "https://github.com/" + repoFullName;
            Assignment assignment = assignmentRepository.findByRepoUrl(repoUrl)
                    .orElseThrow(() -> new RuntimeException("Assignment not found for repo: " + repoUrl));
            
            StudentAssignment studentAssignment = studentAssignmentRepository
                    .findByAssignmentAndBranchName(assignment, branchName)
                    .orElseThrow(() -> new RuntimeException("Student assignment not found for branch: " + branchName));
            
            Double scoreOutOf10 = scoreOutOf100 / 10.0;

            studentAssignment.setScore(scoreOutOf10);
            studentAssignmentRepository.save(studentAssignment);

            notificationService.notifyStudentOnGraded(studentAssignment.getStudent().getUser().getId(), scoreOutOf10);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to update score: " + e.getMessage(), e);
        }
    }

    private String generateUniqueAssignmentCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (assignmentRepository.existsByAssignmentCode(code));
        return code;
    }

    private String generateRandomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }

    private String sanitizeRepoName(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9-_]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}

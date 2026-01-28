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
    public CreateClassResponse createClass(String className, User teacher) {
        try {
            // Generate unique class code
            String classCode = generateUniqueClassCode();
            
            // Create classroom entity (no repository - repos are per assignment now)
            ClassRoom classRoom = ClassRoom.builder()
                    .name(className)
                    .classCode(classCode)
                    .teacher(teacher)
                    .isActive(true)
                    .build();

            classRoom = classRoomRepository.save(classRoom);

            return CreateClassResponse.builder()
                    .classId(classRoom.getId().toString())
                    .classCode(classRoom.getClassCode())
                    .className(classRoom.getName())
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
            // Find classroom
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found with code: " + classCode));

            // Check if student already enrolled
            if (studentRepository.existsByUserAndClassRoom(user, classRoom)) {
                // Return existing enrollment
                Student existingStudent = studentRepository.findByUserAndClassRoom(user, classRoom)
                        .orElseThrow(() -> new RuntimeException("Student enrollment error"));
                
                return JoinClassResponse.builder()
                        .studentId(existingStudent.getId().toString())
                        .build();
            }

            // Create student enrollment
            Student student = Student.builder()
                    .user(user)
                    .classRoom(classRoom)
                    .studentName(studentName)
                    .build();

            student = studentRepository.save(student);

            return JoinClassResponse.builder()
                    .studentId(student.getId().toString())
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
     * Student leaves a class
     * Note: With new architecture, this only removes the student from the class.
     * Student's branches in assignment repos are NOT deleted automatically.
     */
    @Transactional
    public void leaveClass(String classCode, User user) {
        try {
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            
            Student student = studentRepository.findByUserAndClassRoom(user, classRoom)
                    .orElseThrow(() -> new RuntimeException("Student not enrolled in this class"));
            
            // With new architecture: branches belong to assignments, not class
            // StudentAssignments will be cascade deleted due to orphanRemoval in Student entity
            
            // Delete student enrollment
            studentRepository.delete(student);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to leave class: " + e.getMessage(), e);
        }
    }
    
    /**
     * Teacher removes a student from class
     * Note: With new architecture, student's branches in assignment repos are NOT deleted.
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
            
            // With new architecture: branches belong to assignments, not class
            // StudentAssignments will be cascade deleted
            
            // Delete student enrollment
            studentRepository.delete(student);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove student: " + e.getMessage(), e);
        }
    }
    
    /**
     * Teacher deletes a class
     * Note: With new architecture, class doesn't have repository.
     * Assignment repositories will be cascade deleted through Assignment entities.
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
            
            // With new architecture: repos belong to assignments, not class
            // AssignmentService should handle deleting assignment repos
            
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
     * @deprecated This method is deprecated with the new architecture.
     * Use AssignmentService.getCommits(assignmentCode, branchName) instead.
     */
    @Deprecated
    public List<Map<String, Object>> getCommits(String classCode, String branchName) {
        throw new UnsupportedOperationException(
                "getCommits(classCode, branchName) is no longer supported. " +
                        "With the new architecture, classes don't have repositories. " +
                        "Use AssignmentService.getCommits(assignmentCode, branchName) instead."
        );
    }
        /*
        try {
            
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            
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
            
            return commits;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get commits: " + e.getMessage(), e);
        }
    }

    /**
     * Get commit URL for viewing code
     * @deprecated This method is deprecated with the new architecture.
     * Use AssignmentService.getCommitUrl(assignmentCode, commitSha) instead.
     */
    @Deprecated
    public String getCommitUrl(String classCode, String branchName, String commitSha) {
        throw new UnsupportedOperationException(
                "getCommitUrl(classCode, branchName, commitSha) is no longer supported. " +
                "With the new architecture, classes don't have repositories. " +
                "Use AssignmentService.getCommitUrl(assignmentCode, commitSha) instead."
        );
    }

    /**
     * Get all classes by teacher
     */
    public List<ClassRoom> getClassesByTeacher(User teacher) {
        return classRoomRepository.findByTeacher(teacher);
    }
}

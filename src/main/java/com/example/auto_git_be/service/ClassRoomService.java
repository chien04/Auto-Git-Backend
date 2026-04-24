package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.classroom.CreateClassResponse;
import com.example.auto_git_be.dto.classroom.JoinClassResponse;
import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.entity.TeacherAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.AssignmentRepository;
import com.example.auto_git_be.repository.ClassRoomRepository;
import com.example.auto_git_be.repository.CommentRepository;
import com.example.auto_git_be.repository.StudentAssignmentRepository;
import com.example.auto_git_be.repository.StudentRepository;
import com.example.auto_git_be.repository.TeacherAssignmentRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ClassRoomService {

    private final ClassRoomRepository classRoomRepository;
    private final StudentRepository studentRepository;
    private final GitHubService gitHubService;
    private final AssignmentRepository assignmentRepository;
    private final StudentAssignmentRepository studentAssignmentRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final CommentRepository commentRepository;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();

    @Transactional
    public CreateClassResponse createClass(String className, User teacher) {
        try {
            String classCode = generateUniqueClassCode();
            
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

    @Transactional
    public JoinClassResponse joinClass(String studentName, String classCode, User user) {
        try {
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found with code: " + classCode));

            if (studentRepository.existsByUserAndClassRoom(user, classRoom)) {
                Student existingStudent = studentRepository.findByUserAndClassRoom(user, classRoom)
                        .orElseThrow(() -> new RuntimeException("Student enrollment error"));
                
                return JoinClassResponse.builder()
                        .studentId(existingStudent.getId().toString())
                        .build();
            }

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

    public List<Student> getStudentsInClass(String classCode) {
        ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        
        return studentRepository.findByClassRoom(classRoom);
    }

    public List<ClassRoom> getTeacherClasses(User teacher) {
        return classRoomRepository.findByTeacherAndIsActive(teacher, true);
    }

    public List<Student> getStudentEnrollments(User user) {
        return studentRepository.findByUser(user);
    }

    private String generateUniqueClassCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (classRoomRepository.existsByClassCode(code));
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
        // GitHub repo names: alphanumeric, hyphens, underscores
        return name.toLowerCase()
                .replaceAll("[^a-z0-9-_]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    public ClassRoom getClassByCode(String classCode) {
        return classRoomRepository.findByClassCode(classCode)
                .orElseThrow(() -> new RuntimeException("Class not found"));
    }

    @Transactional
    public void leaveClass(String classCode, User user) {
        try {
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            
            Student student = studentRepository.findByUserAndClassRoom(user, classRoom)
                    .orElseThrow(() -> new RuntimeException("Student not enrolled in this class"));
            commentRepository.deleteByStudent(student);
            studentRepository.delete(student);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to leave class: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void removeStudentFromClass(String classCode, Long studentId, User teacher) {
        try {
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            
            if (!classRoom.getTeacher().getId().equals(teacher.getId())) {
                throw new RuntimeException("Only the teacher can remove students from their class");
            }
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));
            
            if (!student.getClassRoom().getId().equals(classRoom.getId())) {
                throw new RuntimeException("Student not in this class");
            }
            commentRepository.deleteByStudent(student);
            studentRepository.delete(student);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove student: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteClass(String classCode, User teacher) {
        try {
            ClassRoom classRoom = classRoomRepository.findByClassCode(classCode)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            
            if (!classRoom.getTeacher().getId().equals(teacher.getId())) {
                throw new RuntimeException("Only the teacher who created the class can delete it");
            }
            
            List<Assignment> assignments = assignmentRepository.findByClassRoom(classRoom);
            for (Assignment assignment : assignments) {
                try {
                    commentRepository.deleteByAssignment(assignment);
                    
                    List<StudentAssignment> studentAssignments = studentAssignmentRepository.findByAssignment(assignment);
                    studentAssignmentRepository.deleteAll(studentAssignments);
                    
                    List<TeacherAssignment> teacherAssignments = teacherAssignmentRepository.findByAssignment(assignment);
                    teacherAssignmentRepository.deleteAll(teacherAssignments);
                    assignment.setIsActive(false);
                    assignmentRepository.save(assignment);
                    try {
                        gitHubService.deleteRepository(assignment.getRepoName());
                    } catch (Exception githubError) {
                        System.err.println("Failed to delete GitHub repository for assignment "
                                + assignment.getAssignmentCode() + ": " + githubError.getMessage());
                    }
                } catch (Exception e) {
                    System.err.println("Failed to delete assignment " + assignment.getAssignmentCode() + ": " + e.getMessage());
                }
            }
            
            List<Student> students = studentRepository.findByClassRoom(classRoom);
            commentRepository.deleteByStudentIn(students);
            studentRepository.deleteAll(students);
            
            classRoom.setIsActive(false);
            classRoomRepository.save(classRoom);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete class: " + e.getMessage(), e);
        }
    }

    public List<ClassRoom> getClassesByTeacher(User teacher) {
        return classRoomRepository.findByTeacher(teacher);
    }
}


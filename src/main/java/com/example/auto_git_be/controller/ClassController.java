package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.classroom.CreateClassRequest;
import com.example.auto_git_be.dto.classroom.CreateClassResponse;
import com.example.auto_git_be.dto.classroom.JoinClassRequest;
import com.example.auto_git_be.dto.classroom.JoinClassResponse;
import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.MessageRepository;
import com.example.auto_git_be.service.AuthService;
import com.example.auto_git_be.service.ClassRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/class")
public class ClassController {

    private final ClassRoomService classRoomService;
    private final AuthService authService;
    private final MessageRepository messageRepository;

    @PostMapping("/create")
    public ResponseEntity<?> createClass(
            @RequestBody CreateClassRequest request,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User teacher = authService.getUserFromToken(token);

        CreateClassResponse response = classRoomService.createClass(
                request.getClassName(),
                teacher
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/join")
    public ResponseEntity<JoinClassResponse> joinClass(
            @RequestBody JoinClassRequest request,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User student = authService.getUserFromToken(token);

        JoinClassResponse response = classRoomService.joinClass(
                request.getStudentName(),
                request.getClassCode(),
                student
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{classCode}")
    public ResponseEntity<Map<String, Object>> getClassInfo(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        authService.getUserFromToken(token);

        ClassRoom classRoom = classRoomService.getClassByCode(classCode);

        Map<String, Object> response = new HashMap<>();
        response.put("classId", classRoom.getId());
        response.put("className", classRoom.getName());
        response.put("classCode", classRoom.getClassCode());
        response.put("teacherName", classRoom.getTeacher().getName());
        response.put("isActive", classRoom.getIsActive());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{classCode}/students")
    public ResponseEntity<List<Map<String, Object>>> getStudents(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);

        List<Student> students = classRoomService.getStudentsInClass(classCode);

        List<Map<String, Object>> response = students.stream().map(student -> {
            Map<String, Object> studentInfo = new HashMap<>();
            studentInfo.put("studentId", student.getId());
            studentInfo.put("userId", student.getUser().getId());
            studentInfo.put("studentName", student.getStudentName());
            studentInfo.put("joinedAt", student.getJoinedAt());
            return studentInfo;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-classes")
    public ResponseEntity<Map<String, Object>> getMyClasses(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);

        List<ClassRoom> teacherClasses = classRoomService.getTeacherClasses(user);

        List<Student> studentEnrollments = classRoomService.getStudentEnrollments(user);

        Map<String, Object> response = new HashMap<>();
        response.put("teacherClasses", teacherClasses.stream().map(c -> {
            Map<String, Object> classInfo = new HashMap<>();
            classInfo.put("classId", c.getId());
            classInfo.put("className", c.getName());
            classInfo.put("classCode", c.getClassCode());
            classInfo.put("studentCount", c.getStudents().size());
            List<Assignment> assignments = c.getAssignments();
            List<Assignment> assignmentActives = assignments.stream().filter(Assignment::getIsActive).toList();
            classInfo.put("assignmentCount", assignmentActives.size());
            return classInfo;
        }).collect(Collectors.toList()));

        response.put("studentClasses", studentEnrollments.stream().map(s -> {
            Map<String, Object> classInfo = new HashMap<>();
            classInfo.put("classId", s.getClassRoom().getId());
            classInfo.put("className", s.getClassRoom().getName());
            classInfo.put("classCode", s.getClassRoom().getClassCode());
            List<Assignment> assignments = s.getClassRoom().getAssignments();
            List<Assignment> assignmentActives = assignments.stream().filter(Assignment::getIsActive).toList();
            classInfo.put("assignmentCount", assignmentActives.size());
            return classInfo;
        }).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{classCode}/leave")
    public ResponseEntity<Map<String, String>> leaveClass(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);

        classRoomService.leaveClass(classCode, user);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Left class successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{classCode}/student/{studentId}")
    public ResponseEntity<Map<String, String>> removeStudent(
            @PathVariable String classCode,
            @PathVariable String studentId,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User teacher = authService.getUserFromToken(token);

        classRoomService.removeStudentFromClass(classCode, Long.parseLong(studentId), teacher);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Student removed successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{classCode}")
    public ResponseEntity<Map<String, String>> deleteClass(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User teacher = authService.getUserFromToken(token);

        classRoomService.deleteClass(classCode, teacher);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Class deleted successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chat/classes-with-messages")
    public ResponseEntity<List<Map<String, Object>>> getClassesWithLastMessages(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User currentUser = authService.getUserFromToken(token);

        List<ClassRoom> userClasses = new java.util.ArrayList<>();

        if ("TEACHER".equals(currentUser.getRole().toString())) {
            userClasses = classRoomService.getTeacherClasses(currentUser);
        } else {
            List<Student> enrollments = classRoomService.getStudentEnrollments(currentUser);
            for (Student enrollment : enrollments) {
                userClasses.add(enrollment.getClassRoom());
            }
        }

        List<Map<String, Object>> classesWithMessages = new java.util.ArrayList<>();

        for (ClassRoom classroom : userClasses) {
            Map<String, Object> classInfo = new HashMap<>();
            classInfo.put("id", classroom.getId());
            classInfo.put("className", classroom.getName());
            classInfo.put("classCode", classroom.getClassCode());
            classInfo.put("teacherName", classroom.getTeacher().getName());
            classInfo.put("studentCount", classroom.getStudents().size());

            // Get last message for this class
            List<com.example.auto_git_be.entity.Message> messages =
                    messageRepository.findByClassRoomAndTypeOrderByCreatedAtAsc(
                            classroom, com.example.auto_git_be.model.MessageType.CLASS_GROUP);

            if (!messages.isEmpty()) {
                com.example.auto_git_be.entity.Message lastMessage = messages.get(messages.size() - 1);
                classInfo.put("lastMessage", lastMessage.getContent());
                classInfo.put("lastMessageTime", lastMessage.getCreatedAt().toString());
                classInfo.put("lastMessageSender", lastMessage.getSender().getName());
            } else {
                classInfo.put("lastMessage", "Chưa có tin nhắn");
                classInfo.put("lastMessageTime", null);
                classInfo.put("lastMessageSender", null);
            }

            classesWithMessages.add(classInfo);
        }

        return ResponseEntity.ok(classesWithMessages);
    }

    @GetMapping("/chat/search-members")
    public ResponseEntity<List<Map<String, Object>>> searchMembersInClasses(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String query) {
        String token = authHeader.substring(7);
        User currentUser = authService.getUserFromToken(token);

        List<ClassRoom> userClasses = new java.util.ArrayList<>();

        if ("TEACHER".equals(currentUser.getRole().toString())) {
            userClasses = classRoomService.getTeacherClasses(currentUser);
        } else {
            List<Student> enrollments = classRoomService.getStudentEnrollments(currentUser);
            for (Student enrollment : enrollments) {
                userClasses.add(enrollment.getClassRoom());
            }
        }

        List<Map<String, Object>> members = new java.util.ArrayList<>();
        Set<Long> addedUserIds = new HashSet<>();

        for (ClassRoom classroom : userClasses) {
            // Add teacher
            User teacher = classroom.getTeacher();
            if (!Objects.equals(teacher.getId(), currentUser.getId()) &&
                    !addedUserIds.contains(teacher.getId())) {
                if (query == null || query.isEmpty() ||
                        teacher.getName().toLowerCase().contains(query.toLowerCase()) ||
                        teacher.getEmail().toLowerCase().contains(query.toLowerCase())) {
                    Map<String, Object> teacherInfo = new HashMap<>();
                    teacherInfo.put("userId", teacher.getId());
                    teacherInfo.put("userName", teacher.getName());
                    teacherInfo.put("userEmail", teacher.getEmail());
                    teacherInfo.put("role", "TEACHER");
                    teacherInfo.put("classId", classroom.getId());
                    teacherInfo.put("className", classroom.getName());
                    members.add(teacherInfo);
                    addedUserIds.add(teacher.getId());
                }
            }

            // Add students
            List<Student> students = classRoomService.getStudentsInClass(classroom.getClassCode());
            for (Student student : students) {
                User studentUser = student.getUser();
                if (!Objects.equals(studentUser.getId(), currentUser.getId()) &&
                        !addedUserIds.contains(studentUser.getId())) {
                    if (query == null || query.isEmpty() ||
                            studentUser.getName().toLowerCase().contains(query.toLowerCase()) ||
                            studentUser.getEmail().toLowerCase().contains(query.toLowerCase())) {
                        Map<String, Object> studentInfo = new HashMap<>();
                        studentInfo.put("userId", studentUser.getId());
                        studentInfo.put("userName", studentUser.getName());
                        studentInfo.put("userEmail", studentUser.getEmail());
                        studentInfo.put("role", "STUDENT");
                        studentInfo.put("classId", classroom.getId());
                        studentInfo.put("className", classroom.getName());
                        members.add(studentInfo);
                        addedUserIds.add(studentUser.getId());
                    }
                }
            }
        }

        return ResponseEntity.ok(members);
    }
}


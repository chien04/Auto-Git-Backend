package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.CreateClassRequest;
import com.example.auto_git_be.dto.CreateClassResponse;
import com.example.auto_git_be.dto.JoinClassRequest;
import com.example.auto_git_be.dto.JoinClassResponse;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.MessageRepository;
import com.example.auto_git_be.service.AuthService;
import com.example.auto_git_be.service.ClassRoomService;
import com.example.auto_git_be.service.StudentService;
import com.example.auto_git_be.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);

            CreateClassResponse response = classRoomService.createClass(
                request.getClassName(),
                teacher
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/join")
    public ResponseEntity<JoinClassResponse> joinClass(
            @RequestBody JoinClassRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User student = authService.getUserFromToken(token);

            JoinClassResponse response = classRoomService.joinClass(
                    request.getStudentName(),
                    request.getClassCode(),
                    student
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get class information
     */
    @GetMapping("/{classCode}")
    public ResponseEntity<Map<String, Object>> getClassInfo(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            authService.getUserFromToken(token); // Verify authentication

            ClassRoom classRoom = classRoomService.getClassByCode(classCode);
            
            Map<String, Object> response = new HashMap<>();
            response.put("classId", classRoom.getId());
            response.put("className", classRoom.getName());
            response.put("classCode", classRoom.getClassCode());
            // With new architecture: classes don't have repositories
            // Use assignments to get repo info
            response.put("teacherName", classRoom.getTeacher().getName());
            response.put("isActive", classRoom.getIsActive());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all students in a class (for teachers)
     */
    @GetMapping("/{classCode}/students")
    public ResponseEntity<List<Map<String, Object>>> getStudents(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);

            List<Student> students = classRoomService.getStudentsInClass(classCode);
            
            List<Map<String, Object>> response = students.stream().map(student -> {
                Map<String, Object> studentInfo = new HashMap<>();
                studentInfo.put("studentId", student.getId());
                studentInfo.put("userId", student.getUser().getId());
                studentInfo.put("studentName", student.getStudentName());
                // With new architecture: branch, commit info are in StudentAssignment
                // Frontend should query assignments separately
                studentInfo.put("joinedAt", student.getJoinedAt());
                return studentInfo;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/my-classes")
    public ResponseEntity<Map<String, Object>> getMyClasses(
            @RequestHeader("Authorization") String authHeader) {
        try {
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
                classInfo.put("assignmentCount", c.getAssignments().size());
                return classInfo;
            }).collect(Collectors.toList()));
            
            response.put("studentClasses", studentEnrollments.stream().map(s -> {
                Map<String, Object> classInfo = new HashMap<>();
                classInfo.put("classId", s.getClassRoom().getId());
                classInfo.put("className", s.getClassRoom().getName());
                classInfo.put("classCode", s.getClassRoom().getClassCode());
                classInfo.put("assignmentCount", s.getAssignments().size());
                return classInfo;
            }).collect(Collectors.toList()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Leave a class (Student)
     */
    @DeleteMapping("/{classCode}/leave")
    public ResponseEntity<Map<String, String>> leaveClass(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);
            
            classRoomService.leaveClass(classCode, user);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Left class successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Remove student from class (Teacher only)
     */
    @DeleteMapping("/{classCode}/student/{studentId}")
    public ResponseEntity<Map<String, String>> removeStudent(
            @PathVariable String classCode,
            @PathVariable String studentId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);
            
            classRoomService.removeStudentFromClass(classCode, Long.parseLong(studentId), teacher);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Student removed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Delete a class (Teacher only)
     */
    @DeleteMapping("/{classCode}")
    public ResponseEntity<Map<String, String>> deleteClass(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);
            
            classRoomService.deleteClass(classCode, teacher);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Class deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get commits for a specific branch
     * @deprecated Use GET /api/assignment/{assignmentCode}/commits?branch= instead
     */
    @Deprecated
    @GetMapping("/{classCode}/commits")
    public ResponseEntity<List<Map<String, Object>>> getCommits(
            @PathVariable String classCode,
            @RequestParam String branch,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "This endpoint is deprecated. With the new architecture, " +
                    "commits belong to assignments, not classes. " +
                    "Use GET /api/assignment/{assignmentCode}/commits?branch= instead.");
            error.put("deprecated", true);
            return ResponseEntity.status(410).body(List.of(error)); // 410 Gone
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get commit URL for viewing code
     * @deprecated Use GET /api/assignment/{assignmentCode}/commit/{commitSha} instead
     */
    @Deprecated
    @GetMapping("/{classCode}/commit/{commitSha}")
    public ResponseEntity<Map<String, String>> getCommitUrl(
            @PathVariable String classCode,
            @PathVariable String commitSha,
            @RequestParam String branch,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", "This endpoint is deprecated. " +
                    "Use GET /api/assignment/{assignmentCode}/commit/{commitSha} instead.");
            error.put("deprecated", "true");
            return ResponseEntity.status(410).body(error); // 410 Gone
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Setup workspace for class (Teacher only)
     * @deprecated Use assignment-based workspace instead
     */
    @Deprecated
    @PostMapping("/{classCode}/workspace/setup")
    public ResponseEntity<Map<String, String>> setupWorkspace(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", "This endpoint is deprecated. With the new architecture, " +
                    "workspaces should be created per assignment, not per class.");
            error.put("deprecated", "true");
            return ResponseEntity.status(410).body(error); // 410 Gone
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Check if workspace exists for class
     * @deprecated Use assignment-based workspace instead
     */
    @Deprecated
    @GetMapping("/{classCode}/workspace/exists")
    public ResponseEntity<Map<String, Boolean>> checkWorkspaceExists(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, Boolean> response = new HashMap<>();
            response.put("exists", false);
            response.put("deprecated", true);
            return ResponseEntity.status(410).body(response); // 410 Gone
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update workspace (add new students)
     * @deprecated Use assignment-based workspace instead
     */
    @Deprecated
    @PostMapping("/{classCode}/workspace/update")
    public ResponseEntity<Map<String, String>> updateWorkspace(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", "This endpoint is deprecated. Use assignment-based workspace instead.");
            error.put("deprecated", "true");
            return ResponseEntity.status(410).body(error); // 410 Gone
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Sync workspace - fetch and pull latest code (Teacher)
     * @deprecated Use assignment-based workspace instead
     */
    @Deprecated
    @PostMapping("/{classCode}/workspace/sync")
    public ResponseEntity<Map<String, String>> syncWorkspace(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", "This endpoint is deprecated. Use assignment-based workspace instead.");
            error.put("deprecated", "true");
            return ResponseEntity.status(410).body(error); // 410 Gone
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Update commit count for a student (called after push)
     * @deprecated This endpoint is deprecated with the new architecture.
     * Use POST /api/assignment/{assignmentCode}/update-commits instead.
     */
    @Deprecated
    @PostMapping("/{classCode}/student/update-commits")
    public ResponseEntity<Map<String, Object>> updateStudentCommits(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "This endpoint is deprecated. With the new architecture, " +
                    "students join assignments (not classes). " +
                    "Use POST /api/assignment/{assignmentCode}/update-commits instead.");
            error.put("deprecated", true);
            return ResponseEntity.status(410).body(error); // 410 Gone
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get local path for class (Teacher)
     * @deprecated Use assignment-based local paths instead
     */
    @Deprecated
    @GetMapping("/{classCode}/localPath")
    public ResponseEntity<Map<String, String>> getLocalPath(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", "This endpoint is deprecated. With the new architecture, " +
                    "localPath is stored per assignment, not per class. " +
                    "Use assignment-based APIs instead.");
            error.put("deprecated", "true");
            return ResponseEntity.status(410).body(error); // 410 Gone
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Check if deadline has passed (Student)
     * @deprecated Deadlines are now per assignment, not per class
     */
    @Deprecated
    @GetMapping("/{classCode}/deadline/check")
    public ResponseEntity<Map<String, Object>> checkDeadline(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "This endpoint is deprecated. With the new architecture, " +
                    "deadlines are per assignment, not per class. " +
                    "Use GET /api/assignment/{assignmentCode}/deadline/check instead.");
            error.put("deprecated", true);
            return ResponseEntity.status(410).body(error); // 410 Gone
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all members from all classes of current user (for chat)
     */
    @GetMapping("/all-members")
    public ResponseEntity<List<Map<String, Object>>> getAllClassMembers(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User currentUser = authService.getUserFromToken(token);
            
            List<ClassRoom> userClasses = new java.util.ArrayList<>();
            
            if ("TEACHER".equals(currentUser.getRole())) {
                // For teacher: get all classes they teach
                userClasses = classRoomService.getTeacherClasses(currentUser);
            } else {
                // For student: get classrooms from student enrollments
                List<Student> enrollments = classRoomService.getStudentEnrollments(currentUser);
                for (Student enrollment : enrollments) {
                    userClasses.add(enrollment.getClassRoom());
                }
            }
            
            List<Map<String, Object>> allMembers = new java.util.ArrayList<>();
            
            for (ClassRoom classroom : userClasses) {
                // Add teacher
                User teacher = classroom.getTeacher();
                if (!Objects.equals(teacher.getId(), currentUser.getId())) {
                    Map<String, Object> teacherInfo = new HashMap<>();
                    teacherInfo.put("userId", teacher.getId());
                    teacherInfo.put("userName", teacher.getName());
                    teacherInfo.put("userEmail", teacher.getEmail());
                    teacherInfo.put("role", "TEACHER");
                    teacherInfo.put("classId", classroom.getId());
                    teacherInfo.put("className", classroom.getName());
                    allMembers.add(teacherInfo);
                }
                
                // Add students
                List<Student> students = classRoomService.getStudentsInClass(classroom.getClassCode());
                for (Student student : students) {
                    User studentUser = student.getUser();
                    if (!Objects.equals(studentUser.getId(), currentUser.getId())) {
                        Map<String, Object> studentInfo = new HashMap<>();
                        studentInfo.put("userId", studentUser.getId());
                        studentInfo.put("userName", studentUser.getName());
                        studentInfo.put("userEmail", studentUser.getEmail());
                        studentInfo.put("role", "STUDENT");
                        studentInfo.put("classId", classroom.getId());
                        studentInfo.put("className", classroom.getName());
                        allMembers.add(studentInfo);
                    }
                }
            }
            
            return ResponseEntity.ok(allMembers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get classes with last message for chat view
     */
    @GetMapping("/chat/classes-with-messages")
    public ResponseEntity<List<Map<String, Object>>> getClassesWithLastMessages(
            @RequestHeader("Authorization") String authHeader) {
        try {
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
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get members in classes for searching
     */
    @GetMapping("/chat/search-members")
    public ResponseEntity<List<Map<String, Object>>> searchMembersInClasses(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String query) {
        try {
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
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}

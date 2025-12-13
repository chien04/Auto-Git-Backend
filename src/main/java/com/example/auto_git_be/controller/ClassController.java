package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.CreateClassRequest;
import com.example.auto_git_be.dto.CreateClassResponse;
import com.example.auto_git_be.dto.JoinClassRequest;
import com.example.auto_git_be.dto.JoinClassResponse;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.service.AuthService;
import com.example.auto_git_be.service.ClassRoomService;
import com.example.auto_git_be.service.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/class")
@CrossOrigin(origins = "*")
public class ClassController {

    @Autowired
    private ClassRoomService classRoomService;

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * Create a new class (Teacher only)
     */
    @PostMapping("/create")
    public ResponseEntity<CreateClassResponse> createClass(
            @RequestBody CreateClassRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);

            CreateClassResponse response = classRoomService.createClass(
                request.getClassName(), 
                request.getLocalPath(), 
                teacher
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Join a class (Student)
     */
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
                    request.getLocalPath(),
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
            response.put("repoUrl", classRoom.getRepoUrl());
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
            
            System.out.println("=== getStudents API called ===");
            System.out.println("Current user ID: " + user.getId());
            System.out.println("Current user email: " + user.getEmail());

            List<Student> students = classRoomService.getStudentsInClass(classCode);
            
            List<Map<String, Object>> response = students.stream().map(student -> {
                Map<String, Object> studentInfo = new HashMap<>();
                studentInfo.put("studentId", student.getId());
                studentInfo.put("userId", student.getUser().getId());
                studentInfo.put("studentName", student.getStudentName());
                studentInfo.put("branchName", student.getBranchName());
                studentInfo.put("commitCount", student.getCommitCount());
                studentInfo.put("lastCommitAt", student.getLastCommitAt());
                studentInfo.put("joinedAt", student.getJoinedAt());
                
                System.out.println("Student: " + student.getStudentName() + 
                                 ", userId: " + student.getUser().getId() + 
                                 ", match: " + student.getUser().getId().equals(user.getId()));
                return studentInfo;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error getting students: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all classes for current user
     */
    @GetMapping("/my-classes")
    public ResponseEntity<Map<String, Object>> getMyClasses(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);

            // Get classes as teacher
            List<ClassRoom> teacherClasses = classRoomService.getTeacherClasses(user);
            
            // Get classes as student
            List<Student> studentEnrollments = classRoomService.getStudentEnrollments(user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("teacherClasses", teacherClasses.stream().map(c -> {
                Map<String, Object> classInfo = new HashMap<>();
                classInfo.put("classId", c.getId());
                classInfo.put("className", c.getName());
                classInfo.put("classCode", c.getClassCode());
                classInfo.put("repoUrl", c.getRepoUrl());
                classInfo.put("studentCount", c.getStudents().size());
                return classInfo;
            }).collect(Collectors.toList()));
            
            response.put("studentClasses", studentEnrollments.stream().map(s -> {
                Map<String, Object> classInfo = new HashMap<>();
                classInfo.put("classId", s.getClassRoom().getId());
                classInfo.put("className", s.getClassRoom().getName());
                classInfo.put("classCode", s.getClassRoom().getClassCode());
                classInfo.put("repoUrl", s.getClassRoom().getRepoUrl());
                classInfo.put("branchName", s.getBranchName());
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
     */
    @GetMapping("/{classCode}/commits")
    public ResponseEntity<List<Map<String, Object>>> getCommits(
            @PathVariable String classCode,
            @RequestParam String branch,
            @RequestHeader("Authorization") String authHeader) {
        try {
            System.out.println("ClassController.getCommits called with classCode: " + classCode + ", branch: " + branch);
            
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);
            System.out.println("Authenticated user: " + user.getEmail());

            List<Map<String, Object>> commits = classRoomService.getCommits(classCode, branch);
            System.out.println("Returning " + commits.size() + " commits to frontend");
            return ResponseEntity.ok(commits);
        } catch (Exception e) {
            System.err.println("Error in getCommits controller: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get commit URL for viewing code
     * Format: /classCode/commit/commitSha?branch=branchName
     */
    @GetMapping("/{classCode}/commit/{commitSha}")
    public ResponseEntity<Map<String, String>> getCommitUrl(
            @PathVariable String classCode,
            @PathVariable String commitSha,
            @RequestParam String branch,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            authService.getUserFromToken(token); // Verify authentication

            String url = classRoomService.getCommitUrl(classCode, branch, commitSha);
            
            Map<String, String> response = new HashMap<>();
            response.put("url", url);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Setup workspace for class (Teacher only)
     */
    @PostMapping("/{classCode}/workspace/setup")
    public ResponseEntity<Map<String, String>> setupWorkspace(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);

            // Verify teacher owns this class
            ClassRoom classroom = classRoomService.getClassByCode(classCode);
            if (!classroom.getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(403).build();
            }

            // Get all students
            List<Student> students = classRoomService.getStudentsByClassCode(classCode);

            // Setup workspace
            String workspaceFilePath = workspaceService.setupClassroomWorkspace(classroom, students);

            Map<String, String> response = new HashMap<>();
            response.put("workspaceFilePath", workspaceFilePath);
            response.put("message", "Workspace đã được tạo thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Check if workspace exists for class
     */
    @GetMapping("/{classCode}/workspace/exists")
    public ResponseEntity<Map<String, Boolean>> checkWorkspaceExists(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);

            ClassRoom classroom = classRoomService.getClassByCode(classCode);
            if (!classroom.getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(403).build();
            }

            boolean exists = workspaceService.workspaceExists(classroom);

            Map<String, Boolean> response = new HashMap<>();
            response.put("exists", exists);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update workspace (add new students)
     */
    @PostMapping("/{classCode}/workspace/update")
    public ResponseEntity<Map<String, String>> updateWorkspace(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);

            ClassRoom classroom = classRoomService.getClassByCode(classCode);
            if (!classroom.getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(403).build();
            }

            List<Student> students = classRoomService.getStudentsByClassCode(classCode);
            workspaceService.updateWorkspace(classroom, students);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Workspace đã được cập nhật");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Sync workspace - fetch and pull latest code (Teacher)
     */
    @PostMapping("/{classCode}/workspace/sync")
    public ResponseEntity<Map<String, String>> syncWorkspace(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);

            ClassRoom classroom = classRoomService.getClassByCode(classCode);
            if (!classroom.getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(403).build();
            }

            List<Student> students = classRoomService.getStudentsByClassCode(classCode);
            workspaceService.syncWorkspace(classroom, students);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Đã đồng bộ code mới nhất từ GitHub");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get local path for class (Teacher)
     */
    @GetMapping("/{classCode}/localPath")
    public ResponseEntity<Map<String, String>> getLocalPath(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);

            ClassRoom classroom = classRoomService.getClassByCode(classCode);
            
            Map<String, String> response = new HashMap<>();
            
            // Check if teacher
            if (classroom.getTeacher().getId().equals(user.getId())) {
                response.put("localPath", classroom.getLocalPath());
                response.put("role", "TEACHER");
            } else {
                // Check if student
                Student student = classRoomService.getStudentByUserAndClass(user, classroom);
                if (student != null) {
                    response.put("localPath", student.getLocalPath());
                    response.put("role", "STUDENT");
                } else {
                    return ResponseEntity.status(403).build();
                }
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}

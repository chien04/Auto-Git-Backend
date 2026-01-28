package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.*;
import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.entity.TeacherAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.service.AssignmentService;
import com.example.auto_git_be.service.AssignmentWorkspaceService;
import com.example.auto_git_be.service.AuthService;
import com.example.auto_git_be.service.ClassRoomService;
import com.example.auto_git_be.service.GitHubService;
import com.example.auto_git_be.service.StudentAssignmentService;
import com.example.auto_git_be.service.StudentService;
import com.example.auto_git_be.service.TeacherAssignmentService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assignment")
@CrossOrigin(origins = "*")
public class AssignmentController {
    
    @Autowired
    private AssignmentService assignmentService;
    
    @Autowired
    private ClassRoomService classRoomService;
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private GitHubService gitHubService;
    
    @Autowired
    private StudentService studentService;
    
    @Autowired
    private StudentAssignmentService studentAssignmentService;
    
    @Autowired
    private AssignmentWorkspaceService assignmentWorkspaceService;
    
    @Autowired
    private TeacherAssignmentService teacherAssignmentService;
    
    /**
     * Create a new assignment (Teacher only)
     */
    @PostMapping("/create")
    public ResponseEntity<CreateAssignmentResponse> createAssignment(
            @RequestBody CreateAssignmentRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);
            
            // Get classroom
            ClassRoom classRoom = classRoomService.getClassByCode(request.getClassCode());
            
            // Verify teacher ownership
            if (!classRoom.getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(403).build();
            }
            
            // Create assignment
            Assignment assignment = assignmentService.createAssignment(
                    classRoom,
                    request.getTitle(),
                    request.getDescription(),
                    request.getDeadline()
            );
            
            String githubToken = gitHubService.getToken();
            
            CreateAssignmentResponse response = CreateAssignmentResponse.builder()
                    .assignmentId(assignment.getId().toString())
                    .assignmentCode(assignment.getAssignmentCode())
                    .title(assignment.getTitle())
                    .repoUrl(assignment.getRepoUrl())
                    .token(githubToken)
                    .deadline(assignment.getDeadline())
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Join an assignment (Student)
     */
    @PostMapping("/join")
    public ResponseEntity<JoinAssignmentResponse> joinAssignment(
            @RequestBody JoinAssignmentRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User student = authService.getUserFromToken(token);
            
            JoinAssignmentResponse response = assignmentService.joinAssignment(
                    request.getAssignmentCode(),
                    request.getLocalPath(),
                    student
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get all assignments in a classroom
     */
    @GetMapping("/class/{classCode}")
    @Transactional
    public ResponseEntity<List<Map<String, Object>>> getAssignmentsByClass(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);
            
            // Bước 1: Từ classCode → tìm ClassRoom (lấy id lớp học)
            ClassRoom classRoom = classRoomService.getClassByCode(classCode);
            
            // Bước 2: Từ ClassRoom → tìm tất cả Assignment
            List<Assignment> assignments = assignmentService.getAssignmentsByClassroom(classRoom);
            
            // Tìm tất cả Student có user_id = user hiện tại (có thể có nhiều Student record với user_id giống nhau)
            List<Student> currentUserStudents = studentService.findByUser(user);
            
            // Lấy danh sách student IDs của user hiện tại
            List<Long> currentStudentIds = currentUserStudents.stream()
                    .map(Student::getId)
                    .collect(Collectors.toList());
            
            // Bước 3: Với mỗi Assignment, kiểm tra xem có StudentAssignment nào của user hiện tại không
            List<Map<String, Object>> result = assignments.stream().map(assignment -> {
                Map<String, Object> map = new HashMap<>();
                map.put("assignmentId", assignment.getId());
                map.put("assignmentCode", assignment.getAssignmentCode());
                map.put("title", assignment.getTitle());
                map.put("description", assignment.getDescription());
                map.put("repoUrl", assignment.getRepoUrl());
                map.put("deadline", assignment.getDeadline());
                map.put("createdAt", assignment.getCreatedAt());
                
                // Bước 4: Lấy tất cả StudentAssignment của assignment này
                List<StudentAssignment> studentAssignments = studentAssignmentService.findByAssignment(assignment);
                map.put("studentCount", studentAssignments.size());
                
                // Bước 5: Kiểm tra xem có StudentAssignment nào có student_id thuộc currentStudentIds không
                StudentAssignment myAssignment = null;
                for (StudentAssignment sa : studentAssignments) {
                    Long studentId = sa.getStudent().getId();
                    
                    if (currentStudentIds.contains(studentId)) {
                        myAssignment = sa;
                        break;
                    }
                }
                
                // Bước 6: Nếu tìm thấy → joined = true, nếu không → joined = false
                if (myAssignment != null) {
                    map.put("joined", true);
                    map.put("commitCount", myAssignment.getCommitCount());
                    map.put("lastCommitAt", myAssignment.getLastCommitAt());
                    map.put("localPath", myAssignment.getLocalPath());
                } else {
                    map.put("joined", false);
                }
                
                return map;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get students in an assignment
     */
    @GetMapping("/{assignmentCode}/students")
    public ResponseEntity<List<Map<String, Object>>> getStudentsInAssignment(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);
            
            Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
            
            // Verify teacher ownership
            if (!assignment.getClassRoom().getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(403).build();
            }
            
            List<com.example.auto_git_be.entity.StudentAssignment> studentAssignments = 
                assignmentService.getStudentsInAssignment(assignment);
            
            List<Map<String, Object>> result = studentAssignments.stream().map(sa -> {
                Map<String, Object> map = new HashMap<>();
                map.put("studentId", sa.getStudent().getId());
                map.put("studentName", sa.getStudent().getStudentName());
                map.put("branchName", sa.getBranchName());
                map.put("commitCount", sa.getCommitCount());
                map.put("lastCommitAt", sa.getLastCommitAt());
                map.put("joinedAt", sa.getJoinedAt());
                return map;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Update commit count for student assignment (called after push)
     */
    @PostMapping("/{assignmentCode}/update-commits")
    public ResponseEntity<Void> updateCommitCount(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);
            
            assignmentService.updateCommitCountForUser(assignmentCode, user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Update score from GitHub Actions (webhook endpoint)
     * This endpoint can be called without authentication from GitHub Actions
     */
    @PostMapping("/update-score")
    public ResponseEntity<Map<String, String>> updateScore(@RequestBody ScoreUpdateRequest request) {
        try {
            assignmentService.updateStudentScore(
                request.getRepoFullName(), 
                request.getBranchName(), 
                request.getScore()
            );
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Score updated successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Delete an assignment
     */
    @DeleteMapping("/{assignmentCode}")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);
            
            assignmentService.deleteAssignment(assignmentCode, teacher);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Setup workspace for assignment (Teacher only)
     * Creates worktrees for all students' branches
     */
    @PostMapping("/{assignmentCode}/workspace/setup")
    public ResponseEntity<Map<String, String>> setupAssignmentWorkspace(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);
            
            Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
            if (assignment == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Assignment not found"));
            }
            
            // Verify teacher owns this assignment's class
            if (!assignment.getClassRoom().getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
            }
            
            // Get teacher's local path from database
            Optional<TeacherAssignment> teacherAssignment = teacherAssignmentService.getTeacherAssignment(teacher, assignment);
            String localPath = teacherAssignment.map(TeacherAssignment::getLocalPath).orElse(null);
            
            if (localPath == null || localPath.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Local path not found. Please create assignment first."));
            }
            
            // Get all student assignments
            List<StudentAssignment> studentAssignments = studentAssignmentService.findByAssignment(assignment);
            
            // Setup workspace with actual localPath
            String workspacePath = assignmentWorkspaceService.setupAssignmentWorkspace(assignment, studentAssignments, token, localPath);
            
            return ResponseEntity.ok(Map.of(
                "message", "Workspace setup completed",
                "workspacePath", workspacePath
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Sync workspace for assignment (Teacher only)
     * Pulls latest code from all student branches
     */
    @PostMapping("/{assignmentCode}/workspace/sync")
    public ResponseEntity<Map<String, String>> syncAssignmentWorkspace(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);
            
            Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
            if (assignment == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Assignment not found"));
            }
            
            // Verify teacher owns this assignment's class
            if (!assignment.getClassRoom().getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
            }
            
            // Get teacher's local path from database
            Optional<TeacherAssignment> teacherAssignment = teacherAssignmentService.getTeacherAssignment(teacher, assignment);
            String localPath = teacherAssignment.map(TeacherAssignment::getLocalPath).orElse(null);
            
            if (localPath == null || localPath.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Local path not found. Please setup workspace first."));
            }
            
            // Get all student assignments
            List<StudentAssignment> studentAssignments = studentAssignmentService.findByAssignment(assignment);
            
            // Sync workspace with actual localPath
            assignmentWorkspaceService.syncAssignmentWorkspace(assignment, studentAssignments, localPath);
            
            return ResponseEntity.ok(Map.of(
                "message", "Workspace synced successfully. All student branches are up to date."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get workspace path for assignment (Teacher only)
     */
    @GetMapping("/{assignmentCode}/workspace/path")
    public ResponseEntity<Map<String, Object>> getAssignmentWorkspacePath(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);
            
            Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
            if (assignment == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Assignment not found"));
            }
            
            // Verify teacher owns this assignment's class
            if (!assignment.getClassRoom().getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
            }
            
            String classCode = assignment.getClassRoom().getClassCode();
            String workspacePath = assignmentWorkspaceService.getWorkspacePath(classCode, assignmentCode);
            boolean exists = assignmentWorkspaceService.workspaceExists(classCode, assignmentCode);
            
            return ResponseEntity.ok(Map.of(
                "workspacePath", workspacePath,
                "exists", exists
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Save teacher's local path for assignment
     */
    @PostMapping("/{assignmentCode}/teacher/localPath")
    public ResponseEntity<Map<String, Object>> saveTeacherLocalPath(
            @PathVariable String assignmentCode,
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);
            
            Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
            if (assignment == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Assignment not found"));
            }
            
            String localPath = request.get("localPath");
            if (localPath == null || localPath.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Local path is required"));
            }
            
            // Check if teacher has access
            if (!teacherAssignmentService.hasAccess(teacher, assignment)) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
            }
            
            // Determine role
            String role = assignment.getClassRoom().getTeacher().getId().equals(teacher.getId()) ? "MAIN" : "SUB";
            
            TeacherAssignment ta = teacherAssignmentService.saveTeacherAssignment(teacher, assignment, localPath, role);
            
            return ResponseEntity.ok(Map.of(
                "message", "Local path saved successfully",
                "localPath", ta.getLocalPath(),
                "role", ta.getRole()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get teacher's local path for assignment
     */
    @GetMapping("/{assignmentCode}/teacher/localPath")
    public ResponseEntity<Map<String, Object>> getTeacherLocalPath(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);
            
            Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
            if (assignment == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Assignment not found"));
            }
            
            // Check if teacher has access
            if (!teacherAssignmentService.hasAccess(teacher, assignment)) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
            }
            
            String localPath = teacherAssignmentService.getLocalPath(teacher, assignment);
            
            if (localPath == null) {
                return ResponseEntity.ok(Map.of(
                    "localPath", "",
                    "exists", false
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "localPath", localPath,
                "exists", true
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get assignment submissions (list of students with their submission info)
     * Accessible by both teachers and students
     */
    @GetMapping("/{assignmentCode}/submissions")
    public ResponseEntity<?> getAssignmentSubmissions(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);
            
            Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
            if (assignment == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Assignment not found"));
            }
            
            // Check if user has access (either teacher or student)
            boolean hasAccess = false;
            if (user.getRole() == User.UserRole.TEACHER) {
                hasAccess = teacherAssignmentService.hasAccess(user, assignment);
            } else if (user.getRole() == User.UserRole.STUDENT) {
                // Check if student is enrolled in this assignment's classroom
                Optional<Student> studentOpt = studentService.findByUserAndClassRoom(user, assignment.getClassRoom());
                if (studentOpt.isPresent()) {
                    Student student = studentOpt.get();
                    Optional<StudentAssignment> studentAssignment = studentAssignmentService.findByStudentAndAssignment(student, assignment);
                    hasAccess = studentAssignment.isPresent();
                }
            }
            
            if (!hasAccess) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
            }
            
            // Get all student assignments
            List<StudentAssignment> studentAssignments = studentAssignmentService.findByAssignment(assignment);
            
            // Convert to DTO
            List<StudentSubmissionDTO> submissions = studentAssignments.stream()
                .map(sa -> {
                    Student student = sa.getStudent();
                    User studentUser = student.getUser();
                    return StudentSubmissionDTO.builder()
                        .studentId(student.getId())
                        .studentName(student.getStudentName())
                        .studentCode(studentUser.getGoogleId())  // Using GoogleId as student code
                        .email(studentUser.getEmail())
                        .commitCount(sa.getCommitCount() != null ? sa.getCommitCount() : 0)
                        .lastCommitAt(sa.getLastCommitAt())
                        .score(sa.getScore())
                        .build();
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(submissions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

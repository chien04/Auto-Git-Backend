package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.assignment.CreateAssignmentRequest;
import com.example.auto_git_be.dto.assignment.CreateAssignmentResponse;
import com.example.auto_git_be.dto.assignment.JoinAssignmentRequest;
import com.example.auto_git_be.dto.assignment.JoinAssignmentResponse;
import com.example.auto_git_be.dto.assignment.ScoreUpdateRequest;
import com.example.auto_git_be.dto.assignment.StudentSubmissionDTO;
import com.example.auto_git_be.dto.comment.CommentResponse;
import com.example.auto_git_be.dto.comment.CreateCommentRequest;
import com.example.auto_git_be.entity.*;
import com.example.auto_git_be.service.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/assignment")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final ClassRoomService classRoomService;
    private final AuthService authService;
    private final StudentService studentService;
    private final StudentAssignmentService studentAssignmentService;
    private final AssignmentWorkspaceService assignmentWorkspaceService;
    private final TeacherAssignmentService teacherAssignmentService;
    private final GitHubService gitHubService;
    private final NotificationService notificationService;
    private final ExcelService excelService;
    private final CommentService commentService;

    @PostMapping("/create")
    public ResponseEntity<CreateAssignmentResponse> createAssignment(
            @RequestBody CreateAssignmentRequest request,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User teacher = authService.getUserFromToken(token);

        ClassRoom classRoom = classRoomService.getClassByCode(request.getClassCode());

        if (!classRoom.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).build();
        }

        Assignment assignment = assignmentService.createAssignment(
                classRoom,
                request.getTitle(),
                request.getDescription(),
                request.getDeadline(),
                request.getTasks()
        );

        String githubToken = gitHubService.getToken();

        CreateAssignmentResponse response = CreateAssignmentResponse.builder()
                .assignmentId(assignment.getId().toString())
                .assignmentCode(assignment.getAssignmentCode())
                .title(assignment.getTitle())
                .token(githubToken)
                .repoUrl(assignment.getRepoUrl())
                .deadline(assignment.getDeadline())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/join")
    public ResponseEntity<JoinAssignmentResponse> joinAssignment(
            @RequestBody JoinAssignmentRequest request,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        User student = authService.getUserFromToken(token);

        JoinAssignmentResponse response = assignmentService.joinAssignment(
                request.getAssignmentCode(),
                request.getLocalPath(),
                student
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/class/{classCode}")
    public ResponseEntity<List<Map<String, Object>>> getAssignmentsByClass(
            @PathVariable String classCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);

        ClassRoom classRoom = classRoomService.getClassByCode(classCode);
        List<Assignment> assignments = assignmentService.getAssignmentsByClassroom(classRoom);
        List<Student> currentUserStudents = studentService.findByUser(user);
        List<Long> currentStudentIds = currentUserStudents.stream()
                .map(Student::getId)
                .toList();

        List<Map<String, Object>> result = assignments.stream().map(assignment -> {
            Map<String, Object> map = new HashMap<>();
            map.put("assignmentId", assignment.getId());
            map.put("assignmentCode", assignment.getAssignmentCode());
            map.put("title", assignment.getTitle());
            map.put("description", assignment.getDescription());
            map.put("repoUrl", assignment.getRepoUrl());
            map.put("deadline", assignment.getDeadline());
            map.put("createdAt", assignment.getCreatedAt());

            List<AssignmentTask> assignmentTasks = assignmentService.getTasksByAssignment(assignment);
            List<Map<String, Object>> taskPayload = assignmentTasks.stream().map(task -> {
                Map<String, Object> taskMap = new HashMap<>();
                taskMap.put("orderNo", task.getOrderNo());
                taskMap.put("taskName", task.getTaskName());
                taskMap.put("description", task.getDescription());
                return taskMap;
            }).collect(Collectors.toList());
            map.put("tasks", taskPayload);

            List<StudentAssignment> studentAssignments = studentAssignmentService.findByAssignment(assignment);
            map.put("studentCount", studentAssignments.size());

            StudentAssignment myAssignment = null;
            for (StudentAssignment sa : studentAssignments) {
                Long studentId = sa.getStudent().getId();

                if (currentStudentIds.contains(studentId)) {
                    myAssignment = sa;
                    break;
                }
            }

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
    }

    @GetMapping("/{assignmentCode}/students")
    public ResponseEntity<List<Map<String, Object>>> getStudentsInAssignment(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User teacher = authService.getUserFromToken(token);

        Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);

        if (!assignment.getClassRoom().getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).build();
        }

        List<StudentAssignment> studentAssignments =
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
    }


    // Update commit count for student assignment (called after push)
    @PostMapping("/{assignmentCode}/update-commits")
    public ResponseEntity<Void> updateCommitCount(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);

        assignmentService.updateCommitCountForUser(assignmentCode, user);

        log.info("Updated commit count for assignment {}", assignmentCode);
        Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
        List<TeacherAssignment> teacherAssignments = teacherAssignmentService.getTeacherAssignment(assignment);

        if (teacherAssignments != null && !teacherAssignments.isEmpty()) {
            for (TeacherAssignment ta : teacherAssignments) {
                if (ta.getTeacher() != null && !ta.getTeacher().getId().equals(user.getId())) {
                    notificationService.notifyTeacherOnSubmission(ta.getTeacher().getId(), user.getName(), assignment.getTitle(), assignment.getClassRoom().getName());
                }
            }
        } else if (assignment.getClassRoom() != null && assignment.getClassRoom().getTeacher() != null) {
            Long fallbackTeacherId = assignment.getClassRoom().getTeacher().getId();
            if (!fallbackTeacherId.equals(user.getId())) {
                log.warn("No teacher-assignment records found for {}. Fallback notifying class teacher {}.", assignmentCode, fallbackTeacherId);
                notificationService.notifyTeacherOnSubmission(fallbackTeacherId, user.getName(), assignment.getTitle(), assignment.getClassRoom().getName());
            }
        }

        return ResponseEntity.ok().build();
    }

    // Check if the deadline has passed for an assignment (Student only)
    @GetMapping("/{assignmentCode}/deadline/check")
    public ResponseEntity<Map<String, Object>> checkDeadline(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);

        Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
        if (assignment == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Assignment not found"));
        }

        LocalDateTime deadline = assignment.getDeadline();
        Map<String, Object> response = new HashMap<>();

        if (deadline == null) {
            // No deadline set - always allow push
            response.put("hasDeadline", false);
            response.put("canPush", true);
            response.put("message", "No deadline set for this assignment");
        } else {
            LocalDateTime now = LocalDateTime.now();
            boolean canPush = now.isBefore(deadline);

            response.put("hasDeadline", true);
            response.put("deadline", deadline.toString());
            response.put("canPush", canPush);

            if (canPush) {
                response.put("message", "Deadline has not passed yet");
            } else {
                response.put("message", "Deadline has passed");
            }
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/update-score")
    public ResponseEntity<Map<String, String>> updateScoreFromGithubAction(@RequestBody ScoreUpdateRequest request) {
        assignmentService.updateStudentScore(request);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Score updated successfully"
        ));
    }

    @DeleteMapping("/{assignmentCode}")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User teacher = authService.getUserFromToken(token);

        assignmentService.deleteAssignment(assignmentCode, teacher);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{assignmentCode}/workspace/sync")
    public ResponseEntity<Map<String, String>> syncAssignmentWorkspace(
            @PathVariable String assignmentCode,
            @RequestBody(required = false) Map<String, String> request,
            @RequestHeader("Authorization") String authHeader) throws IOException {
        String token = authHeader.substring(7);
        User teacher = authService.getUserFromToken(token);

        Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
        if (assignment == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Assignment not found"));
        }

        if (!assignment.getClassRoom().getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }

        String requestLocalPath = request != null ? request.get("localPath") : null;
        String localPath = requestLocalPath != null ? requestLocalPath.trim() : null;

        if (localPath == null || localPath.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Local path not found."));
        }
        List<StudentAssignment> studentAssignments = studentAssignmentService.findByAssignment(assignment);

        assignmentWorkspaceService.syncAssignmentWorkspace(studentAssignments, localPath);

        return ResponseEntity.ok(Map.of(
                "message", "Workspace synced successfully. All student branches are up to date."
        ));
    }

    @GetMapping("/{assignmentCode}/workspace/path")
    public ResponseEntity<Map<String, Object>> getAssignmentWorkspacePath(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
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
    }


    @PostMapping("/{assignmentCode}/teacher/localPath")
    public ResponseEntity<Map<String, Object>> saveTeacherLocalPath(
            @PathVariable String assignmentCode,
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String authHeader) {
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
    }

    @GetMapping("/{assignmentCode}/teacher/localPath")
    public ResponseEntity<Map<String, Object>> getTeacherLocalPath(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User teacher = authService.getUserFromToken(token);

        Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
        if (assignment == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Assignment not found"));
        }

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
    }

    @GetMapping("/{assignmentCode}/submissions")
    public ResponseEntity<?> getAssignmentSubmissions(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
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
    }

    @GetMapping("/export-excel/{assignmentId}")
    public void exportStudentPointExcel(HttpServletResponse response,
                                        @PathVariable Long assignmentId
    ) throws IOException {
        excelService.exportStudentPoint(response, assignmentId);
    }

    @PostMapping("/comments")
    public ResponseEntity<?> createComment(
            @RequestBody CreateCommentRequest request,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);
        CommentResponse response = commentService.createComment(request, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/comments")
    public ResponseEntity<?> getCommentsByFile(
            @RequestParam String assignmentCode,
            @RequestParam String targetBranch,
            @RequestParam String studentFilePath,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);
        List<CommentResponse> comments = commentService.getCommentsByFile(
                assignmentCode,
                targetBranch,
                studentFilePath,
                user
        );
        return ResponseEntity.ok(comments);
    }

    @GetMapping("/{assignmentCode}/student/localPath")
    public ResponseEntity<?> getStudentLocalPath(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);
        StudentAssignment studentAssignment = assignmentService.getStudentAssignmentInfo(assignmentCode, user);
        String localPath = studentAssignment.getLocalPath();
        return ResponseEntity.ok(Map.of(
                "localPath", localPath,
                "exists", localPath != null && !localPath.isBlank(),
                "branchName", studentAssignment.getBranchName()
        ));
    }

    @PatchMapping("/comments/{commentId}/resolve")
    public ResponseEntity<?> resolveComment(
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);
        CommentResponse response = commentService.resolveComment(commentId, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("tasks/result/{assignmentCode}")
    public ResponseEntity<?> getTaskResult(
            @PathVariable String assignmentCode,
            @RequestHeader("Authorization")  String authHeader
    ) {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);
        return ResponseEntity.ok(assignmentService.getTasks(user, assignmentCode));
    }
}

package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.dashboard.ClassStatisticsResponse;
import com.example.auto_git_be.dto.dashboard.CommitActivityResponse;
import com.example.auto_git_be.dto.dashboard.StudentDashboardResponse;
import com.example.auto_git_be.dto.dashboard.TeacherDashboardResponse;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.service.AuthService;
import com.example.auto_git_be.service.ClassRoomService;
import com.example.auto_git_be.service.StudentService;
import com.example.auto_git_be.service.StudentAssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private AuthService authService;

    @Autowired
    private ClassRoomService classRoomService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentAssignmentService studentAssignmentService;

    /**
     * Get dashboard data for student
     */
    @GetMapping("/student")
    public ResponseEntity<StudentDashboardResponse> getStudentDashboard(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);

            // Get all classes student is enrolled in
            List<Student> enrollments = studentService.findByUser(user);

            // Get all assignments student is working on
            int totalCommits = 0;
            LocalDateTime lastCommitAt = null;
            
            for (Student student : enrollments) {
                List<StudentAssignment> assignments = student.getAssignments();
                for (StudentAssignment sa : assignments) {
                    int commits = sa.getCommitCount() != null ? sa.getCommitCount() : 0;
                    totalCommits += commits;
                    
                    // Find most recent commit
                    if (sa.getLastCommitAt() != null) {
                        if (lastCommitAt == null || sa.getLastCommitAt().isAfter(lastCommitAt)) {
                            lastCommitAt = sa.getLastCommitAt();
                        }
                    }
                }
            }

            // Count total and active classes
            int totalClasses = enrollments.size();
            int activeClasses = (int) enrollments.stream()
                    .filter(s -> s.getClassRoom().getIsActive())
                    .count();

            StudentDashboardResponse response = StudentDashboardResponse.builder()
                    .totalCommits(totalCommits)
                    .lastCommitAt(lastCommitAt)
                    .totalClasses(totalClasses)
                    .activeClasses(activeClasses)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get dashboard data for teacher
     */
    @GetMapping("/teacher")
    public ResponseEntity<TeacherDashboardResponse> getTeacherDashboard(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);

            // Get all classes managed by teacher
            List<ClassRoom> classes = classRoomService.getClassesByTeacher(teacher);

            // Count total and active classes
            int totalClasses = classes.size();
            int activeClasses = (int) classes.stream()
                    .filter(ClassRoom::getIsActive)
                    .count();

            // Get all students across all classes
            int totalStudents = 0;
            int studentsSubmitted = 0;
            int totalCommits = 0;

            for (ClassRoom classroom : classes) {
                List<Student> students = studentService.findByClassRoom(classroom);
                totalStudents += students.size();

                for (Student student : students) {
                    // Get all assignments for this student
                    List<StudentAssignment> assignments = student.getAssignments();
                    int studentTotalCommits = 0;
                    
                    for (StudentAssignment sa : assignments) {
                        int commits = sa.getCommitCount() != null ? sa.getCommitCount() : 0;
                        studentTotalCommits += commits;
                        totalCommits += commits;
                    }

                    // Considered "submitted" if has more than 1 commit total (excluding init)
                    if (studentTotalCommits > 1) {
                        studentsSubmitted++;
                    }
                }
            }

            int studentsNotSubmitted = totalStudents - studentsSubmitted;
            double submittedPercentage = totalStudents > 0 
                    ? (studentsSubmitted * 100.0 / totalStudents) 
                    : 0.0;
            double notSubmittedPercentage = totalStudents > 0 
                    ? (studentsNotSubmitted * 100.0 / totalStudents) 
                    : 0.0;
            double averageCommitsPerStudent = totalStudents > 0 
                    ? (totalCommits * 1.0 / totalStudents) 
                    : 0.0;

            TeacherDashboardResponse response = TeacherDashboardResponse.builder()
                    .totalStudents(totalStudents)
                    .studentsSubmitted(studentsSubmitted)
                    .studentsNotSubmitted(studentsNotSubmitted)
                    .submittedPercentage(Math.round(submittedPercentage * 10) / 10.0)
                    .notSubmittedPercentage(Math.round(notSubmittedPercentage * 10) / 10.0)
                    .averageCommitsPerStudent(Math.round(averageCommitsPerStudent * 10) / 10.0)
                    .totalClasses(totalClasses)
                    .activeClasses(activeClasses)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get commit activity heatmap for student (last 28 days)
     */
    @GetMapping("/student/activity")
    public ResponseEntity<CommitActivityResponse> getStudentActivity(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User user = authService.getUserFromToken(token);

            Map<java.time.LocalDate, Integer> dailyCommits = studentService.getCommitActivityByUser(user);

            CommitActivityResponse response = CommitActivityResponse.builder()
                    .dailyCommits(dailyCommits)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get statistics for each class (teacher only)
     */
    @GetMapping("/teacher/classes")
    public ResponseEntity<List<ClassStatisticsResponse>> getTeacherClassStatistics(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            User teacher = authService.getUserFromToken(token);

            List<ClassRoom> classes = classRoomService.getClassesByTeacher(teacher);
            List<ClassStatisticsResponse> statistics = new java.util.ArrayList<>();

            for (ClassRoom classroom : classes) {
                List<Student> students = studentService.findByClassRoom(classroom);
                int totalStudents = students.size();
                int studentsSubmitted = 0;

                for (Student student : students) {
                    // Get all assignments for this student
                    List<StudentAssignment> assignments = student.getAssignments();
                    int studentTotalCommits = 0;
                    
                    for (StudentAssignment sa : assignments) {
                        int commits = sa.getCommitCount() != null ? sa.getCommitCount() : 0;
                        studentTotalCommits += commits;
                    }
                    
                    if (studentTotalCommits > 1) {
                        studentsSubmitted++;
                    }
                }

                int studentsNotSubmitted = totalStudents - studentsSubmitted;
                double submittedPercentage = totalStudents > 0 
                        ? (studentsSubmitted * 100.0 / totalStudents) 
                        : 0.0;
                double notSubmittedPercentage = totalStudents > 0 
                        ? (studentsNotSubmitted * 100.0 / totalStudents) 
                        : 0.0;

                ClassStatisticsResponse stat = ClassStatisticsResponse.builder()
                        .classId(classroom.getId())
                        .className(classroom.getName())
                        .classCode(classroom.getClassCode())
                        .totalStudents(totalStudents)
                        .studentsSubmitted(studentsSubmitted)
                        .studentsNotSubmitted(studentsNotSubmitted)
                        .submittedPercentage(Math.round(submittedPercentage * 10) / 10.0)
                        .notSubmittedPercentage(Math.round(notSubmittedPercentage * 10) / 10.0)
                        .isActive(classroom.getIsActive())
                        .build();

                statistics.add(stat);
            }

            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}


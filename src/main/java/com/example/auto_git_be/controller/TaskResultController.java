package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.assignment.TaskDTO;
import com.example.auto_git_be.dto.assignment.TaskResultRequest;
import com.example.auto_git_be.dto.assignment.TaskResultResponse;
import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.service.AssignmentService;
import com.example.auto_git_be.service.AuthService;
import com.example.auto_git_be.service.StudentService;
import com.example.auto_git_be.service.TaskResultService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/result")
@RequiredArgsConstructor
public class TaskResultController {

    private final AuthService authService;
    private final AssignmentService assignmentService;
    private final StudentService studentService;
    private final TaskResultService taskResultService;
    @PostMapping("/task")
    public ResponseEntity<TaskResultResponse> getTaskResult(
            @RequestBody TaskResultRequest taskResultRequest,
            @RequestHeader("Authorization") String authHeader)
    {
        String token = authHeader.substring(7);
        User user = authService.getUserFromToken(token);

        Long studentId = taskResultRequest.getStudentId();
        Student student;
        if(studentId == null){
            Assignment assignment = assignmentService.getAssignmentByCode(taskResultRequest.getAssignmentCode());
            ClassRoom  classRoom = assignment.getClassRoom();
            student = studentService.findByUserAndClassRoom(user, classRoom)
                    .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        }
        else{
            student = studentService.findById(studentId);
        }
        TaskResultResponse taskResultResponse = taskResultService.getTaskResults(student, taskResultRequest.getAssignmentCode());
        return  ResponseEntity.ok(taskResultResponse);
    }
}

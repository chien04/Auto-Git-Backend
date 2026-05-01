package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.assignment.TaskDTO;
import com.example.auto_git_be.dto.assignment.TaskResultResponse;
import com.example.auto_git_be.dto.assignment.TaskWithHistoryDTO;
import com.example.auto_git_be.entity.*;
import com.example.auto_git_be.repository.StudentTaskResultRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskResultService {

    private final StudentTaskResultRepository studentTaskResultRepository;
    private final AssignmentService assignmentService;
    private final StudentAssignmentService studentAssignmentService;
    public TaskResultResponse getTaskResults(Student student, String assigmentCode) {
        Assignment assignment = assignmentService.getAssignmentByCode(assigmentCode);
        List<AssignmentTask> assignmentTasks = assignment.getTasks();
        StudentAssignment studentAssignment = studentAssignmentService.findByStudentAndAssignment(student, assignment)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found with id " + assigmentCode));

        List<StudentTaskResult> allResults = studentTaskResultRepository.findByStudentAssignment(studentAssignment);

        // 2. Gom nhóm kết quả theo Task để dễ tra cứu
        Map<Long, List<StudentTaskResult>> resultsGroupedByTaskId = allResults.stream()
                .collect(Collectors.groupingBy(res -> res.getAssignmentTask().getId()));

        List<TaskWithHistoryDTO> taskResultList = new ArrayList<>();
        double totalEarnedScore = 0;

        for (AssignmentTask task : assignmentTasks) {
            List<StudentTaskResult> history = resultsGroupedByTaskId.get(task.getId());

            if (history != null && !history.isEmpty()) {
                history.sort((a, b) -> b.getId().compareTo(a.getId()));

                double bestScore = history.stream()
                        .mapToDouble(StudentTaskResult::getScore)
                        .max().orElse(0);
                totalEarnedScore += bestScore;

                List<TaskDTO> historyDTOs = history.stream()
                        .map(res -> TaskDTO.builder()
                                .resultId(res.getId())
                                .language(res.getLanguage())
                                .score(res.getScore())
                                .pass(res.getPass())
                                .total(res.getTotal())
                                .status(res.getStatus())
                                .commitHash(res.getCommitHash())
                                .sourceCode(res.getSourceCode())
                                .build())
                        .toList();

                taskResultList.add(TaskWithHistoryDTO.builder()
                        .taskId(task.getId())
                        .taskOrderNo(task.getOrderNo())
                        .taskName(task.getTaskName())
                        .bestScore(bestScore)
                        .maxScore(10)
                        .history(historyDTOs)
                        .build());
            } else {
                taskResultList.add(TaskWithHistoryDTO.builder()
                        .taskId(task.getId())
                        .taskOrderNo(task.getOrderNo())
                        .taskName(task.getTaskName())
                        .bestScore(0.0)
                        .maxScore(10)
                        .history(null)
                        .build());
            }
        }

        taskResultList.sort(Comparator.comparingInt(TaskWithHistoryDTO::getTaskOrderNo));

        int totalTasksInAssignment = assignmentTasks.size();
        int finalScore = totalTasksInAssignment == 0 ? 0 :
                (int) Math.round(totalEarnedScore / totalTasksInAssignment);

        return TaskResultResponse.builder()
                .totalScore(finalScore)
                .maxTotalScore(10)
                .tasks(taskResultList)
                .build();
    }
}

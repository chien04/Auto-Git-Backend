package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.execute.*;
import com.example.auto_git_be.entity.*;
import com.example.auto_git_be.repository.*;
import com.example.auto_git_be.utils.Language;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class JudgeService {

    private static final long POLLING_INTERVAL_MS = 300;

    private final WebClient judgeWebClient;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentTaskRepository assignmentTaskRepository;
    private final TestCaseRepository testCaseRepository;
    private final LocalCacheService localCacheService;
    private final StudentAssignmentRepository studentAssignmentRepository;
    private final StudentTaskResultRepository studentTaskResultRepository;
    private final GitHubService gitHubService;

    @Value("${judge0.polling.max-wait-ms:60000}")
    private long judge0MaxWaitMs;

    public ExecuteResponse runTests(ExecuteRequest request) {
        int langId = Language.getIdByName(request.getLanguage());

        Assignment assignment = assignmentRepository.findByAssignmentCode(request.getAssignmentCode())
                .orElseThrow(() -> new RuntimeException("Assignment not found"));

        AssignmentTask task = assignmentTaskRepository.findByAssignmentAndOrderNo(assignment, request.getTaskOrderNo())
                .orElseThrow(() -> new RuntimeException("Assignment task not found"));

        List<TestCase> exampleTestCases = testCaseRepository.findByAssignmentTaskAndIsSampleTrue(task);
        List<Judge0IndividualDTO> submissions = exampleTestCases.stream()
                .map(tc -> new Judge0IndividualDTO(
                        request.getSourceCode(),
                        langId,
                        tc.getInputContent(),
                        tc.getOutputContent()
                )).toList();

        if(submissions.isEmpty()) {
            ExecuteResponse response = new ExecuteResponse();
            response.setResults(new ArrayList<>());
            return response;
        }

        Judge0BatchRequest payload = new Judge0BatchRequest(submissions);

        List<Judge0TokenResponse> tokenList;

        try {
            tokenList = judgeWebClient.post()
                    .uri("/submissions/batch?base64_encoded=false")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToFlux(Judge0TokenResponse.class)
                    .collectList()
                    .block();

        } catch (WebClientResponseException.UnprocessableEntity e) {
            System.err.println(e.getResponseBodyAsString());
            throw new RuntimeException("Lỗi gọi judge0");
        } catch (Exception e) {
            throw new RuntimeException("Lỗi gọi Judge0");
        }

        if (tokenList == null || tokenList.isEmpty()) {
            throw new RuntimeException("Lỗi: Không nhận được token từ hệ thống chấm bài.");
        }

        String tokens = tokenList.stream()
                .map(Judge0TokenResponse::getToken)
                .collect(Collectors.joining(","));

        Judge0BatchResponse resultPayload = fetchResultsWithPolling(tokens);

        if (resultPayload == null || resultPayload.getSubmissions() == null) {
            throw new RuntimeException("Lỗi: Không thể lấy kết quả từ Judge0.");
        }

        List<Judge0ResultDTO> judgeSubmissions = resultPayload.getSubmissions();

        List<TestResultDTO> finalResults = IntStream.range(0, judgeSubmissions.size())
                .mapToObj(i -> {
                    Judge0ResultDTO res = judgeSubmissions.get(i);
                    TestCase originalTc = exampleTestCases.get(i);

                    return TestResultDTO.builder()
                            .statusId(res.getStatus() != null ? res.getStatus().getId() : 0)
                            .statusDescription(res.getStatus() != null ? res.getStatus().getDescription() : "Unknown")
                            .stdout(res.getStdout())
                            .time(res.getTime())
                            .memory(res.getMemory())
                            .stderr(res.getStderr())
                            .stdin(originalTc.getInputContent())
                            .build();
                })
                .toList();

        return new ExecuteResponse(finalResults);
    }

    @Transactional
    public SubmitResponse submitCodeForStudent(Student student, SubmitRequest request) throws IOException {
        Assignment assignment = assignmentRepository.findByAssignmentCode(request.getAssignmentCode())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Assignment: " + request.getAssignmentCode()));

        rejectStudentSubmitIfDeadlinePassed(assignment);

        AssignmentTask task = assignmentTaskRepository.findByAssignmentAndOrderNo(assignment, request.getTaskOrderNo())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Task số " + request.getTaskOrderNo()));

        StudentAssignment studentAssignment = studentAssignmentRepository.findByStudentAndAssignment(student, assignment)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));

        EvaluationResult evalResult = executeJudge0Evaluation(task, request.getLanguage(), request.getSourceCode());
        int passed = evalResult.getPassed();
        int total = evalResult.getTotal();
        Double time = evalResult.getMaxTime();
        int memory = evalResult.getMaxMemory();
        String finalStatus = evalResult.getOverallStatus();
        String finalErrorMessage = evalResult.getErrorMessage();

        double score = (double) passed / total * 10;

        String commitHash = gitHubService.pushStudentCode(
                assignment.getRepoName(),
                studentAssignment.getBranchName(),
                request.getFilePath(),
                request.getSourceCode(),
                "Submit task " + request.getTaskOrderNo() + " - Score: " + score
        );

        StudentTaskResult taskResult = StudentTaskResult.builder()
                .studentAssignment(studentAssignment)
                .assignmentTask(task)
                .language(request.getLanguage())
                .score(score)
                .pass(passed)
                .total(total)
                .status(finalStatus)
                .commitHash(commitHash)
                .sourceCode(request.getSourceCode())
                .time(time)
                .memory(memory)
                .errorMessage(finalErrorMessage)
                .build();

        studentTaskResultRepository.save(taskResult);
        double assignmentScore = updateStudentAssignmentScore(studentAssignment, taskResult);

        return SubmitResponse.builder()
                .score(score)
                .assignmentScore(assignmentScore)
                .passedTestCases(passed)
                .totalTestCases(total)
                .status(finalStatus)
                .time(time)
                .memory(memory)
                .build();
    }

    public SubmitResponse submitCodeForTeacher(SubmitRequest request) throws IOException {
        Assignment assignment = assignmentRepository.findByAssignmentCode(request.getAssignmentCode())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Assignment: " + request.getAssignmentCode()));

        AssignmentTask task = assignmentTaskRepository.findByAssignmentAndOrderNo(assignment, request.getTaskOrderNo())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Task‘ " + request.getTaskOrderNo()));

        EvaluationResult evalResult = executeJudge0Evaluation(task, request.getLanguage(), request.getSourceCode());
        int passed = evalResult.getPassed();
        int total = evalResult.getTotal();
        Double time = evalResult.getMaxTime();
        int memory = evalResult.getMaxMemory();
        String finalStatus = evalResult.getOverallStatus();
        String finalErrorMessage = evalResult.getErrorMessage();

        double score = (double) passed / total * 10;

        gitHubService.pushStudentCode(
                assignment.getRepoName(),
                "teacher",
                request.getFilePath(),
                request.getSourceCode(),
                "Teacher submit task " + request.getTaskOrderNo() + " - Score: " + score
        );

        return SubmitResponse.builder()
                .score(score)
                .assignmentScore(score)
                .passedTestCases(passed)
                .totalTestCases(total)
                .status(finalStatus)
                .time(time)
                .memory(memory)
                .errorMessage(finalErrorMessage)
                .build();
    }

    private void rejectStudentSubmitIfDeadlinePassed(Assignment assignment) {
        LocalDateTime deadline = assignment.getDeadline();
        if (deadline != null && LocalDateTime.now().isAfter(deadline)) {
            throw new IllegalArgumentException("Đã quá deadline, không thể nộp bài nữa.");
        }
    }

    private double updateStudentAssignmentScore(StudentAssignment studentAssignment, StudentTaskResult latestTaskResult) {
        Assignment assignment = studentAssignment.getAssignment();
        List<AssignmentTask> assignmentTasks = assignment.getTasks();
        int totalTasks = assignmentTasks == null ? 0 : assignmentTasks.size();
        if (totalTasks == 0) {
            studentAssignment.setScore(0.0);
            studentAssignmentRepository.save(studentAssignment);
            return 0.0;
        }

        List<StudentTaskResult> allResults = new ArrayList<>(studentTaskResultRepository.findByStudentAssignment(studentAssignment));
        allResults.add(latestTaskResult);

        Map<Long, Double> bestScoreByTaskId = allResults.stream()
                .filter(result -> result.getAssignmentTask() != null && result.getScore() != null)
                .collect(Collectors.groupingBy(
                        result -> result.getAssignmentTask().getId(),
                        Collectors.mapping(
                                StudentTaskResult::getScore,
                                Collectors.collectingAndThen(
                                        Collectors.maxBy(Double::compareTo),
                                        maxScore -> maxScore.orElse(0.0)
                                )
                        )
                ));

        double earnedScore = assignmentTasks.stream()
                .mapToDouble(task -> bestScoreByTaskId.getOrDefault(task.getId(), 0.0))
                .sum();
        double assignmentScore = earnedScore / totalTasks;
        studentAssignment.setScore(assignmentScore);
        studentAssignmentRepository.save(studentAssignment);
        return assignmentScore;
    }

    private EvaluationResult executeJudge0Evaluation(AssignmentTask task, String language, String sourceCode) {
        List<TestCase> testCases = testCaseRepository.findByAssignmentTaskAndIsSampleFalse(task);
        if (testCases == null || testCases.isEmpty()) {
            throw new RuntimeException("Lỗi: Bài tập này chưa được giáo viên upload Test Case.");
        }

        int langId = Language.getIdByName(language);
        List<Judge0IndividualDTO> submissionsList = new ArrayList<>();

        for (TestCase tc : testCases) {
            String localInName = "input_" + tc.getOrdinal() + ".txt";
            String localOutName = "output_" + tc.getOrdinal() + ".txt";

            String inputContent = localCacheService.getTestcaseContent(
                    task.getId(), tc.getInputFileUrl(), localInName);

            String outputContent = localCacheService.getTestcaseContent(
                    task.getId(), tc.getOutputFileUrl(), localOutName);

            String encodedSource = Base64.getEncoder().encodeToString(sourceCode.getBytes());
            String encodedStdin = Base64.getEncoder().encodeToString(inputContent.getBytes());
            String encodedExpectedOutput = Base64.getEncoder().encodeToString(outputContent.getBytes());

            submissionsList.add(new Judge0IndividualDTO(
                    encodedSource, langId, encodedStdin, encodedExpectedOutput
            ));
        }

        Judge0BatchRequest payload = new Judge0BatchRequest(submissionsList);
        List<Judge0TokenResponse> tokenList;
        try {
            tokenList = judgeWebClient.post()
                    .uri("/submissions/batch?base64_encoded=true")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToFlux(Judge0TokenResponse.class)
                    .collectList()
                    .block();
        } catch (WebClientResponseException ex) {
            String errorBody = ex.getResponseBodyAsString();
            throw new RuntimeException("Judge0 trả về lỗi " + ex.getStatusCode() + " - Chi tiết: " + errorBody, ex);
        }

        if (tokenList == null || tokenList.isEmpty()) {
            throw new RuntimeException("Lỗi: Không nhận được token submit.");
        }

        String tokens = tokenList.stream()
                .map(Judge0TokenResponse::getToken)
                .collect(Collectors.joining(","));

        Judge0BatchResponse resultPayload = fetchResultsWithPolling(tokens);

        int passed = 0;
        double maxTime = 0.0;
        int maxMemory = 0;
        String overallStatus = "Accepted";
        String errorMessage = null;

        for (Judge0ResultDTO res : resultPayload.getSubmissions()) {
            int currentStatusId = (res.getStatus() != null) ? res.getStatus().getId() : 0;

            if (currentStatusId == 3) {
                passed++; // ID 3 là Accepted
            } else if ("Accepted".equals(overallStatus)) {
                overallStatus = mapJudge0StatusToText(currentStatusId);

                if (currentStatusId == 6) {
                    errorMessage = res.getCompileOutput();
                } else {
                    errorMessage = res.getStderr();
                }
            }
            if (res.getTime() != null) {
                try {
                    double t = Double.parseDouble(res.getTime());
                    maxTime = Math.max(maxTime, t);
                } catch (NumberFormatException ignored) {}
            }
            if (res.getMemory() != null) {
                maxMemory = Math.max(maxMemory, res.getMemory());
            }
        }

        return EvaluationResult.builder()
                .passed(passed)
                .total(testCases.size())
                .maxTime(maxTime)
                .maxMemory(maxMemory)
                .overallStatus(overallStatus)
                .errorMessage(errorMessage)
                .build();
    }

    private Judge0BatchResponse fetchResultsWithPolling(String tokens) {
        long startTime = System.currentTimeMillis();
        long deadline = startTime + judge0MaxWaitMs;

        boolean isFinished = false;
        Judge0BatchResponse resultPayload = null;
        int pollCount = 0;

        while (System.currentTimeMillis() < deadline && !isFinished) {
            pollCount++;

            try {
                Thread.sleep(POLLING_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Tiến trình chờ bị gián đoạn", e);
            }

            resultPayload = judgeWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/submissions/batch")
                            .queryParam("tokens", tokens)
                            .queryParam("base64_encoded", false)
                            .queryParam("fields",
                                    "status_id,stdout,time,memory,stderr,status,compile_output")
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .map(body -> new RuntimeException("Judge0 error: " + body))
                    )
                    .bodyToMono(Judge0BatchResponse.class)
                    .block();

            if (resultPayload != null && resultPayload.getSubmissions() != null) {
                boolean stillProcessing = resultPayload.getSubmissions().stream()
                        .anyMatch(sub -> {
                            int currentStatus = sub.getStatus() != null
                                    ? sub.getStatus().getId()
                                    : sub.getStatusId();

                            return currentStatus == 1 || currentStatus == 2;
                        });

                isFinished = !stillProcessing;
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;

        if (!isFinished) {
            log.warn(
                    "Judge0 timeout sau {} ms ({} s), so lan polling: {}",
                    durationMs,
                    durationMs / 1000.0,
                    pollCount
            );

            throw new RuntimeException(
                    "Loi: Server Judge0 qua tai, cham bai mat qua "
                            + (judge0MaxWaitMs / 1000)
                            + " giay."
            );
        }

        log.info(
                "Judge0 hoan thanh sau {} ms ({} s), so lan polling: {}",
                durationMs,
                durationMs / 1000.0,
                pollCount
        );

        return resultPayload;
    }

    private String mapJudge0StatusToText(int statusId) {
        return switch (statusId) {
            case 3 -> "Accepted";
            case 4 -> "Wrong Answer";
            case 5 -> "Time Limit Exceeded";
            case 6 -> "Compilation Error";
            case 7, 8, 9, 10, 11, 12 -> "Runtime Error";
            case 13 -> "Internal Error";
            case 14 -> "Exec Format Error";
            default -> "Error";
        };
    }
}

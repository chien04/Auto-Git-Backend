package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.execute.*;
import com.example.auto_git_be.entity.*;
import com.example.auto_git_be.repository.*;
import com.example.auto_git_be.utils.Language;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class JudgeService {

    private final WebClient judgeWebClient;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentTaskRepository assignmentTaskRepository;
    private final TestCaseRepository testCaseRepository;
    private final LocalCacheService localCacheService;
    private final StudentAssignmentRepository studentAssignmentRepository;
    private final StudentTaskResultRepository studentTaskResultRepository;
    private final GitHubService gitHubService;

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
    public SubmitResponse submitCode(SubmitRequest request) {
        Assignment assignment = assignmentRepository.findByAssignmentCode(request.getAssignmentCode())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Assignment: " + request.getAssignmentCode()));

        AssignmentTask task = assignmentTaskRepository.findByAssignmentAndOrderNo(assignment, request.getTaskOrderNo())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Task số " + request.getTaskOrderNo()));

        EvaluationResult evalResult = executeJudge0Evaluation(task, request.getLanguage(), request.getSourceCode());
        int passed = evalResult.getPassed();
        int total = evalResult.getTotal();
        Double time = evalResult.getMaxTime();
        int memory = evalResult.getMaxMemory();
        String finalStatus = evalResult.getOverallStatus();
        String finalErrorMessage = evalResult.getErrorMessage();

        int score = (int) Math.round((double) passed / total * 100);

        return SubmitResponse.builder()
                .time(time)
                .memory(memory)
                .score(score)
                .passedTestCases(passed)
                .totalTestCases(total)
                .status(finalStatus)
                .errorMessage(finalErrorMessage)
                .build();
    }

    @Transactional
    public SubmitResponse submitCodeForStudent(Student student, SubmitRequest request) throws IOException {
        Assignment assignment = assignmentRepository.findByAssignmentCode(request.getAssignmentCode())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Assignment: " + request.getAssignmentCode()));

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

        return SubmitResponse.builder()
                .score(score)
                .passedTestCases(passed)
                .totalTestCases(total)
                .status(finalStatus)
                .time(time)
                .memory(memory)
                .build();
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
        int maxRetries = 50;
        int attempts = 0;
        boolean isFinished = false;
        Judge0BatchResponse resultPayload = null;

        while (attempts < maxRetries && !isFinished) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Tiến trình chờ bị gián đoạn", e);
            }

            resultPayload = judgeWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/submissions/batch")
                            .queryParam("tokens", tokens)
                            .queryParam("base64_encoded", false)
                            .queryParam("fields", "status_id,stdout,time,memory,stderr,status")
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
                            int currentStatus = sub.getStatus() != null ? sub.getStatus().getId() : sub.getStatusId();
                            return currentStatus == 1 || currentStatus == 2;
                        });

                isFinished = !stillProcessing;
            }

            attempts++;
        }

        if (!isFinished) {
            throw new RuntimeException("Lỗi: Server Judge0 quá tải, chấm bài mất quá 10 giây.");
        }

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

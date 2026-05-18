package com.example.auto_git_be.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorQueryTool {

    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrantClient;

    private static final String COLLECTION_NAME = "ai_chat";

    @Tool("""
            Semantic search over algorithm descriptions and implementation summaries in the Vector DB.
            
            Use for: algorithm analysis, comparing approaches, detecting similar implementations, plagiarism detection.
            Do NOT use for: scores, submission statistics, student lists.
            
            The Vector DB stores NATURAL LANGUAGE DESCRIPTIONS of student code, not raw source.
            Each entry contains: algorithm summary, execution flow, data structure usage, implementation behavior.
            
            Only retrieve results. The assistant decides how much to show and whether analysis is needed.
            Do NOT auto-generate long comparisons, reports, or full source code output.
            """)
    public String searchStudentCode(

            @P("""
                    Semantic description to search for.
                    Pass the exact string from 'SYSTEM REWRITTEN QUERY INFO' verbatim. Do not translate or rewrite.
                    """)
            String semanticQuery,

            @P("""
                    Student names to filter. Comma-separated for multiple. Use "ALL" for class-wide search (plagiarism, compare strategies).
                    """)
            String studentNames,

            @P("Task order number (1, 2, 3...) or \"ALL\" to search across all tasks.")
            String taskOrderNo,

            @P("Current assignment code. Required.")
            String assignmentCode,

            @P("Set to true ONLY when user explicitly requests plagiarism check or full-class comparison. False otherwise.")
            Boolean isPlagiarismCheck
    ) {
        log.info("[Vector Tool] AI đang tìm kiếm: query='{}', student='{}', file='{}', isPlagiarismCheck={}", semanticQuery, studentNames, taskOrderNo, isPlagiarismCheck);

        try {
            float[] vectorArray = embeddingModel.embed(semanticQuery).content().vector();
            List<Float> vector = new ArrayList<>();
            for (float v : vectorArray) vector.add(v);

            int limit = 5;
            float thresholdScore;
            boolean isAllStudents = studentNames == null || studentNames.equalsIgnoreCase("ALL");
            List<String> namesList = new ArrayList<>();

            if (!isAllStudents) {
                namesList = Arrays.stream(studentNames.split(","))
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toList());
                if (namesList.size() > 1) limit = 10;
                thresholdScore = 0f;
            } else {
                if (Boolean.TRUE.equals(isPlagiarismCheck)) {
                    limit = Integer.MAX_VALUE;
                    thresholdScore = 0f;
                } else {
                    thresholdScore = 0.3f;
                }
            }
            Filter qdrantFilter = buildFilter(assignmentCode, namesList, taskOrderNo);

            SearchPoints.Builder searchBuilder = SearchPoints.newBuilder()
                    .setCollectionName(COLLECTION_NAME)
                    .addAllVector(vector)
                    .setLimit(limit)
                    .setScoreThreshold(thresholdScore)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build());

            searchBuilder.setFilter(qdrantFilter);
            List<ScoredPoint> points = qdrantClient.searchAsync(searchBuilder.build()).get();
            if (points.isEmpty()) {
                return "Không tìm thấy đoạn mã nguồn nào khớp với yêu cầu tìm kiếm ngữ nghĩa này.";
            }

            return points.stream()
                    .map(point -> {
                        Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
                        String sName = getPayloadString(payload, "student_name");
                        String fName = getPayloadString(payload, "file_name");
                        String orderNo = getPayloadString(payload, "task_order_no");
                        String code = getPayloadString(payload, "raw_source_code");

                        return String.format(
                                "--- BEGIN RESULT ---\n" +
                                        "Student: %s\n" +
                                        "File: %s (Task %s)\n" +
                                        "Source code:\n```\n%s\n```\n" +
                                        "--- END RESULT ---",
                                sName, fName, orderNo, code
                        );
                    })
                    .collect(Collectors.joining("\n\n"));

        } catch (Exception e) {
            log.error("Lỗi khi query Vector DB: ", e);
            return "Lỗi hệ thống khi truy xuất Vector Database. Hãy yêu cầu người dùng thử lại.";
        }
    }

    private Filter buildFilter(String assignmentCode, List<String> namesList, String taskOrderNo) {
        List<Condition> musts = new ArrayList<>();

        musts.add(Condition.newBuilder()
                .setField(FieldCondition.newBuilder()
                        .setKey("assignment_code")
                        .setMatch(Match.newBuilder().setKeyword(assignmentCode).build())
                        .build())
                .build());

        if (!namesList.isEmpty()) {
            if (namesList.size() == 1) {
                musts.add(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("student_name")
                                .setMatch(Match.newBuilder().setKeyword(namesList.getFirst()).build())
                                .build())
                        .build());
            } else {
                List<Condition> shouldConditions = namesList.stream()
                        .map(name -> Condition.newBuilder()
                                .setField(FieldCondition.newBuilder()
                                        .setKey("student_name")
                                        .setMatch(Match.newBuilder().setKeyword(name).build())
                                        .build())
                                .build())
                        .collect(Collectors.toList());

                musts.add(Condition.newBuilder()
                        .setNested(NestedCondition.newBuilder()
                                .setKey("student_name")
                                .setFilter(Filter.newBuilder().addAllShould(shouldConditions).build())
                                .build())
                        .build());
            }
        }

        if (taskOrderNo != null && !taskOrderNo.equalsIgnoreCase("ALL")) {
            musts.add(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey("task_order_no")
                            .setMatch(Match.newBuilder().setKeyword(taskOrderNo).build())
                            .build())
                    .build());
        }

        return Filter.newBuilder().addAllMust(musts).build();
    }

    private String getPayloadString(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value == null) return "N/A";
        return value.getStringValue();
    }
}
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
            Perform semantic search over algorithm descriptions and implementation summaries stored in the Vector DB.
            
            USE THIS TOOL FOR:
            - Finding related solution logic
            - Comparing implementation approaches
            - Detecting structurally similar algorithms
            - Understanding how students solved a problem
            
            DO NOT USE THIS TOOL FOR:
            - Scores
            - Submission statistics
            - Student lists
            - Submission status reports
            
            The Vector DB stores NATURAL LANGUAGE DESCRIPTIONS of source code,
            NOT raw source code.
            
            Each embedding may contain:
            - Algorithm summaries
            - Execution flow descriptions
            - Data structure usage
            - High-level implementation behavior
            
            IMPORTANT:
            This tool ONLY retrieves semantically related implementation descriptions.
            
            The assistant MUST decide:
            - how much detail to show
            - whether detailed analysis is necessary
            - whether code snippets should be shown
            
            Do NOT automatically generate:
            - long comparisons
            - detailed reports
            - full algorithm explanations
            - source code output
            
            ════════════════════════════════════════
            PLAGIARISM DETECTION RULES
            ════════════════════════════════════════
            
            Focus ONLY on:
            - algorithm structure
            - execution flow
            - implementation strategy
            - control flow similarity
            
            Ignore:
            - same problem requirements
            - same functionality
            - similar outputs
            - variable names
            - simple syntax similarity
            
            Different algorithms or clearly different approaches
            should NOT be considered plagiarism.
            """)
    public String searchStudentCode(

            @P("""
                    Semantic description to search for.
                    MUST NOT be empty or null.
                    
                    Since the Vector DB stores English natural-language
                    descriptions of source code, always convert programming
                    concepts into technical English descriptions before searching.
                    
                    Examples:
                    - "đệ quy"
                      → "recursive function calling itself"
                    
                    - "vòng lặp vô hạn"
                      → "infinite loop using while(true)"
                    
                    - "quy hoạch động"
                      → "dynamic programming state transition solution"
                    
                    - "tham lam"
                      → "greedy algorithm choosing local optimum"
                    
                    Focus on:
                    - algorithm behavior
                    - execution flow
                    - implementation strategy
                    - data structure usage
                    - logical patterns
                    """)
            String semanticQuery,

            @P("""
                    Student names to analyze.
                    
                    Rules:
                    - Single student:
                      "Nguyen Van A"
                    
                    - Multiple students:
                      "Nguyen Van A, Tran Van B"
                    
                    - Entire class:
                      "ALL"
                    
                    IMPORTANT:
                    Use "ALL" for:
                    - plagiarism detection
                    - comparing solution strategies
                    - finding similar implementations
                    """)
            String studentNames,

            @P("""
                    Task order number corresponding to order_no in the database.
                    
                    Examples:
                    - "1" → task1
                    - "2" → task2
                    - "ALL" → search across all tasks
                    """)
            String taskOrderNo,

            @P("""
                    Current assignment code.
                    REQUIRED.
                    """)
            String assignmentCode
    ) {
        log.info("[Vector Tool] AI đang tìm kiếm: query='{}', student='{}', file='{}'", semanticQuery, studentNames, taskOrderNo);

        try {
            float[] vectorArray = embeddingModel.embed(semanticQuery).content().vector();
            List<Float> vector = new ArrayList<>();
            for (float v : vectorArray) vector.add(v);

            int limit = 5;
            float thresholdScore = 0;
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
                limit = 100;
                thresholdScore = 0.3f;
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
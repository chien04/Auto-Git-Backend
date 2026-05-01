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
            Tìm kiếm ngữ nghĩa và đọc MÃ NGUỒN (source code) của sinh viên từ Vector DB.
            DÙNG KHI: xem chi tiết code, phân tích thuật toán, tìm lỗi logic, kiểm tra đạo văn.
            KHÔNG DÙNG ĐỂ: lấy điểm số, thống kê, danh sách nộp bài — dùng executeQuery thay thế.
            
            HƯỚNG DẪN KIỂM TRA ĐẠO VĂN / COPY CODE:
            Khi người dùng hỏi "có ai copy code không", "kiểm tra đạo văn", "so sánh code giữa các sinh viên":
            1. Gọi tool này NHIỀU LẦN, mỗi lần với query là đoạn code đặc trưng của từng sinh viên.
            2. Lần đầu: dùng query chung như 'solution implementation' với studentNames='ALL' để lấy mẫu code của tất cả sinh viên.
            3. So sánh cấu trúc, biến, logic giữa các kết quả trả về.
            4. KHÔNG dùng query mô tả bài toán (VD: 'find largest number') vì sẽ tìm theo ngữ nghĩa bài toán thay vì tìm code giống nhau.
            SAU KHI NHẬN KẾT QUẢ TỪ TOOL:
                    - Nếu detailLevel='summary': KHÔNG được tự ý in lại code trong câu trả lời, chỉ phân tích và nhận xét.
                    - Nếu detailLevel='full': Được phép hiển thị những đoạn code liên quan không phải trả về toàn bộ source trừ khi người dùng muốn xem full.
    """)
    public String searchStudentCode(
            @P("""
            Mô tả ngữ nghĩa cần tìm. KHÔNG ĐƯỢC ĐỂ TRỐNG HOẶC NULL.
            Vì Vector DB chứa mã nguồn (không có bình luận tiếng Việt), nếu người dùng hỏi các khái niệm lập trình (như 'đệ quy', 'vòng lặp'), bạn KHÔNG ĐƯỢC truyền nguyên chữ tiếng Việt vào.
            Hãy TỰ ĐỘNG DỊCH khái niệm đó thành cấu trúc code hoặc tiếng Anh chuyên ngành trước khi truyền vào.
            VD:
            - Hỏi 'đệ quy' -> Truyền: 'recursive function calling itself'
            - Hỏi 'vòng lặp vô hạn' -> Truyền: 'while(true) infinite loop'
            hoặc paste một đoạn code mẫu để tìm code tương tự.
            """) String semanticQuery,

            @P("""
            Tên sinh viên cần xem code.
            - Nếu xem 1 người: Ghi đúng tên (VD: 'Nguyễn Văn A').
            - Nếu so sánh NHIỀU người: Ghi các tên cách nhau bằng dấu phẩy (VD: 'Nguyễn Văn A, Trần Văn B').
            - Nếu quét cả lớp: Ghi 'ALL'.
            (bắt buộc dùng ALL khi kiểm tra đạo văn hoặc so sánh cách code).
            """) String studentNames,

            @P("""
            Số thứ tự bài (task) cần tìm, tương ứng với order_no trong DB.
            VD: '1' để tìm trong task1, '2' để tìm trong task2.
            Ghi 'ALL' nếu muốn tìm trên mọi task/file.
            """) String taskOrderNo,

            @P("""
            Mức độ chi tiết cần trả về:
            - 'summary': Chỉ trả tên sinh viên, file, score. DÙNG KHI: so sánh, kiểm tra đạo văn, thống kê.
            - 'full': Trả đầy đủ cả code. DÙNG KHI: người dùng yêu cầu xem code cụ thể, phân tích lỗi.
            """) String detailLevel,

            @P("Mã bài tập hiện tại (bắt buộc)") String assignmentCode
    ) {
        log.info("[Vector Tool] AI đang tìm kiếm: query='{}', student='{}', file='{}'", semanticQuery, studentNames, taskOrderNo);

        try {
            float[] vectorArray = embeddingModel.embed(semanticQuery).content().vector();
            List<Float> vector = new ArrayList<>();
            for (float v : vectorArray) vector.add(v);

            int limit = 5;
            boolean isAllStudents = studentNames == null || studentNames.equalsIgnoreCase("ALL");
            List<String> namesList = new ArrayList<>();

            if (!isAllStudents) {
                namesList = Arrays.stream(studentNames.split(","))
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toList());
                if (namesList.size() > 1) limit = 10;
            } else {
                limit = 100;
            }
            Filter qdrantFilter = buildFilter(assignmentCode, namesList, taskOrderNo);

            SearchPoints.Builder searchBuilder = SearchPoints.newBuilder()
                    .setCollectionName(COLLECTION_NAME)
                    .addAllVector(vector)
                    .setLimit(limit)
                    .setScoreThreshold(0.3f)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build());

            searchBuilder.setFilter(qdrantFilter);
            List<ScoredPoint> points = qdrantClient.searchAsync(searchBuilder.build()).get();
            if (points.isEmpty()) {
                return "Không tìm thấy đoạn mã nguồn nào khớp với yêu cầu tìm kiếm ngữ nghĩa này.";
            }

            return points.stream()
                    .map(point -> {
                        Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
                        String sName    = getPayloadString(payload, "student_name");
                        String fName    = getPayloadString(payload, "file_name");
                        String orderNo  = getPayloadString(payload, "task_order_no");
                        String code     = getPayloadString(payload, "text_segment");

                        String codeSection;
                        if ("full".equalsIgnoreCase(detailLevel)) {
                            codeSection = String.format("\nMã nguồn:\n```\n%s\n```", code);
                        } else {
                            String preview = code.length() > 100
                                    ? code.substring(0, 100) + "..."
                                    : code;
                            codeSection = String.format("\nPreview: %s", preview);
                        }

                        return String.format(
                                "--- BẮT ĐẦU KẾT QUẢ ---\n" +
                                        "Sinh viên: %s\n" +
                                        "File: %s (Task %s)\n" +
                                        "Mã nguồn:\n```\n%s\n```\n" +
                                        "--- KẾT THÚC KẾT QUẢ ---",
                                sName, fName, orderNo, codeSection
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
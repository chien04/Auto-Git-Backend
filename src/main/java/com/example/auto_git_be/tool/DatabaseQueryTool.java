package com.example.auto_git_be.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseQueryTool {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Tool("""
    Truy vấn THỐNG KÊ và THÔNG TIN bài tập từ database.
    DÙNG KHI: hỏi về điểm số, trạng thái nộp bài, số lượng sinh viên, thống kê lỗi, danh sách task.
    KHÔNG DÙNG ĐỂ: đọc nội dung mã nguồn (source_code) — dùng searchStudentCode thay thế.
    LƯU Ý: Một sinh viên có thể làm nhiều task. Mỗi task có order_no (số thứ tự: 1, 2, 3...).
    """)
    public String executeQuery(
            @P("""
            DANH SÁCH CỘT CẦN LẤY (phân cách bằng dấu phẩy).
            LUÔN bao gồm: 'assignment_title', 'student_name'.
            CÁC CỘT HỢP LỆ: assignment_title, student_name, task_name, order_no,
              score, pass, total, status, error_message, execution_time, memory_used, language.
            TUYỆT ĐỐI KHÔNG chọn: student_id, assignment_code, source_code.
            """) String selectColumns,

            @P("""
            Điều kiện WHERE (KHÔNG bắt đầu bằng 'WHERE').
            Tìm theo task: dùng order_no. VD: order_no = 1 (tức task1), order_no = 2 (tức task2).
            Tìm theo tên SV: REPLACE(LOWER(student_name), ' ', '') LIKE '%nguyenvana%'.
            Tìm theo trạng thái: status = 'Compilation Error'.
            Để trống nếu không cần lọc thêm.
            """) String conditions,

            @P("""
            Mệnh đề GROUP BY, ORDER BY, LIMIT (nếu cần).
            LƯU Ý CỰC KỲ QUAN TRỌNG VỚI GROUP BY: Trong SQL, MỌI CỘT liệt kê ở phần SELECT bắt buộc phải có mặt trong mệnh đề GROUP BY (nếu không dùng hàm tổng hợp như COUNT, SUM). 
            Ví dụ đúng: Nếu SELECT là 'assignment_title, student_name, status', thì phải là 'GROUP BY assignment_title, student_name, status'.
            VD khác: GROUP BY student_name ORDER BY student_name ASC LIMIT 10.
            Để trống nếu không cần.

            """) String groupingAndSorting,

            @P("Mã bài tập hiện tại (bắt buộc, dùng để filter nội bộ)") String assignmentCode
    ) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT ");
        sqlBuilder.append(selectColumns != null && !selectColumns.trim().isEmpty() ? selectColumns : "*");
        sqlBuilder.append(" FROM v_assignment_analytics WHERE assignment_code = ?");

        if (conditions != null && !conditions.trim().isEmpty()) {
            sqlBuilder.append(" AND (").append(conditions).append(")");
        }

        if (groupingAndSorting != null && !groupingAndSorting.trim().isEmpty()) {
            String upperTail = groupingAndSorting.toUpperCase();
            if (upperTail.contains("GROUP BY") || upperTail.contains("ORDER BY") || upperTail.contains("LIMIT")) {
                sqlBuilder.append(" ").append(groupingAndSorting);
            }
        }

        String finalSql = sqlBuilder.toString();
        log.info("Thực thi truy vấn an toàn: {} với mã: {}", finalSql, assignmentCode);

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(finalSql, assignmentCode);

            if (results.isEmpty()) {
                return "Không tìm thấy dữ liệu nào khớp với yêu cầu.";
            }
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            log.error("Lỗi khi truy vấn DB Tool: {}", e.getMessage());
            return "Lỗi thực thi dữ liệu: Vui lòng kiểm tra lại cấu trúc câu SQL do bạn tạo ra.";
        }
    }
}
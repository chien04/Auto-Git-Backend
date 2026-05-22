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
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseQueryTool {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "TRUNCATE",
            "EXEC", "EXECUTE", "UNION", "INTO", "--", "/*", "*/"
    );

    private static final Set<String> BLOCKED_COMMENT_TOKENS = Set.of("--", "/*", "*/");

    @Tool("""
            Execute a SQL query against the assignment analytics view.
            Use for: scores, execution time, memory usage, submission status, student lists, error statistics, rankings.
            
            IMPORTANT:
            - "execution time", "fastest", "runtime", "optimal by time" means the execution_time column, not SQL EXEC.
            - Highest/lowest score queries must exclude NULL scores: AND score IS NOT NULL.
            - Fastest/slowest runtime queries must exclude NULL execution times: AND execution_time IS NOT NULL.
            - Memory rankings must exclude NULL memory values: AND memory_used IS NOT NULL.
            - For "best/optimal algorithm/solution", correctness comes first: order by non-null score DESC, then execution_time ASC only as a tie-breaker.
              Use: AND score IS NOT NULL ORDER BY score DESC, CASE WHEN execution_time IS NULL THEN 1 ELSE 0 END, execution_time ASC LIMIT 1.
              Then use searchStudentCode for that returned student/task.
            Do NOT use for source code analysis — use searchStudentCode instead.
            
            DATA MODEL: 1 row = 1 task submission. One student → many rows (one per task). order_no = task number (1, 2, 3...).
            
            Built as: SELECT {selectClause} FROM v_assignment_analytics WHERE assignment_code = ? {tailClause}
            """)
    public String executeQuery(

            @P("""
                    Columns to SELECT (part between SELECT and FROM).
                    
                    Available columns:
                    assignment_title, student_name, task_name, order_no,
                    score, pass, total, status, error_message,
                    execution_time, memory_used, language, total_tasks_required
                    
                    status values: Accepted | Wrong Answer | Compilation Error | Runtime Error | Time Limit Exceeded | NULL (not submitted)
                    
                    For aggregation: SUM(score), COUNT(status), AVG(score), etc.
                    NEVER select: student_id, assignment_code, source_code.
                    """)
            String selectClause,

            @P("""
                    Clause appended after WHERE assignment_code = ? Leave empty if not needed.
                    Supports: AND ... | GROUP BY ... | HAVING ... | ORDER BY ... | LIMIT ...
                    
                    Examples:
                    AND status = 'Compilation Error' ORDER BY student_name
                    AND order_no = 2 AND language = 'Java'
                    GROUP BY student_name HAVING SUM(score) > 80 ORDER BY SUM(score) DESC
                    AND execution_time IS NOT NULL ORDER BY execution_time ASC LIMIT 1
                    AND score IS NOT NULL ORDER BY score DESC LIMIT 1
                    AND score IS NOT NULL ORDER BY score DESC, CASE WHEN execution_time IS NULL THEN 1 ELSE 0 END, execution_time ASC LIMIT 1
                    GROUP BY student_name, total_tasks_required HAVING COUNT(status) = MAX(total_tasks_required)
                    GROUP BY student_name, total_tasks_required HAVING SUM(CASE WHEN status IS NULL THEN 1 ELSE 0 END) = MAX(total_tasks_required)
                    """)
            String tailClause,

            @P("Assignment code from system context. Always required.")
            String assignmentCode
    ) {
        String upperSelect = selectClause != null ? selectClause.toUpperCase() : "";
        String upperTail = tailClause != null ? tailClause.toUpperCase() : "";

        for (String keyword : BLOCKED_KEYWORDS) {
            if (containsBlockedKeyword(upperSelect, keyword) || containsBlockedKeyword(upperTail, keyword)) {
                log.warn("Blocked dangerous keyword '{}' in query", keyword);
                return "Lỗi bảo mật: Câu truy vấn chứa từ khóa không được phép: " + keyword;
            }
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(selectClause != null && !selectClause.trim().isEmpty() ? selectClause : "*");
        sql.append(" FROM v_assignment_analytics WHERE assignment_code = ?");

        if (tailClause != null && !tailClause.trim().isEmpty()) {
            sql.append(" ").append(tailClause.trim());
        }

        String finalSql = sql.toString();
        log.info("executeQuery: {} | assignmentCode={}", finalSql, assignmentCode);

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(finalSql, assignmentCode);
            if (results.isEmpty()) {
                return "Không tìm thấy dữ liệu nào khớp với yêu cầu.";
            }
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            log.error("executeQuery error: {}", e.getMessage());
            return "Lỗi SQL: " + e.getMessage() + " | SQL: [" + finalSql + "]";
        }
    }

    private boolean containsBlockedKeyword(String clause, String keyword) {
        if (clause == null || clause.isBlank()) {
            return false;
        }
        if (BLOCKED_COMMENT_TOKENS.contains(keyword)) {
            return clause.contains(keyword);
        }
        return Pattern.compile("(?<![A-Z0-9_])" + Pattern.quote(keyword) + "(?![A-Z0-9_])")
                .matcher(clause)
                .find();
    }
}

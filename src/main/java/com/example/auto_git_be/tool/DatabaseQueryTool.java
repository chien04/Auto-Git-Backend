package com.example.auto_git_be.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseQueryTool {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Tool("""
            Query assignment statistics and submission information from the database.
            
            USE THIS TOOL FOR:
            - Scores
            - Submission status
            - Student statistics
            - Error statistics
            - Task lists
            - Performance reports
            
            DO NOT USE THIS TOOL FOR:
            - Reading source code or source_code content
            - Code analysis
            - Algorithm explanation
            
            Use searchStudentCode instead for source code analysis.
            
            IMPORTANT:
            - One student may submit multiple tasks.
            - Each task has an order_no value representing task order:
              1 = task1
              2 = task2
              3 = task3
              etc.
            """)
    public String executeQuery(

            @P("""
                    LIST OF COLUMNS TO SELECT (comma separated).
                    
                    ALWAYS include:
                    - assignment_title
                    - student_name
                    
                    VALID COLUMNS:
                    - assignment_title
                    - student_name
                    - task_name
                    - order_no
                    - score
                    - pass
                    - total
                    - status
                    - error_message
                    - execution_time
                    - memory_used
                    - language
                    
                    NEVER SELECT:
                    - student_id
                    - assignment_code
                    - source_code
                    """)
            String selectColumns,

            @P("""
                    WHERE conditions (WITHOUT the 'WHERE' keyword).
                    
                    Examples:
                    - order_no = 1
                    - status = 'Compilation Error'
                    - language = 'Java'
                    
                    Use order_no to filter specific tasks:
                    - order_no = 1 → task1
                    - order_no = 2 → task2
                    
                    Leave empty if no additional filtering is needed.
                    """)
            String conditions,

            @P("""
                    GROUP BY, ORDER BY, and LIMIT clause if needed.
                    
                    VERY IMPORTANT GROUP BY RULE:
                    In SQL, EVERY non-aggregate column in SELECT
                    MUST also appear in GROUP BY.
                    
                    Correct example:
                    SELECT:
                      assignment_title, student_name, status
                    
                    GROUP BY:
                      assignment_title, student_name, status
                    
                    Incorrect:
                      GROUP BY student_name
                      (missing assignment_title and status)
                    
                    Another example:
                    GROUP BY assignment_title, student_name
                    ORDER BY student_name ASC
                    LIMIT 10
                    
                    Leave empty if not needed.
                    """)
            String groupingAndSorting,

            @P("""
                    Current assignment code.
                    REQUIRED.
                    Used internally for filtering.
                    """)
            String assignmentCode
    ) {

        if (groupingAndSorting != null && groupingAndSorting.toUpperCase().contains("GROUP BY")) {
            groupingAndSorting = fixGroupBy(selectColumns, groupingAndSorting);
        }

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
            return "Lỗi SQL: " + e.getMessage()
                    + "SQL đã chạy: [" + finalSql + "]";
        }
    }

    private String fixGroupBy(String selectColumns, String groupingAndSorting) {
        List<String> selectCols = Arrays.stream(selectColumns.split(","))
                .map(String::trim)
                .filter(col -> !col.toUpperCase().matches(".*(COUNT|SUM|AVG|MAX|MIN)\\s*\\(.*"))
                .collect(Collectors.toList());

        String correctGroupBy = "GROUP BY " + String.join(", ", selectCols);
        String remainder = groupingAndSorting
                .replaceAll("(?i)GROUP\\s+BY\\s+[\\w\\s,]+", "")
                .trim();

        return (correctGroupBy + " " + remainder).trim();
    }
}
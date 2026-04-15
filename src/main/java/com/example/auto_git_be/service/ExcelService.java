package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.repository.StudentAssignmentRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelService {
    private final StudentAssignmentRepository studentAssignmentRepository;

    public void exportStudentPoint(HttpServletResponse response, Long assignmentId) throws IOException {
        List<StudentAssignment> studentAssignments = studentAssignmentRepository.findByAssignmentId(assignmentId);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("Danh_Sách_Điểm", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        try (Workbook workbook = new XSSFWorkbook();
             var outputStream = response.getOutputStream()) {

            Sheet sheet = workbook.createSheet("Danh Sách");

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Tên Sinh Viên");
            headerRow.createCell(1).setCellValue("Điểm");

            int rowNum = 1;
            for (StudentAssignment sv : studentAssignments) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(sv.getStudent().getStudentName());
                row.createCell(1).setCellValue(sv.getScore());
            }
            workbook.write(outputStream);

        } catch (Exception e) {
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().println("{\"error\": \"Lỗi trong quá trình tạo file bằng POI!\"}");
        }


    }
}

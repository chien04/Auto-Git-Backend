package com.example.auto_git_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSubmissionDTO {
    private Long studentId;
    private String studentName;
    private String studentCode;
    private Integer commitCount; // Số lần nộp (Att.)
    private LocalDateTime lastCommitAt; // Lần nộp cuối cùng
    private Double score; // Điểm (0-10)
    private String email;
}

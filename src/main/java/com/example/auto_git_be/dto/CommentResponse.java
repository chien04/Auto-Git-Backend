package com.example.auto_git_be.dto;

import com.example.auto_git_be.model.CommentStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponse {
    private Long id;
    private Long assignmentId;
    private Long studentId;
    private String targetBranch;
    private String studentFilePath;
    private String filePath;
    private Integer startLine;
    private Integer startColumn;
    private Integer endLine;
    private Integer endColumn;
    private String selectedText;
    private String content;
    private Long authorId;
    private String authorName;
    private CommentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

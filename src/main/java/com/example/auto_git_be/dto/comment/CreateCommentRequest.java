package com.example.auto_git_be.dto.comment;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCommentRequest {
    private String assignmentCode;
    private String targetBranch;
    private String studentFilePath;
    private String filePath;
    private Integer startLine;
    private Integer startColumn;
    private Integer endLine;
    private Integer endColumn;
    private String selectedText;
    private String comment;
}


package com.example.auto_git_be.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDTO {
    private String fileName;
    private String fileContent;
    private String hashcode;
    private long taskOrderNo;
}

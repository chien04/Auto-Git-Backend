package com.example.auto_git_be.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileContext {
    private String filename;
    private String fileContent;
    private String hashcode;
}

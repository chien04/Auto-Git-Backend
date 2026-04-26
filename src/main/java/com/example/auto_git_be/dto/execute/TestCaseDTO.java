package com.example.auto_git_be.dto.execute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseDTO {
    private Integer ordinal;
    private String input;
    private String output;
}

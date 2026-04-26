package com.example.auto_git_be.dto.execute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TestResultDTO {
    private int statusId;
    private String statusDescription;
    private String stdin;
    private String stdout;
    private String time;
    private Integer memory;
    private String stderr;
}
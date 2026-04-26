package com.example.auto_git_be.dto.execute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Judge0ResultDTO {

    private int statusId;
    private String stdout;
    private String time;
    private Integer memory;
    private String stderr;
    private StatusDetail status;
}


package com.example.auto_git_be.dto.execute;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Judge0ResultDTO {

    @JsonProperty("status_id")
    private int statusId;
    private String stdout;
    private String time;
    private Integer memory;
    private String stderr;
    private StatusDetail status;

    @JsonProperty("compile_output")
    private String compileOutput;
}


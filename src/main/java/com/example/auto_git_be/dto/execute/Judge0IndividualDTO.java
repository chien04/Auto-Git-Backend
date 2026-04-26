package com.example.auto_git_be.dto.execute;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Judge0IndividualDTO {
    @JsonProperty("source_code")
    private String sourceCode;
    @JsonProperty("language_id")
    private int languageId;
    @JsonProperty("stdin")
    private String stdin;
    @JsonProperty("expected_output")
    private String expectedOutput;
}

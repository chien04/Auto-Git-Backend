package com.example.auto_git_be.dto.execute;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Judge0BatchRequest {
    @JsonProperty("submissions")
    List<Judge0IndividualDTO> submissions;
}

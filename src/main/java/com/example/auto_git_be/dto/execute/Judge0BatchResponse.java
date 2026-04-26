package com.example.auto_git_be.dto.execute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Judge0BatchResponse {
    List<Judge0ResultDTO> submissions;
}

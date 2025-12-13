package com.example.auto_git_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinClassResponse {
    private String repoUrl;
    private String branch;
    private String token;
    private String studentId;
}

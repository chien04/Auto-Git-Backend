package com.example.auto_git_be.dto.assignment;

import lombok.Data;

@Data
public class TaskDTO {
    private Integer OrderNo;
    private String language;
    private Double score;
    private Integer pass;
    private Integer total;
    private String status;
    private String errorMessage;
}

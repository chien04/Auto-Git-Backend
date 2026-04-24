package com.example.auto_git_be.dto.testcase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadTaskZipsRequest {

    private String assignmentCode;
    private List<TaskZipItem> tasks;
}
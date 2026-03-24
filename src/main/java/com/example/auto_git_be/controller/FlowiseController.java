package com.example.auto_git_be.controller;

import com.example.auto_git_be.service.FlowiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/flowise")
@RequiredArgsConstructor
public class FlowiseController {


    private final FlowiseService flowiseService;

    @PostMapping("/analyze-file")
    public ResponseEntity<?> analyzeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("query") String query) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Vui lòng gửi kèm file code/tài liệu.");
        }

        try {
            String result = flowiseService.processFileWithAi(file, query);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}

package com.example.auto_git_be.tool;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface TeacherAiService {

    @SystemMessage("""
            {{basePrompt}}
            """
    )
    TokenStream chat(
            @V("basePrompt") String basePrompt,
            @V("assignmentCode") String assignmentCode,
            @UserMessage String message
    );
}
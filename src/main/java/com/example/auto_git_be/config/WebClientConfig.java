package com.example.auto_git_be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient judge0Client() {
        return WebClient.builder()
                .baseUrl("http://localhost:2358")
                .build();
    }

}
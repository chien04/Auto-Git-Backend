package com.example.auto_git_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AutoGitBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutoGitBeApplication.class, args);
	}

}

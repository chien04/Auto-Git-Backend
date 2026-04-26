package com.example.auto_git_be.utils;

import lombok.Getter;
@Getter
public enum Language {
    JAVA("java", 62),
    PYTHON("python", 71),
    CPP("cpp", 54),
    C("c", 50),
    JAVASCRIPT("javascript", 63);

    private final String name;
    private final int judge0Id;

    Language(String name, int judge0Id) {
        this.name = name;
        this.judge0Id = judge0Id;
    }

    public static int getIdByName(String name) {
        for (Language lang : values()) {
            if (lang.name.equalsIgnoreCase(name)) {
                return lang.judge0Id;
            }
        }
        return PYTHON.judge0Id;
    }
}

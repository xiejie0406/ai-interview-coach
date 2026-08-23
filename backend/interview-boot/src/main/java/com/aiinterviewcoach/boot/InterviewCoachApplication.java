package com.aiinterviewcoach.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.aiinterviewcoach")
@ConfigurationPropertiesScan(basePackages = "com.aiinterviewcoach")
public class InterviewCoachApplication {
    public static void main(String[] args) {
        if (!Boolean.parseBoolean(System.getenv("AI_INTERVIEW_LEGACY_COMPATIBILITY"))) {
            throw new IllegalStateException(
                    "Legacy AI backend is migration compatibility source; start RuoYiApplication instead. "
                            + "Set AI_INTERVIEW_LEGACY_COMPATIBILITY=true only for an explicitly approved compatibility run.");
        }
        SpringApplication.run(InterviewCoachApplication.class, args);
    }
}

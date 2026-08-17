package com.example.backend.config;

import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleGenAiConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiConfiguration.class);

    @Value("${app.gemini.api-key:${GEMINI_API_KEY:${GOOGLE_API_KEY:}}}")
    private String apiKey;

    @Bean
    public Client genAiClient() {
        logger.info("Initializing Google GenAI Client using Gemini API Key authentication...");

        String effectiveKey = apiKey;
        if (effectiveKey == null || effectiveKey.isBlank()) {
            logger.warn("GEMINI_API_KEY environment variable is missing or empty! Initializing Client bean with placeholder API key.");
            effectiveKey = "DUMMY_GEMINI_API_KEY_PLACEHOLDER";
        }

        return Client.builder()
                .apiKey(effectiveKey.trim())
                .build();
    }
}

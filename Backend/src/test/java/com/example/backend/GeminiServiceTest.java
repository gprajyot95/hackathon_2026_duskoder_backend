package com.example.backend;

import com.example.backend.config.AppProperties;
import com.example.backend.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GeminiServiceTest {

    private Client client;
    private AppProperties appProperties;
    private ObjectMapper objectMapper;
    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        client = Client.builder().apiKey("AIzaSyFakeKeyForUnitTests").build();
        appProperties = new AppProperties();
        objectMapper = new ObjectMapper();
        geminiService = new GeminiService(client, appProperties, objectMapper);
    }

    @Test
    void testInvalidOrMissingApiKeyErrorHandling() {
        // Calling generateQuery with fake API key will attempt request to Gemini API and fail gracefully
        RuntimeException exception = Assertions.assertThrows(RuntimeException.class, () -> {
            geminiService.generateQuery("system instruction", "schema metadata", "What is the total count?");
        });

        Assertions.assertNotNull(exception.getMessage());
        System.out.println("Formatted error message: " + exception.getMessage());
        // Verify user-friendly error message is returned and raw API key is never exposed
        Assertions.assertFalse(exception.getMessage().contains("AIzaSyFakeKeyForUnitTests"));
    }
}

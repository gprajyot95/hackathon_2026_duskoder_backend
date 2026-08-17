package com.example.backend.service;

import com.example.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class PromptBuilderService {

    private static final Logger logger = LoggerFactory.getLogger(PromptBuilderService.class);

    private final ResourceLoader resourceLoader;
    private final AppProperties appProperties;
    private String cachedInstructionText;

    public PromptBuilderService(ResourceLoader resourceLoader, AppProperties appProperties) {
        this.resourceLoader = resourceLoader;
        this.appProperties = appProperties;
    }

    /**
     * Loads system instructions from instruction.md resource.
     */
    public synchronized String getSystemInstruction() {
        if (cachedInstructionText != null && !cachedInstructionText.isBlank()) {
            return cachedInstructionText;
        }

        String path = appProperties.getGemini().getInstructionPath();
        try {
            logger.info("Loading system instruction from resource: {}", path);
            Resource resource = resourceLoader.getResource(path);
            try (InputStream inputStream = resource.getInputStream()) {
                cachedInstructionText = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
                logger.info("Successfully loaded instruction.md ({} bytes)", cachedInstructionText.length());
                return cachedInstructionText;
            }
        } catch (Exception e) {
            logger.error("Failed to load system instruction from {}: {}", path, e.getMessage(), e);
            throw new RuntimeException("Could not load system instruction file: " + path, e);
        }
    }

    /**
     * Constructs the full combined text for Gemini.
     */
    public String buildUserPrompt(String schemaMetadata, String userQuestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DATABASE SCHEMA METADATA (FROM CACHE) ===\n");
        sb.append(schemaMetadata != null ? schemaMetadata : "NO_SCHEMA_AVAILABLE").append("\n\n");
        sb.append("=== USER QUESTION ===\n");
        sb.append(userQuestion != null ? userQuestion : "").append("\n\n");
        sb.append("Analyze the schema metadata and user question according to system instructions and return valid JSON.");
        return sb.toString();
    }
}

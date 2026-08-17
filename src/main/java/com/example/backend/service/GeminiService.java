package com.example.backend.service;

import com.example.backend.config.AppProperties;
import com.example.backend.model.GeminiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService implements LlmService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private final Client client;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public GeminiService(Client client,
                         AppProperties appProperties,
                         ObjectMapper objectMapper) {
        this.client = client;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeminiResponse generateResponse(String systemInstruction, String schemaMetadata, String userQuestion) {
        return generateQuery(systemInstruction, schemaMetadata, userQuestion);
    }

    /**
     * Stage 1: Query Generation Mode using Google GenAI Java SDK with Gemini API Key.
     */
    @Override
    public GeminiResponse generateQuery(String systemInstruction, String schemaMetadata, String userQuestion) {
        String model = appProperties.getGemini().getModel();

        logger.info("[STAGE 1: Query Generation] Constructing request for Gemini model '{}'...", model);

        String userPrompt = "=== DATABASE SCHEMA METADATA (FROM CACHE) ===\n" + schemaMetadata + "\n" +
                "=== USER QUESTION ===\n" + userQuestion;

        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .temperature(0.1f);

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            configBuilder.systemInstruction(Content.builder()
                    .parts(Collections.singletonList(Part.fromText(systemInstruction)))
                    .build());
        }

        GenerateContentConfig config = configBuilder.build();

        try {
            logger.info("[STAGE 1: Query Generation] Sending request to Gemini API...");
            long startTime = System.currentTimeMillis();
            GenerateContentResponse response = client.models.generateContent(model, userPrompt, config);
            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("[STAGE 1: Query Generation] Received response from Gemini API in {}ms", elapsed);

            String responseText = response.text();
            if (responseText != null && !responseText.isBlank()) {
                logger.info("--------------------------------------------------");
                logger.info("[STAGE 1: Query Generation] RAW GEMINI RESPONSE");
                logger.info("--------------------------------------------------");
                logger.info("{}", responseText);
                logger.info("--------------------------------------------------");
                return parseGeminiResponseText(responseText);
            } else {
                logger.error("[STAGE 1: Query Generation] Empty response text received from Gemini API");
                throw new RuntimeException("Gemini API returned an empty response.");
            }
        } catch (Exception e) {
            String userFriendlyError = formatGeminiErrorMessage(e);
            logger.error("[STAGE 1: Query Generation] Error calling Gemini API: {}", e.getMessage(), e);
            throw new RuntimeException(userFriendlyError, e);
        }
    }

    /**
     * Stage 2: Response Generation Mode with Comprehensive Diagnostic Logging.
     */
    @Override
    public GeminiResponse generateFormattedResponse(String systemInstruction, String userQuestion, String executedSql, List<Map<String, Object>> queryResults) {
        String model = appProperties.getGemini().getModel();

        logger.info("[STAGE 2 DEBUG] Starting Stage 2 request construction for model '{}'...", model);

        long prepStart = System.currentTimeMillis();

        String staticRuntimeContext = "Current Execution Mode:\n" +
                "Response Generation\n\n" +
                "The SQL query has already been executed.\n\n" +
                "Do NOT generate SQL.\n\n" +
                "Do NOT modify SQL.\n\n" +
                "Do NOT suggest another SQL query.\n\n" +
                "Your responsibility is only to explain the supplied database result according to the response format defined in instruction.md.\n\n" +
                "=== SYSTEM INSTRUCTION ===";

        String safeInstruction = (systemInstruction != null) ? systemInstruction : "";
        String fullSystemInstruction = staticRuntimeContext + "\n" + safeInstruction;

        String safeQuestion = (userQuestion != null) ? userQuestion : "";
        String safeSql = (executedSql != null) ? executedSql : "N/A";

        int rowCount = (queryResults != null) ? queryResults.size() : 0;
        String jsonResultString;
        try {
            jsonResultString = objectMapper.writeValueAsString(queryResults != null ? queryResults : Collections.emptyList());
        } catch (Exception e) {
            logger.error("[STAGE 2 DEBUG] Error serializing query results to JSON: {}", e.getMessage());
            jsonResultString = "[]";
        }

        byte[] jsonBytes = jsonResultString.getBytes(StandardCharsets.UTF_8);
        int jsonCharCount = jsonResultString.length();
        int jsonByteCount = jsonBytes.length;
        int jsonEstimatedTokens = jsonCharCount / 4;

        logger.info("[STAGE 2 DEBUG] Database Result Summary:");
        logger.info("[STAGE 2 DEBUG]   Rows Returned:            {}", rowCount);
        logger.info("[STAGE 2 DEBUG]   Serialized JSON Length:   {} characters", jsonCharCount);
        logger.info("[STAGE 2 DEBUG]   Serialized JSON Size:     {} bytes", jsonByteCount);
        logger.info("[STAGE 2 DEBUG]   Approximate Tokens:       ~{}", jsonEstimatedTokens);

        String userPrompt = "=== ORIGINAL USER QUESTION ===\n" + safeQuestion + "\n" +
                "=== EXECUTED SQL QUERY ===\n" + safeSql + "\n" +
                "=== DATABASE QUERY RESULT (" + rowCount + " rows) ===\n" + jsonResultString;

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .temperature(0.2f)
                .systemInstruction(Content.builder()
                        .parts(Collections.singletonList(Part.fromText(fullSystemInstruction)))
                        .build())
                .build();

        StringBuilder completePromptBuilder = new StringBuilder();
        completePromptBuilder.append("=== SYSTEM INSTRUCTION WITH RUNTIME CONTEXT ===\n")
                .append(fullSystemInstruction)
                .append("\n\n=== USER PROMPT PARTS ===\n")
                .append(userPrompt);

        String completePrompt = completePromptBuilder.toString();

        logger.info("--------------------------------------------------");
        logger.info("[STAGE 2] FINAL PROMPT");
        logger.info("--------------------------------------------------");
        logger.info("{}", completePrompt);
        logger.info("--------------------------------------------------");

        long prepDuration = System.currentTimeMillis() - prepStart;
        logger.info("[STAGE 2 DEBUG] Prompt Prepared ({} ms)", prepDuration);

        logger.info("[STAGE 2 DEBUG] Sending Request to Gemini API...");
        long sdkStart = System.currentTimeMillis();
        try {
            GenerateContentResponse response = client.models.generateContent(model, userPrompt, config);
            long sdkDuration = System.currentTimeMillis() - sdkStart;
            logger.info("[STAGE 2 DEBUG] Response Received from Gemini API in {} ms", sdkDuration);

            String responseText = response.text();
            if (responseText != null && !responseText.isBlank()) {
                logger.info("--------------------------------------------------");
                logger.info("[STAGE 2: Response Generation] RAW GEMINI RESPONSE");
                logger.info("--------------------------------------------------");
                logger.info("{}", responseText);
                logger.info("--------------------------------------------------");
                return parseGeminiResponseText(responseText);
            } else {
                logger.error("[STAGE 2 DEBUG] Empty response text from Gemini API");
                throw new RuntimeException("Gemini API returned an empty response in Stage 2.");
            }
        } catch (Exception e) {
            long sdkDuration = System.currentTimeMillis() - sdkStart;
            String userFriendlyError = formatGeminiErrorMessage(e);
            logger.error("[STAGE 2 DEBUG] Gemini Request Failed after {} ms: {}", sdkDuration, e.getMessage(), e);
            throw new RuntimeException(userFriendlyError, e);
        }
    }

    /**
     * AI Title Generation for Chat Sessions (2-5 words) using Google GenAI SDK with Gemini API Key.
     */
    @Override
    public String generateChatTitle(String firstUserQuestion) {
        if (firstUserQuestion == null || firstUserQuestion.isBlank()) {
            return "New Chat";
        }
        String model = appProperties.getGemini().getModel();

        String systemInstructionText =
                "Generate a concise chat title (2 to 5 words) that best describes the user's request.\n" +
                "Rules:\n" +
                "- Maximum 5 words.\n" +
                "- No punctuation unless necessary.\n" +
                "- No quotes or markdown.\n" +
                "- Professional and easy to scan.\n" +
                "- Do not repeat the full question.\n" +
                "- Return ONLY the title text.";

        String userPrompt = "User Question: " + firstUserQuestion;

        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.3f)
                .systemInstruction(Content.builder()
                        .parts(Collections.singletonList(Part.fromText(systemInstructionText)))
                        .build())
                .build();

        try {
            logger.info("Generating AI chat title via Gemini API for question: '{}'", firstUserQuestion);
            GenerateContentResponse response = client.models.generateContent(model, userPrompt, config);
            String rawTitle = response.text();
            if (rawTitle != null && !rawTitle.isBlank()) {
                String cleanTitle = rawTitle.trim().replaceAll("^[\"']|[\"']$", "").replaceAll("\\n.*", "").trim();
                if (!cleanTitle.isBlank() && cleanTitle.split("\\s+").length <= 8) {
                    logger.info("Generated AI chat title: '{}'", cleanTitle);
                    return cleanTitle;
                }
            }
        } catch (Exception e) {
            logger.warn("AI title generation failed, using fallback: {}", e.getMessage());
        }

        return fallbackTitle(firstUserQuestion);
    }

    private String fallbackTitle(String text) {
        if (text == null || text.isBlank()) return "New Chat";
        String clean = text.replaceAll("[^a-zA-Z0-9\\s]", " ").trim();
        String[] words = clean.split("\\s+");
        StringBuilder sb = new StringBuilder();
        int count = Math.min(words.length, 4);
        for (int i = 0; i < count; i++) {
            if (words[i].length() > 0) {
                String word = words[i].substring(0, 1).toUpperCase() + (words[i].length() > 1 ? words[i].substring(1).toLowerCase() : "");
                sb.append(word).append(i == count - 1 ? "" : " ");
            }
        }
        return sb.length() > 0 ? sb.toString() : "New Chat";
    }

    private GeminiResponse parseGeminiResponseText(String responseText) {
        try {
            String cleanJson = cleanMarkdownCodeBlocks(responseText);
            return objectMapper.readValue(cleanJson, GeminiResponse.class);
        } catch (Exception e) {
            logger.error("Failed to parse Gemini response JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse response from Gemini API: " + e.getMessage(), e);
        }
    }

    private String cleanMarkdownCodeBlocks(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1 && trimmed.endsWith("```")) {
                return trimmed.substring(firstNewline + 1, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    /**
     * Maps raw exceptions to clean, user-friendly error messages without exposing technical stack traces or API keys.
     */
    private String formatGeminiErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "An error occurred while communicating with Gemini API.";
        }
        String msg = throwable.getMessage();
        if (msg == null) {
            msg = throwable.toString();
        }
        String lower = msg.toLowerCase();

        if (lower.contains("429") || lower.contains("resource_exhausted") || lower.contains("quota")) {
            return "Gemini API quota exceeded. Please try again shortly.";
        }
        if (lower.contains("401") || lower.contains("403") || lower.contains("unauthenticated") ||
                (lower.contains("invalid") && (lower.contains("key") || lower.contains("api") || lower.contains("argument")))) {
            return "Invalid or missing Gemini API Key. Please verify your GEMINI_API_KEY environment variable.";
        }
        if (lower.contains("404") || lower.contains("not_found")) {
            return "Configured Gemini model was not found.";
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("504")) {
            return "Gemini API request timed out. Please try again.";
        }
        if (lower.contains("500") || lower.contains("503") || lower.contains("unavailable")) {
            return "Gemini AI service is temporarily unavailable. Please try again shortly.";
        }

        String sanitized = msg.replaceAll("key=[A-Za-z0-9_\\-]+", "key=***")
                             .replaceAll("Bearer\\s+[A-Za-z0-9_\\-\\.]+", "Bearer ***");
        if (sanitized.length() > 150) {
            sanitized = sanitized.substring(0, 150) + "...";
        }
        return "Gemini API error: " + sanitized;
    }
}

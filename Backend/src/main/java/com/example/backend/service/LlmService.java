package com.example.backend.service;

import com.example.backend.model.GeminiResponse;

import java.util.List;
import java.util.Map;

public interface LlmService {

    /**
     * Legacy single-call method.
     */
    GeminiResponse generateResponse(String systemInstruction, String schemaMetadata, String userQuestion);

    /**
     * Stage 1: Query Generation Mode.
     * Determines whether question can be answered from schema metadata alone (type=text) or requires SQL generation (type=query).
     */
    GeminiResponse generateQuery(String systemInstruction, String schemaMetadata, String userQuestion);

    /**
     * Stage 2: Response Generation Mode.
     * Converts executed SQL query results into a human-friendly natural language response according to instruction.md.
     */
    GeminiResponse generateFormattedResponse(String systemInstruction, String userQuestion, String executedSql, List<Map<String, Object>> queryResults);

    /**
     * AI Title Generation for Chat Sessions (2-5 words).
     */
    String generateChatTitle(String firstUserQuestion);
}

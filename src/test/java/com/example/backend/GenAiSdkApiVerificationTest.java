package com.example.backend;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class GenAiSdkApiVerificationTest {

    @Test
    void testSdkGenerateContent() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "DUMMY_GEMINI_API_KEY_PLACEHOLDER";
        }
        Client client = Client.builder()
                .apiKey(apiKey)
                .build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .temperature(0.1f)
                .systemInstruction(Content.builder().parts(Collections.singletonList(Part.fromText("System prompt"))).build())
                .build();

        try {
            GenerateContentResponse response = client.models.generateContent(
                    "gemini-3.6-flash",
                    "Hello Gemini",
                    config
            );
            System.out.println("Text: " + response.text());
        } catch (Exception e) {
            System.out.println("Expected API call response/error: " + e.getMessage());
        }
    }

    @Test
    void testSqlExtractionFromAnswerField() throws Exception {
        String json = "{\n" +
                "  \"type\": \"query\",\n" +
                "  \"requiresDatabase\": true,\n" +
                "  \"confidence\": 0.98,\n" +
                "  \"title\": \"Customers with High-Value Transactions Linked to Open Fraud Cases\",\n" +
                "  \"answer\": \"```sql\\nSELECT DISTINCT c.customer_id, c.full_name FROM customers AS c JOIN accounts AS a ON c.customer_id = a.customer_id WHERE t.amount > 50000 AND fc.status = 'OPEN';\\n```\"\n" +
                "}";

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.example.backend.model.GeminiResponse response = mapper.readValue(json, com.example.backend.model.GeminiResponse.class);

        Assertions.assertTrue(response.getRequiresDatabase());
        Assertions.assertNotNull(response.getEffectiveSql());
        Assertions.assertTrue(response.getEffectiveSql().startsWith("SELECT DISTINCT"));
        System.out.println("Extracted SQL: " + response.getEffectiveSql());
    }

    @Test
    void testEnglishTextWithPrepositionIsIgnored() throws Exception {
        String json = "{\n" +
                "  \"type\": \"text\",\n" +
                "  \"requiresDatabase\": false,\n" +
                "  \"answer\": \"Customers with open fraud cases.\"\n" +
                "}";

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.example.backend.model.GeminiResponse response = mapper.readValue(json, com.example.backend.model.GeminiResponse.class);

        Assertions.assertNull(response.getEffectiveSql());
    }
}

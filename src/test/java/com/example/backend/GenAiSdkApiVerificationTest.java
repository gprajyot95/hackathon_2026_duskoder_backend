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

    @Test
    void testRenderEnvVarsWithoutPasswordFallback() {
        // Scenario 1: User sets SPRING_DATASOURCE_URL (no pass in string) and SPRING_DATASOURCE_USERNAME, but no password env var
        String url = "jdbc:postgresql://ep-dawn-violet-azxq73u3.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require";
        String user = "neondb_owner";
        String pass = null;

        com.example.backend.config.DatabaseConfig.DbCredentials creds = com.example.backend.config.DatabaseConfig.resolveCredentials(url, user, pass);
        Assertions.assertEquals("neondb_owner", creds.getUsername());
        Assertions.assertEquals("npg_8btGxWBV5DCp", creds.getPassword());
        Assertions.assertEquals("jdbc:postgresql://ep-dawn-violet-azxq73u3.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require", creds.getJdbcUrl());
    }

    @Test
    void testEmbeddedCredentialsInUrl() {
        // Scenario 2: Connection string containing embedded user:pass
        String url = "postgresql://neondb_owner:npg_8btGxWBV5DCp@ep-dawn-violet-azxq73u3.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require";

        com.example.backend.config.DatabaseConfig.DbCredentials creds = com.example.backend.config.DatabaseConfig.resolveCredentials(url, null, null);
        Assertions.assertEquals("neondb_owner", creds.getUsername());
        Assertions.assertEquals("npg_8btGxWBV5DCp", creds.getPassword());
        Assertions.assertEquals("jdbc:postgresql://ep-dawn-violet-azxq73u3.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require", creds.getJdbcUrl());
    }

    @Test
    void testExplicitCredentials() {
        // Scenario 3: All 3 explicitly set
        String url = "jdbc:postgresql://customhost:5432/mydb";
        String user = "customuser";
        String pass = "custompass";

        com.example.backend.config.DatabaseConfig.DbCredentials creds = com.example.backend.config.DatabaseConfig.resolveCredentials(url, user, pass);
        Assertions.assertEquals("customuser", creds.getUsername());
        Assertions.assertEquals("custompass", creds.getPassword());
        Assertions.assertEquals("jdbc:postgresql://customhost:5432/mydb", creds.getJdbcUrl());
    }
}

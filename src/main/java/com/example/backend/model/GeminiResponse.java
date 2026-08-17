package com.example.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiResponse {

    @JsonProperty("type")
    private String type; // "text" or "query"

    @JsonProperty("requiresDatabase")
    private Boolean requiresDatabase;

    @JsonProperty("requires_database")
    private Boolean requiresDatabaseSnake;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("response")
    private Object response; // Can be a String or a JSON Object (Map/Node) as per instruction.md

    @JsonProperty("sql")
    private String sql;

    @JsonProperty("generatedQuery")
    private String generatedQuery;

    @JsonProperty("generated_query")
    private String generatedQuerySnake;

    @JsonProperty("intentExplanation")
    private String intentExplanation;

    @JsonProperty("intent_explanation")
    private String intentExplanationSnake;

    @JsonProperty("answer")
    private String answer;

    // Output Format fields defined in instruction.md
    @JsonProperty("title")
    private String title;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("detailedAnswer")
    private String detailedAnswer;

    @JsonProperty("highlights")
    private List<String> highlights;

    @JsonProperty("relatedObjects")
    private Object relatedObjects;

    @JsonProperty("resultSummary")
    private Object resultSummary;

    @JsonProperty("suggestedFollowupQuestions")
    private List<String> suggestedFollowupQuestions;

    @JsonProperty("visualizationHint")
    private String visualizationHint;

    @JsonProperty("visualizationHints")
    private Object visualizationHints;

    public GeminiResponse() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getRequiresDatabase() {
        if (requiresDatabase != null) {
            return requiresDatabase;
        }
        if (requiresDatabaseSnake != null) {
            return requiresDatabaseSnake;
        }
        return "query".equalsIgnoreCase(type) || "QUERY".equalsIgnoreCase(type) || getEffectiveSql() != null;
    }

    public void setRequiresDatabase(Boolean requiresDatabase) {
        this.requiresDatabase = requiresDatabase;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getGeneratedQuery() {
        return generatedQuery;
    }

    public void setGeneratedQuery(String generatedQuery) {
        this.generatedQuery = generatedQuery;
    }

    public String getIntentExplanation() {
        if (intentExplanation != null && !intentExplanation.isBlank()) {
            return intentExplanation;
        }
        return intentExplanationSnake;
    }

    public void setIntentExplanation(String intentExplanation) {
        this.intentExplanation = intentExplanation;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        if (intentExplanation != null && !intentExplanation.isBlank()) {
            return intentExplanation;
        }
        return intentExplanationSnake;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetailedAnswer() {
        return detailedAnswer;
    }

    public void setDetailedAnswer(String detailedAnswer) {
        this.detailedAnswer = detailedAnswer;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }

    public Object getRelatedObjects() {
        return relatedObjects;
    }

    public void setRelatedObjects(Object relatedObjects) {
        this.relatedObjects = relatedObjects;
    }

    public Object getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(Object resultSummary) {
        this.resultSummary = resultSummary;
    }

    public List<String> getSuggestedFollowupQuestions() {
        return suggestedFollowupQuestions;
    }

    public void setSuggestedFollowupQuestions(List<String> suggestedFollowupQuestions) {
        this.suggestedFollowupQuestions = suggestedFollowupQuestions;
    }

    public String getVisualizationHint() {
        if (visualizationHint != null && !visualizationHint.isBlank()) {
            return visualizationHint;
        }
        if (visualizationHints instanceof Map) {
            Object pref = ((Map<?, ?>) visualizationHints).get("preferredView");
            if (pref != null) return pref.toString();
        }
        return null;
    }

    public void setVisualizationHint(String visualizationHint) {
        this.visualizationHint = visualizationHint;
    }

    public Object getVisualizationHints() {
        return visualizationHints;
    }

    public void setVisualizationHints(Object visualizationHints) {
        this.visualizationHints = visualizationHints;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    /**
     * Helper to retrieve the target SQL statement from any field or embedded markdown code block.
     */
    public String getEffectiveSql() {
        // 1. Explicit SQL / generatedQuery fields
        for (String candidate : new String[]{sql, generatedQuery, generatedQuerySnake}) {
            String extracted = extractSqlFromString(candidate);
            if (extracted != null) return extracted;
        }

        // 2. answer / detailedAnswer / summary properties
        for (String candidate : new String[]{answer, detailedAnswer, summary}) {
            String extracted = extractSqlFromString(candidate);
            if (extracted != null) return extracted;
        }

        // 3. String response
        if (response instanceof String) {
            String extracted = extractSqlFromString((String) response);
            if (extracted != null) return extracted;
        }

        // 4. Map response
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            for (String key : new String[]{"sql", "generatedQuery", "generated_query", "query", "answer", "detailedAnswer", "summary"}) {
                if (map.containsKey(key) && map.get(key) != null) {
                    String extracted = extractSqlFromString(map.get(key).toString());
                    if (extracted != null) return extracted;
                }
            }
        }

        return null;
    }

    private String extractSqlFromString(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();

        // 1. Check for Markdown code block: ```sql ... ``` or ``` ... ```
        if (trimmed.contains("```")) {
            int startIdx = trimmed.indexOf("```");
            int endIdx = trimmed.lastIndexOf("```");
            if (endIdx > startIdx) {
                String codeBlock = trimmed.substring(startIdx + 3, endIdx).trim();
                if (codeBlock.toLowerCase().startsWith("sql")) {
                    codeBlock = codeBlock.substring(3).trim();
                }
                String upper = codeBlock.toUpperCase();
                if (!codeBlock.isBlank() && isValidSqlStatement(upper)) {
                    return cleanSqlText(codeBlock);
                }
            }
        }

        // 2. Direct SQL statement check (starts with SELECT, WITH, EXPLAIN, SHOW)
        String upper = trimmed.toUpperCase();
        if (isValidSqlStatement(upper)) {
            return cleanSqlText(trimmed);
        }

        // 3. Embedded SELECT or WITH inside text
        int selectIdx = upper.indexOf("SELECT ");
        int withIdx = findSqlWithKeywordIndex(upper);

        int startPos = -1;
        if (selectIdx != -1 && withIdx != -1) {
            startPos = Math.min(selectIdx, withIdx);
        } else if (selectIdx != -1) {
            startPos = selectIdx;
        } else if (withIdx != -1) {
            startPos = withIdx;
        }

        if (startPos != -1) {
            String extracted = trimmed.substring(startPos).trim();
            extracted = extracted.replaceAll("```.*$", "").trim();
            int semicolonIdx = extracted.indexOf(';');
            if (semicolonIdx != -1) {
                extracted = extracted.substring(0, semicolonIdx + 1).trim();
            }
            if (isValidSqlStatement(extracted.toUpperCase())) {
                return cleanSqlText(extracted);
            }
        }

        return null;
    }

    private boolean isValidSqlStatement(String upperSql) {
        if (upperSql == null || upperSql.isBlank()) return false;
        String trimmedUpper = upperSql.trim();
        if (trimmedUpper.startsWith("SELECT ") || trimmedUpper.startsWith("SELECT\n") || trimmedUpper.startsWith("SELECT\r")) {
            return trimmedUpper.contains(" FROM ");
        }
        if (trimmedUpper.startsWith("WITH ") || trimmedUpper.startsWith("WITH\n") || trimmedUpper.startsWith("WITH\r")) {
            return trimmedUpper.contains(" AS ") && (trimmedUpper.contains("SELECT ") || trimmedUpper.contains("SELECT\n"));
        }
        if (trimmedUpper.startsWith("EXPLAIN ") || trimmedUpper.startsWith("SHOW ")) {
            return true;
        }
        return trimmedUpper.contains("SELECT ") && trimmedUpper.contains(" FROM ");
    }

    private int findSqlWithKeywordIndex(String upperText) {
        if (upperText == null) return -1;
        int idx = upperText.indexOf("WITH ");
        while (idx != -1) {
            String sub = upperText.substring(idx);
            if (sub.contains(" AS ") && sub.contains("SELECT ")) {
                return idx;
            }
            idx = upperText.indexOf("WITH ", idx + 5);
        }
        return -1;
    }

    private String cleanSqlText(String sqlText) {
        if (sqlText == null) return null;
        String clean = sqlText.replaceAll("```.*$", "").trim();
        clean = clean.replaceAll("^```sql\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$", "").trim();
        return clean;
    }

    /**
     * Helper to get the primary text explanation from answer, response object, detailedAnswer, or summary.
     */
    public String getEffectiveAnswer() {
        if (answer != null && !answer.isBlank()) {
            return answer;
        }
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            if (map.containsKey("answer") && map.get("answer") != null) {
                return map.get("answer").toString();
            }
        }
        if (response instanceof String) {
            String strResp = (String) response;
            if (!strResp.isBlank()) return strResp;
        }
        if (detailedAnswer != null && !detailedAnswer.isBlank()) {
            return detailedAnswer;
        }
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        return null;
    }

    /**
     * Helper to extract highlights from either root or response object.
     */
    @SuppressWarnings("unchecked")
    public List<String> getEffectiveHighlights() {
        if (highlights != null && !highlights.isEmpty()) {
            return highlights;
        }
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            if (map.containsKey("highlights") && map.get("highlights") instanceof List) {
                return (List<String>) map.get("highlights");
            }
        }
        return null;
    }
}

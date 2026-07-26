package com.example.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryResultResponse {

    private String type; // "text" or "query"
    private Boolean requiresDatabase;
    private Double confidence;
    private String question;
    
    // Unified Response Contract Fields (instruction.md)
    private String title;
    private String summary;
    private String answer;
    private List<String> highlights;
    private Object relatedObjects;
    private Object resultSummary;
    private List<String> suggestedFollowupQuestions;
    private String visualizationHint;

    // Optional SQL & Data fields (for reference/table visualization)
    private String sql;
    private List<Map<String, Object>> data;
    private Integer rowCount;
    private Long executionTimeMs;
    private String error;

    public QueryResultResponse() {
    }

    public static QueryResultResponse textResponse(String question, GeminiResponse gemini) {
        QueryResultResponse res = new QueryResultResponse();
        res.setType("text");
        res.setRequiresDatabase(false);
        res.setQuestion(question);
        if (gemini != null) {
            res.setConfidence(gemini.getConfidence());
            res.setTitle(gemini.getTitle());
            res.setSummary(gemini.getSummary());
            res.setAnswer(gemini.getEffectiveAnswer());
            res.setHighlights(gemini.getEffectiveHighlights());
            res.setRelatedObjects(gemini.getRelatedObjects());
            res.setResultSummary(gemini.getResultSummary());
            res.setSuggestedFollowupQuestions(gemini.getSuggestedFollowupQuestions());
            res.setVisualizationHint(gemini.getVisualizationHint());
        }
        return res;
    }

    public static QueryResultResponse formattedQueryResponse(String question, String sql, List<Map<String, Object>> data, GeminiResponse formattedGemini, long totalTimeMs) {
        QueryResultResponse res = new QueryResultResponse();
        res.setType("query");
        res.setRequiresDatabase(true);
        res.setQuestion(question);
        res.setSql(sql);
        res.setData(data);
        res.setRowCount(data != null ? data.size() : 0);
        res.setExecutionTimeMs(totalTimeMs);

        if (formattedGemini != null) {
            res.setConfidence(formattedGemini.getConfidence());
            res.setTitle(formattedGemini.getTitle());
            res.setSummary(formattedGemini.getSummary());
            res.setAnswer(formattedGemini.getEffectiveAnswer());
            res.setHighlights(formattedGemini.getEffectiveHighlights());
            res.setRelatedObjects(formattedGemini.getRelatedObjects());
            res.setResultSummary(formattedGemini.getResultSummary());
            res.setSuggestedFollowupQuestions(formattedGemini.getSuggestedFollowupQuestions());
            res.setVisualizationHint(formattedGemini.getVisualizationHint());
        }
        return res;
    }

    public static QueryResultResponse errorResponse(String question, String errorMessage) {
        QueryResultResponse res = new QueryResultResponse();
        res.setQuestion(question);
        res.setError(errorMessage);
        return res;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getRequiresDatabase() {
        return requiresDatabase;
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

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
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
        return visualizationHint;
    }

    public void setVisualizationHint(String visualizationHint) {
        this.visualizationHint = visualizationHint;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

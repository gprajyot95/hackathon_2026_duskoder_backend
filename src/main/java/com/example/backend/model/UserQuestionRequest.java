package com.example.backend.model;

public class UserQuestionRequest {

    private String question;
    private String userId;

    public UserQuestionRequest() {
    }

    public UserQuestionRequest(String question) {
        this.question = question;
    }

    public UserQuestionRequest(String question, String userId) {
        this.question = question;
        this.userId = userId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}

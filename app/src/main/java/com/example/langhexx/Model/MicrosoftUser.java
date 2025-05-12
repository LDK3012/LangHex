package com.example.langhexx.Model;

import java.util.HashMap;
import java.util.Map;

public class MicrosoftUser {
    private String userId;
    private String email;
    private String displayName;
    private String writingExerciseId;
    private String writingLevelName;
    private String writingTopicTitle;
    private String writingUserAnswer;
    private long writingTimestamp;
    private String writingFeedbackSummary;

    public MicrosoftUser() {
        //
    }

    public MicrosoftUser(String userId, String email, String displayName) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
    }

    // Getters
    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getWritingExerciseId() {
        return writingExerciseId;
    }

    public String getWritingLevelName() {
        return writingLevelName;
    }

    public String getWritingTopicTitle() {
        return writingTopicTitle;
    }

    public String getWritingUserAnswer() {
        return writingUserAnswer;
    }

    public long getWritingTimestamp() {
        return writingTimestamp;
    }

    public String getWritingFeedbackSummary() {
        return writingFeedbackSummary;
    }

    // Setters
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setWritingExerciseId(String writingExerciseId) {
        this.writingExerciseId = writingExerciseId;
    }

    public void setWritingLevelName(String writingLevelName) {
        this.writingLevelName = writingLevelName;
    }

    public void setWritingTopicTitle(String writingTopicTitle) {
        this.writingTopicTitle = writingTopicTitle;
    }

    public void setWritingUserAnswer(String writingUserAnswer) {
        this.writingUserAnswer = writingUserAnswer;
    }

    public void setWritingTimestamp(long writingTimestamp) {
        this.writingTimestamp = writingTimestamp;
    }

    public void setWritingFeedbackSummary(String writingFeedbackSummary) {
        this.writingFeedbackSummary = writingFeedbackSummary;
    }

    // Method to generate a map for saving writing answer data to Firebase
    public Map<String, Object> toMapForWritingAnswer() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("exerciseId", this.writingExerciseId);
        result.put("levelName", this.writingLevelName);
        result.put("topicTitle", this.writingTopicTitle);
        result.put("userAnswer", this.writingUserAnswer);
        result.put("timestamp", this.writingTimestamp);
        result.put("feedbackSummary", this.writingFeedbackSummary);
        return result;
    }
}
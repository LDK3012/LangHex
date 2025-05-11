package com.example.langhexx.Model;

import java.util.HashMap;
import java.util.Map;

public class UserWritingAnswer {
    private String exerciseId;
    private String levelName;
    private String topicTitle;
    private String userAnswer;
    private long timestamp;
    private String feedbackSummary;

    public UserWritingAnswer() {
        // Default constructor
    }

    public UserWritingAnswer(String exerciseId, String levelName, String topicTitle, String userAnswer, long timestamp, String feedbackSummary) {
        this.exerciseId = exerciseId;
        this.levelName = levelName;
        this.topicTitle = topicTitle;
        this.userAnswer = userAnswer;
        this.timestamp = timestamp;
        this.feedbackSummary = feedbackSummary;
    }

    // Getters
    public String getExerciseId() { return exerciseId; }
    public String getLevelName() { return levelName; }
    public String getTopicTitle() { return topicTitle; }
    public String getUserAnswer() { return userAnswer; }
    public long getTimestamp() { return timestamp; }
    public String getFeedbackSummary() { return feedbackSummary; }

    // Setters
    public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }
    public void setLevelName(String levelName) { this.levelName = levelName; }
    public void setTopicTitle(String topicTitle) { this.topicTitle = topicTitle; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setFeedbackSummary(String feedbackSummary) { this.feedbackSummary = feedbackSummary; }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("exerciseId", exerciseId);
        result.put("levelName", levelName);
        result.put("topicTitle", topicTitle);
        result.put("userAnswer", userAnswer);
        result.put("timestamp", timestamp);
        result.put("feedbackSummary", feedbackSummary);
        return result;
    }
}
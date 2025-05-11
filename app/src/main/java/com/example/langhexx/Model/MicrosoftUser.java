package com.example.langhexx.Model;

import java.util.HashMap;
import java.util.Map;

public class MicrosoftUser {
    private String userId;
    private String email;
    private String displayName;

    // Fields migrated from UserWritingAnswer, specific to a writing exercise context
    private String writingExerciseId;
    private String writingLevelName;
    private String writingTopicTitle;
    private String writingUserAnswer;
    private long writingTimestamp;
    private String writingFeedbackSummary;

    public MicrosoftUser() {
        // Default constructor required for Firebase
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
        // Note: exerciseId for the path is usually determined by the controller (e.g. exerciseTitle)
        // The writingExerciseId stored here is for record-keeping within the object if needed.
        // When saving to Firebase path like .../WritingAnswers/{exerciseTitle},
        // the exerciseTitle itself acts as the key.
        // The map should contain the fields as they are expected in the DB node.
        result.put("exerciseId", this.writingExerciseId); // or a more specific context like currentWritingExercise.getId()
        result.put("levelName", this.writingLevelName);
        result.put("topicTitle", this.writingTopicTitle);
        result.put("userAnswer", this.writingUserAnswer);
        result.put("timestamp", this.writingTimestamp);
        result.put("feedbackSummary", this.writingFeedbackSummary);
        return result;
    }
}
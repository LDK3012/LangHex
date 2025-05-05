package com.example.langhexx.Model;

import java.util.HashMap;
import java.util.Map;

// *** Renamed class ***
public class ReadingQuestion {
    private String id;
    private String questionText;
    private Map<String, String> options; // Key: "A", "B", "C", "D"; Value: Text of option
    private String correctAnswer; // Key of the correct answer, e.g., "B"
    // Keep track of user's selection within the adapter is generally better for list views
    // private int selectedOptionId = -1;
    // private String questionNumberText; // Usually handled by adapter position

    public ReadingQuestion() {
        this.options = new HashMap<>();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    // Remove selectedOptionId and questionNumberText if handled by adapter
    // public int getSelectedOptionId() { return selectedOptionId; }
    // public void setSelectedOptionId(int selectedOptionId) { this.selectedOptionId = selectedOptionId; }
    // public String getQuestionNumberText() { return questionNumberText; }
    // public void setQuestionNumberText(String questionNumberText) { this.questionNumberText = questionNumberText; }
}
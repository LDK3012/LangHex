package com.example.langhexx.Model;

import java.util.HashMap;
import java.util.Map;

public class ReadingQuestion {
    private String id;
    private String questionText;
    private Map<String, String> options; // Key: "A", "B", "C", "D"; Value: Text of option
    private String correctAnswer;

    public ReadingQuestion() {
        this.options = new HashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

}
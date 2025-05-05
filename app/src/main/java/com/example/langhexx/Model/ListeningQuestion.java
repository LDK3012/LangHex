package com.example.langhexx.Model; // Hoặc package phù hợp

import java.util.HashMap;
import java.util.Map;

public class ListeningQuestion {
    private String id;
    private String questionText;
    private Map<String, String> options; // Key: "A", "B", "C", "D"; Value: Text của option
    private String correctAnswer; // Key của đáp án đúng, ví dụ: "B"
    private int selectedOptionId = -1; // Lưu ID của RadioButton được chọn (-1 là chưa chọn)
    private String questionNumberText; // Thêm nếu bạn muốn hiển thị "Question 1:", "Question 2:",...

    public ListeningQuestion() {
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

    public int getSelectedOptionId() { return selectedOptionId; }
    public void setSelectedOptionId(int selectedOptionId) { this.selectedOptionId = selectedOptionId; }

    public String getQuestionNumberText() { return questionNumberText; }
    public void setQuestionNumberText(String questionNumberText) { this.questionNumberText = questionNumberText; }
}
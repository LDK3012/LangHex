package vn.enghexteam.enghex.Model;

import java.util.HashMap;
import java.util.Map;

public class ListeningQuestion {
    private String id;
    private String questionText;
    private Map<String, String> options;
    private String correctAnswer;
    private int selectedOptionId = -1;
    private int initialSelectedOptionId = -1;
    private String questionNumberText;

    public ListeningQuestion() {
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

    public int getSelectedOptionId() { return selectedOptionId; }
    public void setSelectedOptionId(int selectedOptionId) { this.selectedOptionId = selectedOptionId; }

    public int getInitialSelectedOptionId() { return initialSelectedOptionId; }
    public void setInitialSelectedOptionId(int initialSelectedOptionId) { this.initialSelectedOptionId = initialSelectedOptionId; }

    public String getQuestionNumberText() { return questionNumberText; }
    public void setQuestionNumberText(String questionNumberText) { this.questionNumberText = questionNumberText; }
}
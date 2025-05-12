package com.example.langhexx.Model;

public class WritingExercise {
    private String id; // Firebase key for the exercise
    private String title; // Display title of the exercise
    private String script;

    public WritingExercise() {
        // Default constructor
    }

    public WritingExercise(String id, String title, String script) {
        this.id = id;
        this.title = title;
        this.script = script;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }
}

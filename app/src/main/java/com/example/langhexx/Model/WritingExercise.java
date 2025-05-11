package com.example.langhexx.Model;

public class WritingExercise {
    private String id;
    private String title;
    private String script;

    public WritingExercise() {

    }

    public WritingExercise(String id, String title, String script) {
        this.id = id;
        this.title = title;
        this.script = script;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getScript() {
        return script;
    }

    // Setters (optional, depending on how you create the objects)
    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setScript(String script) {
        this.script = script;
    }
}
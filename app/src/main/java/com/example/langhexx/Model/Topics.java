package com.example.langhexx.Model;

public class Topics {
    private String title ;
    public Topics() {
        // Required for Firebase
    }
    public Topics(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

}

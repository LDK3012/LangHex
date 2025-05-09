package com.example.langhexx.Model;

public class ErrorDetail {
    public final String errorText;
    public final int startIndex;
    public final int endIndex;
    public final String type; // "spelling", "grammar", "vocabulary"
    public final String suggestion;

    public ErrorDetail(String errorText, int startIndex, int endIndex, String type, String suggestion) {
        this.errorText = errorText;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.type = type;
        this.suggestion = suggestion;
    }
}
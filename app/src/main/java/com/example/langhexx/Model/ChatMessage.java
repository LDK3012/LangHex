package com.example.langhexx.Model;

public class ChatMessage {
    public static final int SENDER_USER = 0;
    public static final int SENDER_AI = 1;
    private String message;
    private int senderType; // 0 = user, 1 = bot

    public ChatMessage(String message, int senderType) {
        this.message = message;
        this.senderType = senderType;
    }

    public String getMessage() {
        return message;
    }

    public int getSenderType() {
        return senderType;
    }
}

package com.example.langhexx.Model;

public class MicrosoftUser {
    private String userId; // Microsoft User ID
    private String email;
    private String displayName;
    // Thêm các trường khác nếu cần, ví dụ: avatarUrl

    public MicrosoftUser() {
        // Default constructor required for calls to DataSnapshot.getValue(MicrosoftUser.class)
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

    // Setters (tùy chọn)
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
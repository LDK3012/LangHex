package com.example.langhexx.Model;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.common.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ChatHistoryManager {
    private static final String PREF_NAME = "chat_history";
    private static final String KEY_MESSAGES = "messages";

    public static void saveChatHistory(
        Context context, List<ChatMessage> messages) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String json = new Gson().toJson(messages);
        editor.putString(KEY_MESSAGES, json);
        editor.apply();
    }

    public static List<ChatMessage> loadChatHistory(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_MESSAGES, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<ChatMessage>>() {}.getType();
        return new Gson().fromJson(json, type);
    }

    public static void clearChatHistory(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_MESSAGES).apply();
    }
}

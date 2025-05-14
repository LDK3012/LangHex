package com.example.langhexx.Model;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.RequestQueue;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class SpeakingVoiceModel implements SpeakingContract.Model {
    private static final String TAG = "SpeakingPronunModel";
    private static final String FIREBASE_DB_URL = "https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private ArrayList<String> scriptList = new ArrayList<>();
    private int currentScriptIndex = 0;
    private RequestQueue requestQueue;
    private Context appContext;

    public SpeakingVoiceModel(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void loadQuestions(String levelName, String topicId, SpeakingContract.QuestionListener listener) {
        if (topicId == null || topicId.isEmpty()) {
            listener.onQuestionLoadError("Missing Topic ID.");
            return;
        }

        Log.d(TAG, "Loading scripts for Pronunciation Topic ID: " + topicId + " under Level: " + levelName);
        DatabaseReference topicRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Speaking")
                .child("Pronunciation")
                .child("Topics")
                .child(topicId);

        topicRef.child("scripts").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                scriptList.clear();
                currentScriptIndex = 0;
                if (snapshot.exists() && snapshot.hasChildren()) {
                    for (DataSnapshot scriptSnap : snapshot.getChildren()) {
                        String script = scriptSnap.getValue(String.class);
                        if (script != null && !script.trim().isEmpty()) {
                            scriptList.add(script);
                            Log.d(TAG, "Loaded script: " + script);
                        }
                    }
                    Log.i(TAG, "Total scripts loaded: " + scriptList.size());
                } else {
                    Log.w(TAG, "No 'scripts' node found or it's empty in Firebase for this topicId: " + topicId);
                }

                if (!scriptList.isEmpty()) {
                    listener.onQuestionsLoaded(new ArrayList<>(scriptList));
                } else {
                    listener.onQuestionLoadError("No scripts found for this pronunciation topic.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase data loading cancelled: " + error.getMessage(), error.toException());
                listener.onQuestionLoadError("Firebase data loading error: " + error.getMessage());
            }
        });
    }

    /**
     * Phương thức này có thể cần định nghĩa lại hoàn toàn cho việc đánh giá phát âm.
     * Hiện tại để trống hoặc trả về mặc định vì không có logic đánh giá phát âm.
     */
    @Override
    public void evaluateAnswer(String script, String userAnswerRecordingPath, SpeakingContract.EvaluationListener listener) {
        Log.d(TAG, "evaluateAnswer called for Pronunciation. Script: " + script + ", User Answer Path: " + userAnswerRecordingPath);
        // TODO: Implement pronunciation evaluation logic if needed.
        listener.onEvaluationError(userAnswerRecordingPath, "Pronunciation evaluation not implemented.");
    }

    @Override
    public String getCurrentQuestion() {
        if (scriptList != null && currentScriptIndex < scriptList.size()) {
            return scriptList.get(currentScriptIndex);
        }
        return null;
    }

    @Override
    public int getCurrentQuestionIndex() { // Sẽ là script index
        return currentScriptIndex;
    }

    @Override
    public int getQuestionCount() { // Sẽ là tổng số scripts
        return scriptList != null ? scriptList.size() : 0;
    }

    @Override
    public void advanceQuestionIndex() {
        if (currentScriptIndex < getQuestionCount()) {
            currentScriptIndex++;
        }
    }

    @Override
    public void cleanup() {
        if (requestQueue != null) {
            Log.d(TAG, "Cancelling requests with tag: " + TAG);
            requestQueue.cancelAll(TAG);
        }
    }
}
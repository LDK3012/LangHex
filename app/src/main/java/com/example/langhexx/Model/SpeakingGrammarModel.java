package com.example.langhexx.Model;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;


import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.langhexx.BuildConfig;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SpeakingGrammarModel implements SpeakingContract.Model {
    private static final String TAG = "SpeakingGrammarModel";
    private static final String OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY;
    private static final String FIREBASE_DB_URL = "https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private ArrayList<String> questionList = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private RequestQueue requestQueue;

    public SpeakingGrammarModel(Context context) {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    @Override
    public void loadQuestions(String levelName, String identifier, SpeakingContract.QuestionListener listener) {
        if (identifier == null || identifier.isEmpty()) {
            if (listener != null) listener.onQuestionLoadError("Missing Topic ID (identifier).");
            Log.e(TAG, "loadQuestions error: Missing Topic ID (identifier).");
            return;
        }
        if (levelName == null || levelName.isEmpty()) {
            if (listener != null) listener.onQuestionLoadError("Missing Level Name.");
            Log.e(TAG, "loadQuestions error: Missing Level Name.");
            return;
        }

        Log.d(TAG, "Loading questions for Level: " + levelName + ", Topic ID (Identifier): " + identifier);
        DatabaseReference topicRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Speaking")
                .child("Q&A")
                .child("Topics")
                .child(identifier);

        topicRef.child("questions").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                questionList.clear();
                currentQuestionIndex = 0;
                if (snapshot.exists() && snapshot.hasChildren()) {
                    for (DataSnapshot questionSnap : snapshot.getChildren()) {
                        String question = questionSnap.getValue(String.class);
                        if (question != null && !question.trim().isEmpty()) {
                            questionList.add(question);
                        }
                    }
                    Log.i(TAG, "Total questions loaded: " + questionList.size());
                } else {
                    Log.w(TAG, "No 'questions' node found or it's empty in Firebase for topicId: " + identifier);
                }

                if (listener != null) {
                    if (!questionList.isEmpty()) {
                        listener.onQuestionsLoaded(new ArrayList<>(questionList));
                    } else {
                        listener.onQuestionLoadError("No questions found for this topic.");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase data loading cancelled: " + error.getMessage(), error.toException());
                if (listener != null) {
                    listener.onQuestionLoadError("Firebase data loading error: " + error.getMessage());
                }
            }
        });
    }

    @Override
    public void evaluateAnswer(String question, String userAnswer, SpeakingContract.EvaluationListener listener) {
        Log.d(TAG, "Evaluating with OpenAI. Question: \"" + question + "\", Answer: \"" + userAnswer + "\"");
        if (userAnswer == null || userAnswer.trim().isEmpty()) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (listener != null) {
                    listener.onEvaluationSuccess(userAnswer, false, "Please provide an answer.", "Please type your answer to the question.");
                }
            });
            return;
        }
        String promptText = String.format(
                "You are an English teacher evaluating a student's short answer. "
                        + "Given the following question and student's answer, "
                        + "please reply ONLY with a JSON object with the following fields:\n"
                        + "1. is_correct: boolean\n"
                        + "2. feedback_en: string (English explanation, short)\n"
                        + "3. suggested_answer_en: string (English, only if is_correct is false)\n"
                        + "DO NOT add any extra explanation or text outside JSON.\n\n"
                        + "Question: \"%s\"\n"
                        + "User's Answer: \"%s\"\n"
                        + "Examples:\n"
                        + "{\n"
                        + "  \"is_correct\": false,\n"
                        + "  \"feedback_en\": \"There's a grammar error with the verb tense.\",\n"
                        + "  \"suggested_answer_en\": \"I went to the park yesterday.\"\n"
                        + "}\n"
                        + "{\n"
                        + "  \"is_correct\": true,\n"
                        + "  \"feedback_en\": \"Correct answer!\",\n"
                        + "  \"suggested_answer_en\": \"\"\n"
                        + "}\n",
                question, userAnswer);

        OkHttpClient client = new OkHttpClient();
        JSONObject reqBody = new JSONObject();
        try {
            reqBody.put("model", "gpt-4.1");
            JSONArray messagesArr = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are an English language evaluation assistant. Only return pure JSON, no extra text.");
            messagesArr.put(systemMsg);
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", promptText);
            messagesArr.put(userMsg);
            reqBody.put("messages", messagesArr);
            reqBody.put("temperature", 0.2);
            reqBody.put("max_tokens", 512);
        } catch (JSONException e) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (listener != null) listener.onEvaluationError(userAnswer, "Error creating OpenAI request.");
            });
            return;
        }

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), reqBody.toString());

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (listener != null) listener.onEvaluationError(userAnswer, "OpenAI API connection error.");
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseStr = response.body() != null ? response.body().string() : null;
                if (response.body() != null) response.body().close();
                if (response.isSuccessful() && responseStr != null) {
                    try {
                        Log.d(TAG, "OpenAI raw response: " + responseStr); // debug log
                        JSONObject jsonResponse = new JSONObject(responseStr);
                        JSONArray choices = jsonResponse.getJSONArray("choices");
                        JSONObject messageObj = choices.getJSONObject(0).getJSONObject("message");
                        String aiContent = messageObj.getString("content").trim();
                        Log.d(TAG, "OpenAI aiContent: " + aiContent); // debug log
                        int idxStart = aiContent.indexOf("{");
                        int idxEnd = aiContent.lastIndexOf("}");
                        if (idxStart < 0 || idxEnd <= idxStart) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                if (listener != null)
                                    listener.onEvaluationError(userAnswer, "OpenAI trả về sai định dạng, không tìm thấy JSON hợp lệ.");
                            });
                            return;
                        }
                        String jsonOnly = aiContent.substring(idxStart, idxEnd + 1);
                        Log.d(TAG, "OpenAI jsonOnly: " + jsonOnly); // debug log
                        JSONObject evalResult = new JSONObject(jsonOnly);
                        boolean isCorrect = evalResult.optBoolean("is_correct", false);
                        String feedbackEn = evalResult.optString("feedback_en", "");
                        String suggestionEn = evalResult.optString("suggested_answer_en", "");
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (listener != null)
                                listener.onEvaluationSuccess(userAnswer, isCorrect, feedbackEn, suggestionEn);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Lỗi parse JSON OpenAI", e);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (listener != null)
                                listener.onEvaluationError(userAnswer, "Lỗi parse JSON: " + e.getMessage());
                        });
                    }
                } else {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (listener != null)
                            listener.onEvaluationError(userAnswer, "Invalid response from OpenAI (Code: " + response.code() + ")");
                    });
                }
            }
        });
    }



    @Override
    public String getCurrentQuestion() {
        if (questionList != null && !questionList.isEmpty() && currentQuestionIndex >= 0 && currentQuestionIndex < questionList.size()) {
            return questionList.get(currentQuestionIndex);
        }
        return null;
    }

    @Override
    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    @Override
    public int getQuestionCount() {
        return questionList != null ? questionList.size() : 0;
    }

    @Override
    public void advanceQuestionIndex() {
        if (currentQuestionIndex < (getQuestionCount() - 1)) {
            currentQuestionIndex++;
        } else {
            Log.d(TAG, "Already at the last question or no more questions.");
        }
    }

    @Override
    public void previousQuestionIndex() {
        if (currentQuestionIndex > 0) {
            currentQuestionIndex--;
            Log.d(TAG, "Moved to previous question index: " + currentQuestionIndex);
        } else {
            Log.d(TAG, "Already at the first question.");
        }
    }

    @Override
    public void cleanup() {
        if (requestQueue != null) {
            requestQueue.cancelAll(TAG);
        }
        if (questionList != null) {
            questionList.clear();
        }
        currentQuestionIndex = 0;
        Log.d(TAG, "SpeakingGrammarModel cleaned up.");
    }
}
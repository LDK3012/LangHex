package com.example.langhexx.Model;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
public class SpeakingGrammarModel implements SpeakingContract.Model{
    private static final String TAG = "SpeakingModel";
    private static final String GEMINI_API_KEY = "AIzaSyDoQKvSTwu_RJMIKl3c456iLFW0oIK16tc"; // Ensure this key is kept secure
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;
    private static final String FIREBASE_DB_URL = "https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private ArrayList<String> questionList = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private RequestQueue requestQueue;
    private Context appContext;

    public SpeakingGrammarModel(Context context) {
        this.appContext = context.getApplicationContext();
        requestQueue = Volley.newRequestQueue(appContext);
    }

    @Override
    public void loadQuestions(String levelName, String topicId, SpeakingContract.QuestionListener listener) {
        // levelName is not used in the new path for questions, but topicId (formerly topicTitle) is crucial.
        if (topicId == null || topicId.isEmpty()) {
            listener.onQuestionLoadError("Missing Topic ID.");
            return;
        }

        Log.d(TAG, "Loading questions for Topic ID: " + topicId);
        DatabaseReference topicRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Speaking")
                .child("Q&A")
                .child("Topics")
                .child(topicId);

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
                            Log.d(TAG, "Loaded question: " + question);
                        }
                    }
                    Log.i(TAG, "Total questions loaded: " + questionList.size());
                } else {
                    Log.w(TAG, "No 'questions' node found or it's empty in Firebase for this topicId: " + topicId);
                }

                if (!questionList.isEmpty()) {
                    listener.onQuestionsLoaded(new ArrayList<>(questionList));
                } else {
                    listener.onQuestionLoadError("No questions found for this topic.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase data loading cancelled: " + error.getMessage(), error.toException());
                listener.onQuestionLoadError("Firebase data loading error: " + error.getMessage());
            }
        });
    }

    @Override
    public void evaluateAnswer(String question, String userAnswer, SpeakingContract.EvaluationListener listener) {
        Log.d(TAG, "Evaluating with Gemini. Question: " + question + ", Answer: " + userAnswer);
        String promptText = String.format(
                "Analyze the following user's answer in response to the given question within a language learning context.\n" +
                        "Question: \"%s\"\n" +
                        "User's Answer: \"%s\"\n\n" +
                        "Respond ONLY with a JSON object containing these fields:\n" +
                        "1. 'is_correct': boolean (true if the answer is grammatically correct, relevant to the question's topic, and logically coherent. The answer is considered correct even if it answers the core question and provides additional relevant information, instead of just the most direct minimal answer. For example, if the question is 'Do you like ice cream?' and the answer is 'No, I like chocolate', this IS correct. Set to false ONLY if there are significant grammatical errors, the answer is completely irrelevant, or logically nonsensical).\n" +
                        "2. 'feedback_vi': string (If 'is_correct' is false, provide a BRIEF explanation *in Vietnamese* identifying the main issue - e.g., grammar error type, vocabulary choice, relevance, logic. If 'is_correct' is true, this can be empty OR provide minor suggestions/positive feedback *in Vietnamese*, e.g., 'Câu trả lời đúng và tự nhiên!' or 'Câu trả lời đúng rồi, bạn có thể nói đầy đủ hơn là \"No, I don\\'t like ice cream, but I like chocolate.\" nếu muốn.').\n" +
                        "3. 'suggested_answer_en': string (Provide a well-formed, correct alternative answer *in English* ONLY if the user's answer is incorrect ('is_correct': false). Leave empty if the user's answer is correct).\n\n" +
                        "Example of expected JSON if incorrect (grammar error):\n" +
                        "{\n" +
                        "  \"is_correct\": false,\n" +
                        "  \"feedback_vi\": \"Câu trả lời có lỗi ngữ pháp về thì của động từ.\",\n" +
                        "  \"suggested_answer_en\": \"I went to the park yesterday.\"\n" +
                        "}\n\n" +
                        "Example of expected JSON if correct but indirect (like the ice cream example):\n" +
                        "{\n" +
                        "  \"is_correct\": true,\n" +
                        "  \"feedback_vi\": \"Câu trả lời tốt! Bạn đã trả lời đúng trọng tâm và mở rộng thêm thông tin liên quan.\",\n" +
                        "  \"suggested_answer_en\": \"\"\n" +
                        "}\n\n" +
                        "Example of expected JSON if correct and direct:\n" +
                        "{\n" +
                        "  \"is_correct\": true,\n" +
                        "  \"feedback_vi\": \"\",\n" +
                        "  \"suggested_answer_en\": \"\"\n" +
                        "}",
                question, userAnswer
        );

        JSONObject requestBody = new JSONObject();
        try {
            JSONArray contentsArray = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray partsArray = new JSONArray();
            JSONObject part = new JSONObject();

            part.put("text", promptText);
            partsArray.put(part);
            content.put("parts", partsArray);
            contentsArray.put(content);
            requestBody.put("contents", contentsArray);

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("response_mime_type", "application/json");
            requestBody.put("generationConfig", generationConfig);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON request body", e);
            listener.onEvaluationError(userAnswer, "Error creating JSON request");
            return;
        }

        Log.d(TAG, "Gemini Request Body: " + requestBody.toString());

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, GEMINI_API_URL, requestBody,
                response -> {
                    Log.d(TAG, "Gemini API Full Response: " + response.toString());
                    try {
                        JSONArray candidates = response.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject firstCandidate = candidates.getJSONObject(0);
                            JSONArray safetyRatings = firstCandidate.optJSONArray("safetyRatings");
                            if (safetyRatings != null) {
                                for (int i = 0; i < safetyRatings.length(); i++) {
                                    JSONObject rating = safetyRatings.getJSONObject(i);
                                    String probability = rating.optString("probability", "UNKNOWN");
                                    if (!probability.equals("NEGLIGIBLE") && !probability.equals("LOW")) {
                                        String category = rating.optString("category", "UNKNOWN");
                                        Log.w(TAG, "Content blocked by safety filter. Category: " + category + ", Probability: " + probability);
                                        listener.onEvaluationError(userAnswer, "Content unsuitable (blocked by safety filter)");
                                        return;
                                    }
                                }
                            }

                            JSONObject content = firstCandidate.optJSONObject("content");
                            if (content != null) {
                                JSONArray parts = content.optJSONArray("parts");
                                if (parts != null && parts.length() > 0) {
                                    String jsonResponseString = parts.getJSONObject(0).optString("text", "");
                                    if (!jsonResponseString.isEmpty()) {
                                        Log.d(TAG, "Gemini JSON Response String (from parts): " + jsonResponseString);
                                        try {
                                            JSONObject evaluationResult = new JSONObject(jsonResponseString);
                                            boolean isCorrect = evaluationResult.optBoolean("is_correct", false);
                                            String feedbackVi = evaluationResult.optString("feedback_vi", "");
                                            String suggestionEn = evaluationResult.optString("suggested_answer_en", "");
                                            listener.onEvaluationSuccess(userAnswer, isCorrect, feedbackVi, suggestionEn);
                                        } catch (JSONException jsonEx) {
                                            Log.e(TAG, "Error parsing the JSON string within 'parts'", jsonEx);
                                            listener.onEvaluationError(userAnswer, "Error parsing JSON response structure");
                                        }
                                    } else {
                                        listener.onEvaluationError(userAnswer,"'text' part in API response is empty");
                                    }
                                } else {
                                    listener.onEvaluationError(userAnswer,"'parts' in API response is empty or invalid");
                                }
                            } else {
                                String finishReason = firstCandidate.optString("finishReason", "UNKNOWN");
                                Log.w(TAG,"Candidate finished with reason: " + finishReason);
                                listener.onEvaluationError(userAnswer, "API response missing valid 'content' (Reason: "+finishReason+")");
                            }
                        } else {
                            JSONObject promptFeedback = response.optJSONObject("promptFeedback");
                            if (promptFeedback != null) {
                                String blockReason = promptFeedback.optString("blockReason", "unknown");
                                Log.e(TAG, "Gemini prompt blocked. Reason: " + blockReason);
                                listener.onEvaluationError(userAnswer,"Request blocked (Reason: " + blockReason + ")");
                            } else {
                                Log.e(TAG,"Gemini response missing 'candidates' and 'promptFeedback'. Response: " + response);
                                listener.onEvaluationError(userAnswer,"API response missing valid 'candidates'");
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing top-level Gemini JSON response", e);
                        listener.onEvaluationError(userAnswer, "Error parsing overall JSON response");
                    }
                },
                error -> {
                    String errorMsg = "Network error or undefined API error";
                    int statusCode = -1;
                    String responseData = "";
                    if (error.networkResponse != null) {
                        statusCode = error.networkResponse.statusCode;
                        if (error.networkResponse.data != null) {
                            try {
                                responseData = new String(error.networkResponse.data, "UTF-8");
                                Log.e(TAG, "Gemini API Error Response (Raw Data): " + responseData);
                                try {
                                    JSONObject errorJson = new JSONObject(responseData);
                                    JSONObject errorDetail = errorJson.optJSONObject("error");
                                    if (errorDetail != null) {
                                        errorMsg = errorDetail.optString("message", responseData);
                                    } else {
                                        errorMsg = responseData;
                                    }
                                } catch (JSONException e) {
                                    errorMsg = responseData;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error decoding network error response data", e);
                                errorMsg = "Error reading network response.";
                            }
                        }
                        errorMsg = "API Error (Code: " + statusCode + "): " + errorMsg;
                    } else {
                        errorMsg = "Connection error or timeout.";
                        Log.e(TAG, "Volley Error (No Network Response): " + error.toString(), error);
                    }
                    Log.e(TAG, "Gemini API Volley Error: " + errorMsg, error);
                    listener.onEvaluationError(userAnswer, errorMsg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };
        jsonObjectRequest.setTag(TAG);
        requestQueue.add(jsonObjectRequest);
    }

    @Override
    public String getCurrentQuestion() {
        if (questionList != null && currentQuestionIndex < questionList.size()) {
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
        if (currentQuestionIndex < getQuestionCount()) {
            currentQuestionIndex++;
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
package com.example.langhexx.Model;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

import com.android.volley.Request;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpeakingGrammarModel implements SpeakingContract.Model {
    private static final String TAG = "SpeakingGrammarModel";
    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;
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
        Log.d(TAG, "Evaluating with Gemini. Question: \"" + question + "\", Answer: \"" + userAnswer + "\"");
        if (userAnswer == null || userAnswer.trim().isEmpty()) {
            Log.w(TAG, "User answer is empty. Returning as incorrect.");
            if (listener != null) {
                listener.onEvaluationSuccess(userAnswer, false, "Please provide an answer.", "Please type your answer to the question.");
            }
            return;
        }

        String promptText = String.format(
                "Analyze the following user's answer in response to the given question within a language learning context.\n" +
                        "Question: \"%s\"\n" +
                        "User's Answer: \"%s\"\n\n" +
                        "Respond ONLY with a JSON object containing these fields:\n" +
                        "1. 'is_correct': boolean (true if the answer is grammatically correct, relevant, coherent, and provides a direct or reasonably inferable answer to the question asked. An answer can be general rather than highly specific and still be correct (e.g., Q: 'What time do you wake up?' A: 'I wake up in the morning.' IS correct). Also correct if it provides additional relevant info (e.g., Q: 'Do you like ice cream?' A: 'No, I like chocolate.' IS correct). False ONLY for:\n" +
                        "    a) Significant grammatical errors.\n" +
                        "    b) Clear irrelevance to the question.\n" +
                        "    c) Nonsensical or incoherent content.\n" +
                        "    d) Explicit evasions (e.g., 'I don\\'t know', 'I won\\'t tell you', 'I can\\'t say', 'I can\\'t remember') or answers that are so vague they offer no meaningful information (e.g., Q: 'What time do you wake up?' A: 'Sometime.' would be considered incorrect here) when the question clearly seeks a specific type of information and the learning context encourages providing it. The goal is to encourage practice in forming informative statements, not just grammatically correct non-answers to specific questions.).\n" +
                        "2. 'feedback_en': string (If 'is_correct' is false, provide a BRIEF explanation *in English* of the main issue – e.g., grammar, vocabulary, relevance, or why the answer is considered evasive or overly vague. For instance, for an answer like 'I don\\'t know' or 'Sometime.' to 'What time do you wake up?', feedback could be: 'This answer is a bit too vague or doesn\\'t provide the specific information requested. Please try to give a more precise time.' If 'is_correct' is true, this can be empty OR provide minor positive feedback *in English*, e.g., 'Your answer is correct and natural!' or 'Correct! 'I wake up in the morning' is a good general answer. For more practice, you could also try giving a specific time like 'I wake up at 7 AM'.' for answers that are general but correct.).\n" +
                        "3. 'suggested_answer_en': string (Provide a well-formed, correct alternative answer *in English* ONLY if 'is_correct' is false. This should be an example of a direct and informative answer to the question. For example, if the question was 'What time do you wake up?' and the user answered 'I don\\'t know' or 'Sometime.', a suggested answer could be 'I wake up at 7 AM.'. Leave empty if 'is_correct' is true, unless providing an alternative way to phrase a general but correct answer, see feedback_en for example).\n\n" +
                        "Example (incorrect grammar):\n" +
                        "{\n" +
                        "  \"is_correct\": false,\n" +
                        "  \"feedback_en\": \"There's a grammar error with the verb tense.\",\n" +
                        "  \"suggested_answer_en\": \"I went to the park yesterday.\"\n" +
                        "}\n\n" +
                        "Example (correct, general answer like 'I wake up in the morning.' to 'What time do you wake up?'):\n" +
                        "{\n" +
                        "  \"is_correct\": true,\n" +
                        "  \"feedback_en\": \"Correct! 'I wake up in the morning' is a good general answer. For more practice, you could also try giving a specific time like 'I wake up at 7 AM'.\",\n" +
                        "  \"suggested_answer_en\": \"\"\n" +
                        "}\n\n" +
                        "Example (correct, indirect):\n" +
                        "{\n" +
                        "  \"is_correct\": true,\n" +
                        "  \"feedback_en\": \"Good answer! You addressed the question and added relevant details.\",\n" +
                        "  \"suggested_answer_en\": \"\"\n" +
                        "}\n\n" +
                        "Example (incorrect, evasive answer like 'I don\\'t know' to 'What time do you wake up?'):\n" +
                        "{\n" +
                        "  \"is_correct\": false,\n" +
                        "  \"feedback_en\": \"While 'I don\\'t know' is grammatically correct, the question asks for a specific time. Please try to provide an actual time you wake up.\",\n" +
                        "  \"suggested_answer_en\": \"I wake up at 7 AM.\"\n" +
                        "}\n\n" +
                        "Example (incorrect, overly vague answer like 'Sometime.' to 'What time do you wake up?'):\n" +
                        "{\n" +
                        "  \"is_correct\": false,\n" +
                        "  \"feedback_en\": \"This answer is a bit too vague. Please try to give a more precise time.\",\n" +
                        "  \"suggested_answer_en\": \"I wake up at 8 AM.\"\n" +
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
            Log.e(TAG, "Error creating JSON request for Gemini", e);
            if (listener != null) listener.onEvaluationError(userAnswer, "Error creating API request.");
            return;
        }

        Log.d(TAG, "Gemini Request: " + requestBody.toString().substring(0, Math.min(requestBody.toString().length(), 500)) + "..."); // Log snippet

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, GEMINI_API_URL, requestBody,
                response -> {
                    Log.d(TAG, "Gemini API Full Response: " + response.toString());
                    try {
                        JSONObject promptFeedback = response.optJSONObject("promptFeedback");
                        if (promptFeedback != null) {
                            String blockReason = promptFeedback.optString("blockReason");
                            if (blockReason != null && !blockReason.isEmpty() && !blockReason.equalsIgnoreCase("OTHER")  && !blockReason.equalsIgnoreCase("SAFETY")) { // Gemini specific block reasons
                                Log.e(TAG, "Gemini prompt blocked by API. Reason: " + blockReason);
                                if (listener != null) listener.onEvaluationError(userAnswer, "Your request was blocked by the API (Reason: " + blockReason + "). Please try rephrasing.");
                                return;
                            }
                            JSONArray safetyRatingsPrompt = promptFeedback.optJSONArray("safetyRatings");
                            if (safetyRatingsPrompt != null) {
                                for (int i = 0; i < safetyRatingsPrompt.length(); i++) {
                                    JSONObject rating = safetyRatingsPrompt.getJSONObject(i);
                                    String probability = rating.optString("probability", "NEGLIGIBLE");
                                    if (!probability.equalsIgnoreCase("NEGLIGIBLE") && !probability.equalsIgnoreCase("LOW")) {
                                        String category = rating.optString("category", "UNKNOWN_CATEGORY");
                                        Log.w(TAG, "Prompt content has safety concerns. Category: " + category + ", Probability: " + probability);
                                        if (listener != null) listener.onEvaluationError(userAnswer, "Your input has content safety concerns (Category: " + category + "). Please rephrase.");
                                        return;
                                    }
                                }
                            }
                        }

                        JSONArray candidates = response.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject firstCandidate = candidates.getJSONObject(0);
                            JSONArray safetyRatings = firstCandidate.optJSONArray("safetyRatings");
                            if (safetyRatings != null) {
                                for (int i = 0; i < safetyRatings.length(); i++) {
                                    JSONObject rating = safetyRatings.getJSONObject(i);
                                    String probability = rating.optString("probability", "NEGLIGIBLE");
                                    if (!probability.equalsIgnoreCase("NEGLIGIBLE")) { // Stricter check for response
                                        String category = rating.optString("category", "UNKNOWN_CATEGORY");
                                        Log.w(TAG, "API response content blocked by safety filter. Category: " + category + ", Probability: " + probability);
                                        if (listener != null) listener.onEvaluationError(userAnswer, "API response unsuitable (Content Safety: " + category + ")");
                                        return;
                                    }
                                }
                            }

                            String finishReason = firstCandidate.optString("finishReason", "");
                            if (!finishReason.equalsIgnoreCase("STOP") && !finishReason.isEmpty() && !finishReason.equalsIgnoreCase("MAX_TOKENS")) { // MAX_TOKENS can sometimes be ok if content is present
                                Log.w(TAG, "Candidate finished with non-STOP reason: " + finishReason);
                                if (listener != null) listener.onEvaluationError(userAnswer, "API processing incomplete (Reason: " + finishReason + ")");
                                return;
                            }


                            JSONObject content = firstCandidate.optJSONObject("content");
                            if (content != null) {
                                JSONArray parts = content.optJSONArray("parts");
                                if (parts != null && parts.length() > 0) {
                                    String jsonResponseString = parts.getJSONObject(0).optString("text", "").trim();
                                    if (!jsonResponseString.isEmpty() && jsonResponseString.startsWith("{") && jsonResponseString.endsWith("}")) {
                                        Log.d(TAG, "Gemini JSON Response (from parts): " + jsonResponseString);
                                        JSONObject evaluationResult = new JSONObject(jsonResponseString);
                                        boolean isCorrect = evaluationResult.optBoolean("is_correct", false);
                                        String feedbackEn = evaluationResult.optString("feedback_en", ""); // Changed from feedback_vi
                                        String suggestionEn = evaluationResult.optString("suggested_answer_en", "");
                                        if (listener != null) listener.onEvaluationSuccess(userAnswer, isCorrect, feedbackEn, suggestionEn);
                                    } else {
                                        Log.e(TAG, "Gemini response 'text' part is not valid JSON or empty: " + jsonResponseString);
                                        if (listener != null) listener.onEvaluationError(userAnswer, "API returned an invalid or empty evaluation format.");
                                    }
                                } else {
                                    Log.w(TAG, "'parts' in API response 'content' is missing or empty.");
                                    if (listener != null) listener.onEvaluationError(userAnswer, "API response structure error (missing content parts).");
                                }
                            } else {
                                Log.w(TAG, "API response candidate missing 'content'. FinishReason: " + finishReason);
                                if (listener != null) listener.onEvaluationError(userAnswer, "API response incomplete (Reason: " + finishReason + ")");
                            }
                        } else {
                            Log.e(TAG, "Gemini response missing 'candidates'. Full Response: " + response);
                            if (listener != null) listener.onEvaluationError(userAnswer, "API did not return evaluation candidates.");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing Gemini JSON response", e);
                        if (listener != null) listener.onEvaluationError(userAnswer, "Error processing API response.");
                    }
                },
                error -> {
                    String errorMsg = "Network error or API communication issue.";
                    int statusCode = -1;
                    String responseData = "";
                    if (error.networkResponse != null) {
                        statusCode = error.networkResponse.statusCode;
                        if (error.networkResponse.data != null) {
                            try {
                                responseData = new String(error.networkResponse.data, "UTF-8");
                                Log.e(TAG, "Gemini API Error (Status: " + statusCode + "), Response: " + responseData);
                                JSONObject errorJson = new JSONObject(responseData);
                                JSONObject errorDetail = errorJson.optJSONObject("error");
                                if (errorDetail != null && errorDetail.has("message")) {
                                    errorMsg = errorDetail.getString("message");
                                } else {
                                    errorMsg = "API error, see logs for details.";
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error decoding network error response", e);
                                errorMsg = "Error reading API error response.";
                            }
                        } else {
                            errorMsg = "API returned status " + statusCode + " with no data.";
                        }
                        errorMsg = "API Error (Code: " + statusCode + "): " + errorMsg;
                    } else {
                        Log.e(TAG, "Volley Error (No Network Response / Timeout): " + error.toString(), error);
                        errorMsg = "Connection error or timeout. Please check your network.";
                    }
                    if (listener != null) listener.onEvaluationError(userAnswer, errorMsg);
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
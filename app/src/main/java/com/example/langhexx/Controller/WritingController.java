package com.example.langhexx.Controller;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
// Import View explicitly if needed for getFeedbackPanelView, though it's an interface method
// import android.view.View;


import com.example.langhexx.Model.WritingExercise;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WritingController {

    private static final String TAG = "WritingController";
    private static final String GEMINI_API_KEY = "AIzaSyDoQKvSTwu_RJMIKl3c456iLFW0oIK16tc"; // <<<<<< THAY THẾ KEY CỦA BẠN VÀO ĐÂY
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public interface ViewInterface {
        void displayExerciseTitle(String title);
        void displayQuestionPrompt(String prompt);
        void showToast(String message);
        void showFailToast(String message);
        void showConfirmationDialog(String title, String message, Runnable onConfirm);
        void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle);
        void finishActivity();
        void setSubmitButtonState(String text, boolean enabled);
        void setUIElementsVisibility(boolean visible);
        void showLoading(String message);
        void hideLoading();
        void clearAnswerInput();
        void displayStructuredAIFeedback(
                String taskResponseFeedback, int taskResponseIconType,
                String coherenceCohesionFeedback, int coherenceCohesionIconType,
                String grammarVocabularyFeedback, int grammarVocabularyIconType,
                String lengthFeedback, int lengthIconType
        );
        void setFeedbackPanelVisibility(boolean visible);
        void setFeedbackTriggerVisibility(boolean visible);
        void setAnswerInputVisibility(boolean visible);
        void focusOnFeedbackPanel(); // MỚI: Để yêu cầu View cuộn tới panel
        // android.view.View getFeedbackPanelView(); // KHÔNG CẦN NẾU focusOnFeedbackPanel tự xử lý
    }

    public static final int STATE_SUBMIT_WRITING = 0;
    public static final int STATE_RETRY_WRITING = 1;
    private int currentButtonState = STATE_SUBMIT_WRITING;

    private ViewInterface view;
    private String levelName;
    private String topicTitle;
    private String exerciseTitle;
    private WritingExercise currentWritingExercise;
    private ArrayList<String> allExerciseTitles;

    private boolean exerciseDataLoaded = false;
    private boolean titlesLoaded = false;
    private boolean isUserEditingAfterFeedback = false; // MỚI

    private DatabaseReference databaseReference;

    public WritingController(ViewInterface view, Intent intent) {
        this.view = view;
        this.allExerciseTitles = new ArrayList<>();
        this.databaseReference = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        if (intent != null) {
            levelName = intent.getStringExtra("LEVEL_NAME");
            topicTitle = intent.getStringExtra("TOPIC_TITLE");
            exerciseTitle = intent.getStringExtra("EXERCISE_TITLE");
        } else {
            handleInitializationError("Error: Intent is null.");
            return;
        }

        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            handleInitializationError("Error: Missing exercise identifiers in Intent.");
        }
    }

    public void initialize() {
        Log.d(TAG, "Initializing Controller for: L-" + levelName + ", T-" + topicTitle + ", E-" + exerciseTitle);
        if (view == null) {
            Log.e(TAG, "View is null in initialize");
            return;
        }
        currentButtonState = STATE_SUBMIT_WRITING; // Reset state on init
        isUserEditingAfterFeedback = false;
        view.setUIElementsVisibility(false);
        view.setFeedbackPanelVisibility(false);
        view.setFeedbackTriggerVisibility(false);
        loadExerciseDataFromFirebase();
        loadAllExerciseTitlesFromFirebase();
    }

    private void handleInitializationError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (view != null) {
            view.showFailToast(errorMessage);
            view.finishActivity();
        }
    }

    private void loadExerciseDataFromFirebase() {
        // ... (không thay đổi)
        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            if(view != null) {
                Log.e(TAG, "Cannot load exercise data: Level/Topic/ExerciseTitle is null.");
                view.showFailToast("Error: Missing data to load exercise.");
                view.finishActivity();
            }
            return;
        }
        DatabaseReference exerciseRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Writing").child("Topics").child(topicTitle)
                .child("Exercises").child(exerciseTitle);
        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "Writing exercise data not found at path: " + exerciseRef.toString());
                    if (view != null) {
                        view.displayQuestionPrompt("Content not available for this exercise.");
                        view.setSubmitButtonState("N/A", false);
                    }
                    exerciseDataLoaded = true;
                    checkIfAllDataLoadedAndReady();
                    return;
                }

                String scriptPrompt = snapshot.child("script").getValue(String.class);

                if (scriptPrompt != null && !scriptPrompt.isEmpty()) {
                    currentWritingExercise = new WritingExercise(snapshot.getKey(), exerciseTitle, scriptPrompt);
                    Log.d(TAG, "Script/Prompt loaded: " + currentWritingExercise.getScript());
                    if (view != null) {
                        view.displayExerciseTitle(currentWritingExercise.getTitle());
                        view.displayQuestionPrompt(currentWritingExercise.getScript());
                    }
                } else {
                    Log.w(TAG, "'script' field missing or empty in Firebase for exercise: " + exerciseTitle);
                    currentWritingExercise = new WritingExercise(snapshot.getKey(), exerciseTitle, "Writing prompt not found.");
                    if (view != null) {
                        view.displayExerciseTitle(exerciseTitle);
                        view.displayQuestionPrompt("Writing prompt not available for this exercise.");
                    }
                }
                exerciseDataLoaded = true;
                checkIfAllDataLoadedAndReady();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase loading cancelled/failed for writing exercise: " + error.getMessage(), error.toException());
                if (view != null) {
                    view.showFailToast("Error loading writing exercise: " + error.getMessage());
                    view.finishActivity();
                }
                exerciseDataLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void loadAllExerciseTitlesFromFirebase() {
        // ... (không thay đổi)
        if (levelName == null || topicTitle == null) {
            Log.e(TAG, "Cannot load all writing exercise titles: Level/Topic null.");
            titlesLoaded = true;
            checkIfAllDataLoadedAndReady();
            return;
        }
        DatabaseReference exercisesRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Writing").child("Topics").child(topicTitle).child("Exercises");

        Log.d(TAG, "Loading all writing exercise titles from: " + exercisesRef.toString());
        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allExerciseTitles.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot exSnap : snapshot.getChildren()) {
                        String title = exSnap.getKey();
                        if (title != null && !title.isEmpty()) {
                            allExerciseTitles.add(title);
                        }
                    }
                    Log.i(TAG, "Loaded " + allExerciseTitles.size() + " writing exercise titles for topic: " + topicTitle);
                } else {
                    Log.w(TAG, "No writing exercises found under topic path: " + topicTitle);
                }
                titlesLoaded = true;
                checkIfAllDataLoadedAndReady();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load all writing exercise titles: " + error.getMessage());
                titlesLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
        });
    }


    private void checkIfAllDataLoadedAndReady() {
        if (exerciseDataLoaded && titlesLoaded && view != null) {
            Log.d(TAG, "All initial data loaded for Writing.");
            view.setUIElementsVisibility(true);
            view.setAnswerInputVisibility(true); // Đảm bảo input luôn hiển thị nếu data đã load
            updateSubmitButtonBasedOnState();
        }
    }

    private void updateSubmitButtonBasedOnState() {
        if (view == null) return;

        boolean contentNotAvailable = currentWritingExercise == null ||
                currentWritingExercise.getScript() == null ||
                currentWritingExercise.getScript().equals("Writing prompt not available for this exercise.") ||
                currentWritingExercise.getScript().equals("Writing prompt not found.") ||
                currentWritingExercise.getScript().equals("Content not available for this exercise.");

        // Luôn hiển thị answer input nếu UI elements được phép hiển thị (đã được xử lý trong checkIfAllDataLoadedAndReady)
        // view.setAnswerInputVisibility(true); // Giữ nguyên theo logic cũ của bạn nếu cần

        if (contentNotAvailable) {
            view.setSubmitButtonState("N/A", false);
            view.setFeedbackPanelVisibility(false);
            view.setFeedbackTriggerVisibility(false);
            view.displayQuestionPrompt("Content not available for this exercise.");
            return;
        }

        switch (currentButtonState) {
            case STATE_SUBMIT_WRITING:
                view.setSubmitButtonState("Submit", true);
                // Nếu đang ở trạng thái submit (hoặc vừa chuyển từ Retry sang Submit do edit)
                // thì ẩn panel feedback.
                if (isUserEditingAfterFeedback) { // Nếu người dùng vừa edit và chuyển về SUBMIT
                    view.setFeedbackPanelVisibility(false);
                    isUserEditingAfterFeedback = false; // Reset cờ
                } else if (!viewIsShowingFeedbackPanel()){ // Ẩn nếu panel chưa hiển thị (trạng thái submit ban đầu)
                    view.setFeedbackPanelVisibility(false);
                }
                // Nếu panel đang hiển thị và user không edit (ví dụ mới load lại app ở trạng thái retry)
                // thì để nguyên, onResume sẽ xử lý.
                view.setFeedbackTriggerVisibility(false);
                break;
            case STATE_RETRY_WRITING:
                view.setSubmitButtonState("Retry", true);
                // Khi ở trạng thái Retry, panel feedback phải được hiển thị.
                // Việc hiển thị được thực hiện trong proceedWithWritingSubmission và onResume.
                // Ở đây chỉ đảm bảo trigger ẩn.
                view.setFeedbackTriggerVisibility(false);
                view.setFeedbackPanelVisibility(true); // Đảm bảo panel hiển thị khi ở trạng thái Retry
                break;
            default:
                view.setSubmitButtonState("Submit", true);
                view.setFeedbackPanelVisibility(false);
                view.setFeedbackTriggerVisibility(false);
                break;
        }
    }

    // MỚI: Phương thức để View thông báo khi text thay đổi
    public void onAnswerTextChanged() {
        if (view == null) return;
        // Chỉ xử lý nếu đang ở trạng thái RETRY (đã có feedback)
        if (currentButtonState == STATE_RETRY_WRITING) {
            Log.d(TAG, "Text changed while in Retry state. Switching to Submit state.");
            currentButtonState = STATE_SUBMIT_WRITING;
            isUserEditingAfterFeedback = true; // Đánh dấu người dùng đã edit sau khi có feedback
            updateSubmitButtonBasedOnState(); // Sẽ ẩn panel feedback và đổi nút
        }
    }
    // MỚI: Giúp controller biết trạng thái hiện tại của panel trên View
    private boolean viewIsShowingFeedbackPanel() {
        // Điều này giả định rằng bạn sẽ thêm một phương thức vào ViewInterface
        // để lấy trạng thái của panel. Nếu không, logic này cần được điều chỉnh.
        // For now, let's assume we manage this via currentButtonState and onResume.
        // If currentButtonState is STATE_RETRY_WRITING, we assume panel *should* be visible.
        // This check is more about the initial state before any interaction.
        return false; // Hoặc một cách kiểm tra thực tế hơn từ View
    }


    public void onSubmitButtonClicked(String userAnswer) {
        if (view == null) return;
        Log.d(TAG, "Submit button clicked. State: " + currentButtonState);

        switch (currentButtonState) {
            case STATE_SUBMIT_WRITING:
                isUserEditingAfterFeedback = false; // Reset cờ này khi submit
                if (userAnswer.trim().isEmpty()) {
                    view.showFailToast("Please write something before submitting.");
                    return;
                }
                if (currentWritingExercise == null || currentWritingExercise.getScript() == null || currentWritingExercise.getScript().isEmpty() || currentWritingExercise.getScript().contains("not available")) {
                    view.showFailToast("Cannot submit without a valid writing prompt.");
                    return;
                }
                view.showConfirmationDialog("Confirm Submission", "Are you sure you want to submit your writing for feedback?", () -> {
                    proceedWithWritingSubmission(userAnswer, currentWritingExercise.getScript());
                });
                break;
            case STATE_RETRY_WRITING:
                Log.i(TAG, "Retry button clicked. Resetting for new attempt.");
                currentButtonState = STATE_SUBMIT_WRITING;
                isUserEditingAfterFeedback = false; // Reset cờ
                view.clearAnswerInput();
                // updateSubmitButtonBasedOnState sẽ ẩn panel feedback
                updateSubmitButtonBasedOnState();
                break;
            default:
                Log.w(TAG, "Unknown button state clicked: " + currentButtonState);
                currentButtonState = STATE_SUBMIT_WRITING;
                isUserEditingAfterFeedback = false;
                updateSubmitButtonBasedOnState();
                break;
        }
    }

    private void proceedWithWritingSubmission(String userAnswer, String originalPrompt) {
        if (view == null) return;
        Log.i(TAG, "Proceeding with writing submission...");
        view.showLoading("Getting feedback...");
        view.setFeedbackTriggerVisibility(false);
        view.setFeedbackPanelVisibility(false); // Ẩn panel trong khi loading

        getFeedbackFromGemini(originalPrompt, userAnswer, new FeedbackCallback() {
            @Override
            public void onSuccess(JSONObject feedbackJson) {
                mainThreadHandler.post(() -> {
                    if (view == null) return;
                    view.hideLoading();
                    try {
                        Log.d(TAG, "Raw Gemini JSON feedback: " + feedbackJson.toString(2));

                        JSONObject taskResponse = feedbackJson.getJSONObject("taskResponse");
                        JSONObject coherenceCohesion = feedbackJson.getJSONObject("coherenceCohesion");
                        JSONObject grammarVocabulary = feedbackJson.getJSONObject("grammarVocabulary");
                        JSONObject length = feedbackJson.getJSONObject("length");

                        view.displayStructuredAIFeedback(
                                taskResponse.getString("feedback"), taskResponse.getInt("iconType"),
                                coherenceCohesion.getString("feedback"), coherenceCohesion.getInt("iconType"),
                                grammarVocabulary.getString("feedback"), grammarVocabulary.getInt("iconType"),
                                length.getString("feedback"), length.getInt("iconType")
                        );

                        view.showToast("Feedback received!");
                        view.setFeedbackPanelVisibility(true);   // Hiển thị panel feedback
                        view.focusOnFeedbackPanel();              // MỚI: Yêu cầu View cuộn/focus vào panel

                        currentButtonState = STATE_RETRY_WRITING;
                        isUserEditingAfterFeedback = false; // Reset, vì feedback vừa được hiển thị
                        updateSubmitButtonBasedOnState(); // Cập nhật nút sang "Retry"

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing structured feedback JSON from Gemini", e);
                        view.showFailToast("Error displaying feedback. Invalid format from AI.");
                        currentButtonState = STATE_SUBMIT_WRITING;
                        isUserEditingAfterFeedback = false;
                        updateSubmitButtonBasedOnState();
                    }
                });
            }

            @Override
            public void onError(String error) {
                mainThreadHandler.post(() -> {
                    if (view == null) return;
                    view.hideLoading();
                    view.showFailToast("Failed to get AI feedback: " + error);
                    currentButtonState = STATE_SUBMIT_WRITING;
                    isUserEditingAfterFeedback = false;
                    updateSubmitButtonBasedOnState();
                });
            }
        });
    }

    interface FeedbackCallback {
        void onSuccess(JSONObject feedbackJson);
        void onError(String error);
    }

    private void getFeedbackFromGemini(String originalPrompt, String userAnswer, FeedbackCallback callback) {
        // ... (không thay đổi)
        executorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GEMINI_API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);

                String promptForGemini = "You are an English language learning assistant. " +
                        "Evaluate the following written response to the prompt. " +
                        "Provide constructive feedback for a language learner. " +
                        "Your response MUST be a JSON object with the following exact structure: " +
                        "{\"taskResponse\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>}, " +
                        "\"coherenceCohesion\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>}, " +
                        "\"grammarVocabulary\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>}, " +
                        "\"length\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>}} " +
                        "Be concise in your feedback strings. iconType should be 1 if the user did well in that aspect (e.g., good length, clear task response), 0 otherwise (e.g., some errors, could be clearer).\n\n" +
                        "Original Prompt: \"" + originalPrompt + "\"\n\n" +
                        "User's Response: \"" + userAnswer + "\"\n\n" +
                        "Provide only the JSON object as your response.";

                JSONObject jsonBody = new JSONObject();
                JSONArray contentsArray = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray partsArray = new JSONArray();
                JSONObject part = new JSONObject();
                part.put("text", promptForGemini);
                partsArray.put(part);
                content.put("parts", partsArray);
                contentsArray.put(content);
                jsonBody.put("contents", contentsArray);

                JSONObject generationConfig = new JSONObject();
                generationConfig.put("response_mime_type", "application/json");
                jsonBody.put("generationConfig", generationConfig);

                // Log.d(TAG, "Gemini Request Body: " + jsonBody.toString(2)); // Verbose logging

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Gemini API Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine);
                        }
                        String rawResponse = response.toString();
                        // Log.d(TAG, "Gemini API Raw Response: " + rawResponse); // Verbose logging

                        JSONObject fullJsonResponse = new JSONObject(rawResponse);
                        JSONObject structuredFeedback;

                        JSONObject firstCandidate = fullJsonResponse.getJSONArray("candidates").getJSONObject(0);
                        JSONObject firstCandidateContent = firstCandidate.getJSONObject("content");
                        JSONArray firstCandidateParts = firstCandidateContent.getJSONArray("parts");

                        if (firstCandidateParts.length() > 0) {
                            JSONObject firstPart = firstCandidateParts.getJSONObject(0);
                            if (firstPart.has("text")) {
                                String feedbackJsonString = firstPart.getString("text");
                                // Log.d(TAG, "Gemini feedback is a string in 'text' field: " + feedbackJsonString);
                                if (feedbackJsonString.startsWith("```json")) {
                                    feedbackJsonString = feedbackJsonString.substring(7); // Skip ```json\n
                                }
                                if (feedbackJsonString.endsWith("```")) {
                                    feedbackJsonString = feedbackJsonString.substring(0, feedbackJsonString.length() - 3);
                                }
                                structuredFeedback = new JSONObject(feedbackJsonString.trim());
                            } else if (firstPart.has("jsonObject")) { // Check for direct JSON object
                                structuredFeedback = firstPart.getJSONObject("jsonObject");
                            }
                            else { // Fallback if the structure is just the object itself
                                structuredFeedback = firstPart;
                            }
                        } else {
                            throw new JSONException("Parts array is empty in Gemini response");
                        }

                        // Log.d(TAG, "Parsed Structured Feedback: " + structuredFeedback.toString(2)); // Verbose
                        callback.onSuccess(structuredFeedback);
                    }
                } else {
                    StringBuilder errorResponse = new StringBuilder();
                    if (conn.getErrorStream() != null) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                            String responseLine;
                            while ((responseLine = br.readLine()) != null) {
                                errorResponse.append(responseLine.trim());
                            }
                        }
                    } else {
                        errorResponse.append("No error stream data.");
                    }
                    Log.e(TAG, "Gemini API Error. Code: " + responseCode + ". Response: " + errorResponse.toString());
                    callback.onError("Server error: " + responseCode + ". " + errorResponse.toString());
                }

            } catch (Exception e) {
                Log.e(TAG, "Error calling Gemini API or parsing response", e);
                callback.onError("Client-side error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private boolean hasNextExercise() {
        if (titlesLoaded && allExerciseTitles != null && !allExerciseTitles.isEmpty() && exerciseTitle != null) {
            int currentIndex = allExerciseTitles.indexOf(exerciseTitle);
            return currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1;
        }
        return false;
    }

    public void onPause() {
        Log.d(TAG, "onPause called by View.");
    }

    public void onResume() {
        Log.d(TAG, "onResume called by View.");
        if (view != null) {
            // Khi resume, cập nhật lại UI dựa trên trạng thái hiện tại của controller
            // Đặc biệt quan trọng nếu app bị đưa vào background rồi quay lại
            if (currentButtonState == STATE_RETRY_WRITING && !isUserEditingAfterFeedback) {
                // Nếu đang ở trạng thái retry và người dùng KHÔNG đang edit (tức là feedback nên được hiển thị)
                view.setFeedbackPanelVisibility(true);
                view.focusOnFeedbackPanel(); // Có thể focus lại nếu cần
            } else {
                // Ngược lại (đang submit hoặc user đã edit sau feedback), panel nên ẩn
                // updateSubmitButtonBasedOnState sẽ xử lý việc này
            }
            updateSubmitButtonBasedOnState(); // Luôn gọi để đảm bảo UI nhất quán
        }
    }


    public void onDestroy() {
        if (view != null) {
            this.view = null;
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Log.d(TAG, "ExecutorService shut down.");
        }
    }

    public boolean handleBackPressed() {
        return false;
    }

    public int getCurrentButtonState() {
        return currentButtonState;
    }
}
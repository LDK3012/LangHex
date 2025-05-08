package com.example.langhexx.Controller;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.text.TextUtils; // Import TextUtils

import androidx.annotation.NonNull;

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
    // REMEMBER TO REPLACE WITH YOUR ACTUAL KEY FOR PRODUCTION
    private static final String GEMINI_API_KEY = "AIzaSyDoQKvSTwu_RJMIKl3c456iLFW0oIK16tc"; // THAY KEY CỦA BẠN
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
        void setAnswerEditTextEnabled(boolean enabled);
        void requestFocusOnAnswerInput(); // NEW: For explicit focus and keyboard
        void focusOnFeedbackPanel();
        void setSeeRevisedVersionButtonVisibility(boolean visible);
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
    private boolean isUserEditingAfterFeedback = false;
    private boolean allCriteriaSuccess = false;
    private String submittedTextForCurrentFeedback = ""; // Store text that received feedback

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
        if (view == null) { Log.e(TAG, "View is null in initialize"); return; }

        currentButtonState = STATE_SUBMIT_WRITING;
        isUserEditingAfterFeedback = false;
        allCriteriaSuccess = false;
        submittedTextForCurrentFeedback = ""; // Reset
        view.setUIElementsVisibility(false);
        view.setFeedbackPanelVisibility(false);
        view.setFeedbackTriggerVisibility(false);
        view.setSeeRevisedVersionButtonVisibility(false);
        view.setAnswerEditTextEnabled(true); // Enable EditText but don't force focus/keyboard
        // The initial check for empty text in onAnswerTextChanged will disable submit if needed
        loadExerciseDataFromFirebase();
        loadAllExerciseTitlesFromFirebase();
    }

    private void handleInitializationError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (view != null) { view.showFailToast(errorMessage); view.finishActivity(); }
    }

    private void loadExerciseDataFromFirebase() {
        // ... (no changes in this method)
        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            if(view != null) { Log.e(TAG, "Cannot load exercise data: Level/Topic/ExerciseTitle is null."); view.showFailToast("Error: Missing data to load exercise."); view.finishActivity(); }
            return;
        }
        DatabaseReference exerciseRef = databaseReference.child("Lessons").child("Levels").child(levelName).child("Writing").child("Topics").child(topicTitle).child("Exercises").child(exerciseTitle);
        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "Writing exercise data not found at path: " + exerciseRef);
                    if (view != null) {
                        view.displayQuestionPrompt("Content not available for this exercise.");
                        view.setSubmitButtonState("N/A", false);
                        view.setAnswerEditTextEnabled(false); // Disable if no content
                    }
                    exerciseDataLoaded = true; checkIfAllDataLoadedAndReady(); return;
                }
                String scriptPrompt = snapshot.child("script").getValue(String.class);
                if (scriptPrompt != null && !scriptPrompt.isEmpty()) {
                    currentWritingExercise = new WritingExercise(snapshot.getKey(), exerciseTitle, scriptPrompt);
                    if (view != null) { view.displayExerciseTitle(currentWritingExercise.getTitle()); view.displayQuestionPrompt(currentWritingExercise.getScript()); }
                } else {
                    currentWritingExercise = new WritingExercise(snapshot.getKey(), exerciseTitle, "Writing prompt not found.");
                    if (view != null) {
                        view.displayExerciseTitle(exerciseTitle);
                        view.displayQuestionPrompt("Writing prompt not available for this exercise.");
                        view.setAnswerEditTextEnabled(false); // Disable if no content
                    }
                }
                exerciseDataLoaded = true; checkIfAllDataLoadedAndReady();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase loading cancelled/failed: " + error.getMessage(), error.toException());
                if (view != null) {
                    view.showFailToast("Error loading exercise: " + error.getMessage());
                    view.setAnswerEditTextEnabled(false); // Disable on error
                }
                exerciseDataLoaded = true; checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void loadAllExerciseTitlesFromFirebase() {
        // ... (no changes in this method)
        if (levelName == null || topicTitle == null) { titlesLoaded = true; checkIfAllDataLoadedAndReady(); return; }
        DatabaseReference exercisesRef = databaseReference.child("Lessons").child("Levels").child(levelName).child("Writing").child("Topics").child(topicTitle).child("Exercises");
        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                allExerciseTitles.clear();
                if (snapshot.exists()) { for (DataSnapshot exSnap : snapshot.getChildren()) { allExerciseTitles.add(exSnap.getKey()); } }
                titlesLoaded = true; checkIfAllDataLoadedAndReady();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { titlesLoaded = true; checkIfAllDataLoadedAndReady(); }
        });
    }


    private void checkIfAllDataLoadedAndReady() {
        if (exerciseDataLoaded && titlesLoaded && view != null) {
            Log.d(TAG, "All initial data loaded.");
            view.setUIElementsVisibility(true);
            view.setAnswerInputVisibility(true);
            updateSubmitButtonBasedOnState();
            // Trigger an initial onAnswerTextChanged with empty string to set initial submit button state
            onAnswerTextChanged(""); // Assuming EditText is initially empty
        }
    }

    private void updateSubmitButtonBasedOnState() {
        if (view == null) return;
        Log.d(TAG, "updateSubmitButtonBasedOnState - CurrentState: " + currentButtonState + ", isEditingAfterFeedback: " + isUserEditingAfterFeedback + ", allSuccess: " + allCriteriaSuccess);

        boolean contentNotAvailable = currentWritingExercise == null || currentWritingExercise.getScript() == null ||
                currentWritingExercise.getScript().equals("Writing prompt not available for this exercise.") ||
                currentWritingExercise.getScript().equals("Writing prompt not found.") ||
                currentWritingExercise.getScript().equals("Content not available for this exercise.");

        if (contentNotAvailable) {
            view.setSubmitButtonState("N/A", false);
            view.setFeedbackPanelVisibility(false);
            view.setFeedbackTriggerVisibility(false);
            view.setSeeRevisedVersionButtonVisibility(false);
            view.setAnswerEditTextEnabled(false); // Important: disable if no content
            view.displayQuestionPrompt("Content not available for this exercise.");
            return;
        }

        switch (currentButtonState) {
            case STATE_SUBMIT_WRITING:
                view.setSubmitButtonState("Submit", true); // Default to true, onAnswerTextChanged will refine
                view.setAnswerEditTextEnabled(true);
                if (isUserEditingAfterFeedback) {
                    view.setFeedbackPanelVisibility(true);
                    view.setSeeRevisedVersionButtonVisibility(!allCriteriaSuccess); // Show if not all success
                } else {
                    view.setFeedbackPanelVisibility(false);
                    view.setSeeRevisedVersionButtonVisibility(false);
                }
                view.setFeedbackTriggerVisibility(false);
                break;
            case STATE_RETRY_WRITING: // After feedback is received
                view.setAnswerEditTextEnabled(false); // Disable EditText
                if (allCriteriaSuccess) {
                    view.setSubmitButtonState("Done", true);
                } else {
                    view.setSubmitButtonState("Edit", true);
                }
                view.setFeedbackTriggerVisibility(false);
                view.setFeedbackPanelVisibility(true);
                view.setSeeRevisedVersionButtonVisibility(!allCriteriaSuccess);
                break;
            default:
                view.setSubmitButtonState("Submit", true);
                view.setAnswerEditTextEnabled(true);
                view.setFeedbackPanelVisibility(false);
                view.setFeedbackTriggerVisibility(false);
                view.setSeeRevisedVersionButtonVisibility(false);
                break;
        }
    }

    // CHANGE: Method signature changed to accept current text
    public void onAnswerTextChanged(String currentText) {
        if (view == null) return;

        if (currentButtonState == STATE_SUBMIT_WRITING) {
            if (isUserEditingAfterFeedback) {
                // User is editing after feedback. Enable submit only if text has changed.
                boolean hasChanged = !currentText.trim().equals(submittedTextForCurrentFeedback.trim());
                view.setSubmitButtonState("Submit", hasChanged);
            } else {
                // Initial submission. Enable submit if text is not empty.
                view.setSubmitButtonState("Submit", !TextUtils.isEmpty(currentText.trim()));
            }
        }
        // No action needed if in STATE_RETRY_WRITING as EditText should be disabled
    }


    public void onSubmitButtonClicked(String userAnswer) {
        if (view == null) return;
        Log.d(TAG, "Submit button clicked. State: " + currentButtonState + ", AllSuccess: " + allCriteriaSuccess + ", EditingAfterFeedback: " + isUserEditingAfterFeedback);

        switch (currentButtonState) {
            case STATE_SUBMIT_WRITING:
                allCriteriaSuccess = false;
                if (userAnswer.trim().isEmpty()) {
                    view.showFailToast("Please write something before submitting."); return;
                }
                if (currentWritingExercise == null || currentWritingExercise.getScript() == null || currentWritingExercise.getScript().isEmpty() || currentWritingExercise.getScript().contains("not available")) {
                    view.showFailToast("Cannot submit without a valid writing prompt."); return;
                }
                // Store the text being submitted BEFORE calling API
                // This is for the "Edit" functionality later
                // This will be overwritten if it was an initial submission.
                // If it was an edit, this submittedTextForCurrentFeedback is already the text that previously got feedback.
                // We need to store the *new* text that is about to be submitted.
                // This means submittedTextForCurrentFeedback gets updated *after* successful submission and feedback.
                // For now, it's correct that it holds the text for which feedback *was* received.

                view.showConfirmationDialog("Confirm Submission", "Are you sure you want to submit your writing for feedback?", () -> {
                    // This submittedTextForCurrentFeedback will be set *after* successful feedback.
                    // The userAnswer is the *new* text being submitted.
                    proceedWithWritingSubmission(userAnswer, currentWritingExercise.getScript());
                });
                break;
            case STATE_RETRY_WRITING: // Button is "Done" or "Edit"
                if (allCriteriaSuccess) { // Button is "Done"
                    Log.i(TAG, "'Done' button clicked. Finishing activity.");
                    isUserEditingAfterFeedback = false;
                    view.finishActivity();
                } else { // Button is "Edit"
                    Log.i(TAG, "'Edit' button clicked. Enabling edit mode, keeping feedback panel.");
                    currentButtonState = STATE_SUBMIT_WRITING;
                    isUserEditingAfterFeedback = true;
                    allCriteriaSuccess = false; // Reset for the new edit attempt
                    updateSubmitButtonBasedOnState(); // Sets button to "Submit", enables EditText
                    // CRITICAL: Disable submit button initially as text hasn't changed from what received feedback
                    view.setSubmitButtonState("Submit", false);
                    view.requestFocusOnAnswerInput(); // Request focus and show keyboard
                }
                break;
            default:
                Log.w(TAG, "Unknown button state clicked: " + currentButtonState);
                currentButtonState = STATE_SUBMIT_WRITING;
                isUserEditingAfterFeedback = false;
                allCriteriaSuccess = false;
                submittedTextForCurrentFeedback = "";
                updateSubmitButtonBasedOnState();
                break;
        }
    }

    private void proceedWithWritingSubmission(String userAnswer, String originalPrompt) {
        if (view == null) return;
        Log.i(TAG, "Proceeding with writing submission for text: " + userAnswer);
        view.showLoading("Getting feedback...");
        // Store the text that is *being submitted now* to compare against later if "Edit" is pressed.
        // This userAnswer is what will receive feedback.
        final String textBeingSubmitted = userAnswer;


        getFeedbackFromGemini(originalPrompt, userAnswer, new FeedbackCallback() {
            @Override
            public void onSuccess(JSONObject feedbackJson) {
                mainThreadHandler.post(() -> {
                    if (view == null) return;
                    view.hideLoading();
                    try {
                        JSONObject taskResponse = feedbackJson.getJSONObject("taskResponse");
                        JSONObject coherenceCohesion = feedbackJson.getJSONObject("coherenceCohesion");
                        JSONObject grammarVocabulary = feedbackJson.getJSONObject("grammarVocabulary");
                        JSONObject length = feedbackJson.getJSONObject("length");

                        allCriteriaSuccess = taskResponse.getInt("iconType") == 1 &&
                                coherenceCohesion.getInt("iconType") == 1 &&
                                grammarVocabulary.getInt("iconType") == 1 &&
                                length.getInt("iconType") == 1;
                        Log.d(TAG, "All criteria success after Gemini: " + allCriteriaSuccess);

                        view.displayStructuredAIFeedback(
                                taskResponse.getString("feedback"), taskResponse.getInt("iconType"),
                                coherenceCohesion.getString("feedback"), coherenceCohesion.getInt("iconType"),
                                grammarVocabulary.getString("feedback"), grammarVocabulary.getInt("iconType"),
                                length.getString("feedback"), length.getInt("iconType")
                        );

                        view.showToast("Feedback received!");
                        currentButtonState = STATE_RETRY_WRITING;
                        isUserEditingAfterFeedback = false;
                        submittedTextForCurrentFeedback = textBeingSubmitted; // Store the text that received this feedback

                        updateSubmitButtonBasedOnState(); // Sets button to "Edit"/"Done", disables EditText, shows panel
                        // view.setSeeRevisedVersionButtonVisibility(!allCriteriaSuccess); // Already handled by updateSubmitButton...
                        view.focusOnFeedbackPanel(); // Scroll to feedback panel

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing structured feedback JSON", e);
                        view.showFailToast("Error displaying feedback. Invalid AI format.");
                        allCriteriaSuccess = false;
                        currentButtonState = STATE_SUBMIT_WRITING; // Revert to submit
                        isUserEditingAfterFeedback = false;
                        // submittedTextForCurrentFeedback remains what it was before this failed attempt
                        updateSubmitButtonBasedOnState();
                        view.setSeeRevisedVersionButtonVisibility(true); // Default to show if error
                    }
                });
            }

            @Override
            public void onError(String error) {
                mainThreadHandler.post(() -> {
                    if (view == null) return;
                    view.hideLoading();
                    view.showFailToast("Failed to get AI feedback: " + error);
                    allCriteriaSuccess = false;
                    currentButtonState = STATE_SUBMIT_WRITING; // Revert to submit
                    isUserEditingAfterFeedback = false;
                    // submittedTextForCurrentFeedback remains what it was before this failed attempt
                    updateSubmitButtonBasedOnState();
                    view.setSeeRevisedVersionButtonVisibility(true); // Default to show if error
                });
            }
        });
    }

    interface FeedbackCallback {
        void onSuccess(JSONObject feedbackJson);
        void onError(String error);
    }

    private void getFeedbackFromGemini(String originalPrompt, String userAnswer, FeedbackCallback callback) {
        // ... (No changes in this method, but ensure GEMINI_API_KEY is set)
        if (GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY") || GEMINI_API_KEY.isEmpty()) {
            Log.e(TAG, "Gemini API Key is not set!");
            callback.onError("API Key not configured.");
            return;
        }
        executorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GEMINI_API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000); // 20 seconds
                conn.setReadTimeout(20000);    // 20 seconds

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

                // Specify JSON output directly in generationConfig
                JSONObject generationConfig = new JSONObject();
                generationConfig.put("response_mime_type", "application/json");
                jsonBody.put("generationConfig", generationConfig);


                try (OutputStream os = conn.getOutputStream()) { os.write(jsonBody.toString().getBytes("utf-8")); }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Gemini API Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) response.append(responseLine);

                        Log.d(TAG, "Raw Gemini Response: " + response.toString());
                        JSONObject fullJsonResponse = new JSONObject(response.toString());
                        JSONArray candidates = fullJsonResponse.getJSONArray("candidates");
                        if (candidates.length() > 0) {
                            JSONObject firstCandidate = candidates.getJSONObject(0);
                            JSONObject candidateContent = firstCandidate.getJSONObject("content");
                            JSONArray candidateParts = candidateContent.getJSONArray("parts");
                            if (candidateParts.length() > 0) {
                                // The "text" field should directly contain the JSON string due to response_mime_type
                                String feedbackJsonString = candidateParts.getJSONObject(0).getString("text");
                                // Gemini might still wrap it in markdown sometimes, defensively remove it
                                if (feedbackJsonString.startsWith("```json")) {
                                    feedbackJsonString = feedbackJsonString.substring(7);
                                    if (feedbackJsonString.endsWith("```")) {
                                        feedbackJsonString = feedbackJsonString.substring(0, feedbackJsonString.length() - 3);
                                    }
                                } else if (feedbackJsonString.startsWith("```")) { // More generic markdown block
                                    feedbackJsonString = feedbackJsonString.substring(3);
                                    if (feedbackJsonString.endsWith("```")) {
                                        feedbackJsonString = feedbackJsonString.substring(0, feedbackJsonString.length() - 3);
                                    }
                                }
                                callback.onSuccess(new JSONObject(feedbackJsonString.trim()));
                            } else throw new JSONException("Parts array is empty in Gemini response");
                        } else throw new JSONException("Candidates array is empty in Gemini response");
                    }
                } else {
                    StringBuilder errorResponse = new StringBuilder();
                    if (conn.getErrorStream() != null) {
                        try (BufferedReader brError = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                            String line; while ((line = brError.readLine()) != null) errorResponse.append(line.trim());
                        }
                    } else {
                        errorResponse.append("No error stream data.");
                    }
                    Log.e(TAG, "Gemini API Error Response: " + errorResponse.toString());
                    callback.onError("Server error: " + responseCode + ". " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calling Gemini API", e);
                callback.onError("Client-side error processing Gemini response: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void onPause() { Log.d(TAG, "onPause called by View."); }

    public void onResume() {
        Log.d(TAG, "onResume. State: " + currentButtonState + ", AllSuccess: " + allCriteriaSuccess + ", EditingAfterFeedback: " + isUserEditingAfterFeedback);
        if (view != null) {
            updateSubmitButtonBasedOnState();

            if (currentButtonState == STATE_RETRY_WRITING) { // After feedback is shown
                view.setFeedbackPanelVisibility(true);
                // view.setSeeRevisedVersionButtonVisibility(!allCriteriaSuccess); // Handled by updateSubmitButtonState
                // view.focusOnFeedbackPanel(); // This might be called too early if view is not fully laid out. Better to call after feedback is set.
            } else if (currentButtonState == STATE_SUBMIT_WRITING && isUserEditingAfterFeedback) {
                // User was editing, came back to app. Ensure panel is visible.
                view.setFeedbackPanelVisibility(true);
                // view.setSeeRevisedVersionButtonVisibility(!allCriteriaSuccess); // Handled by updateSubmitButtonState
                // Ensure submit button state is correct based on whether text changed
                // This requires getting current text from view, which is awkward here.
                // onAnswerTextChanged is the better place.
                // For now, updateSubmitButtonBasedOnState sets it to "Submit", true initially,
                // then if "Edit" was clicked, it's explicitly set to false.
                // This state should be preserved or re-evaluated.
                // Safest is to rely on the flow: if "Edit" was clicked, submit is disabled.
                // If user typed, it's enabled/disabled by onAnswerTextChanged.
            }
        }
    }

    public void onDestroy() {
        Log.d(TAG, "onDestroy called.");
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        this.view = null;
    }

    public boolean handleBackPressed() { return false; }
    public int getCurrentButtonState() { return currentButtonState; }
    public boolean areAllCriteriaSuccess() { return this.allCriteriaSuccess; }
    public boolean isUserEditingAfterFeedback() { return this.isUserEditingAfterFeedback; }
    public String getSubmittedTextForCurrentFeedback() { return this.submittedTextForCurrentFeedback; }
}
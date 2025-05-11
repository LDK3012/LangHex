package com.example.langhexx.Controller;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.langhexx.Model.MicrosoftUser;
import com.example.langhexx.Model.UserWritingAnswer;
import com.example.langhexx.Model.WritingExercise;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WritingController {

    private static final String TAG = "WritingController";
    private static final String GEMINI_API_KEY = "AIzaSyCf-9jplfin2aWdFAdxWcCdzox5wzIkBbQ"; // Replace with your actual key if needed
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    private final Handler inlineAnalysisHandler = new Handler(Looper.getMainLooper());
    private Runnable inlineAnalysisRunnable;
    private static final long INLINE_ANALYSIS_DEBOUNCE_MS = 1500;

    private MicrosoftUser currentMicrosoftUser;
    private FirebaseAuth mAuth;

    private boolean hasLoadedSavedAnswer = false;
    private boolean isFeedbackPanelVisible = false;
    private boolean editButtonForcedByLoad = false;

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
        void requestFocusOnAnswerInput();
        void focusOnFeedbackPanel();
        // void setSeeRevisedVersionButtonVisibility(boolean visible); // REMOVED
        void clearInlineErrorHighlighting();
        void displaySavedAnswer(String answer);
        String getCurrentAnswerText();
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
    private String submittedTextForCurrentFeedback = "";

    private DatabaseReference databaseReference;

    public WritingController(ViewInterface view, Intent intent) {
        this.view = view;
        this.allExerciseTitles = new ArrayList<>();
        this.databaseReference = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        this.mAuth = FirebaseAuth.getInstance();

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
            return;
        }
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null) {
            loadMicrosoftUserData(firebaseUser.getUid());
        } else {
            Log.w(TAG, "No Firebase user logged in at controller initialization.");
        }
    }

    private void loadMicrosoftUserData(String firebaseUid) {
        DatabaseReference userRef = databaseReference.child("Users").child(firebaseUid);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String msUserId = snapshot.child("microsoftGraphId").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String displayName = snapshot.child("name").getValue(String.class);

                    if (msUserId != null && !msUserId.isEmpty()) {
                        currentMicrosoftUser = new MicrosoftUser(msUserId, email, displayName);
                        Log.d(TAG, "Microsoft user data loaded: " + displayName + " (MS ID: " + msUserId + ")");
                    } else {
                        Log.d(TAG, "User " + firebaseUid + " is not a Microsoft Graph linked user or microsoftGraphId is missing/empty.");
                        // If not MS linked, can still use Firebase UID for progress path under MicrosoftUsers if that's the desired structure
                        currentMicrosoftUser = new MicrosoftUser(firebaseUid, email, displayName); // Use firebaseUid as the ID for path
                        Log.d(TAG, "Using Firebase UID as key for user data: " + firebaseUid);
                    }
                } else {
                    Log.w(TAG, "User data node not found for UID: " + firebaseUid);
                }
                // Crucially, ensure loadSavedUserAnswerFromFirebase is called AFTER user context is determined
                loadSavedUserAnswerFromFirebase();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load Microsoft user data.", error.toException());
                loadSavedUserAnswerFromFirebase();
            }
        });
    }


    public void initialize() {
        Log.d(TAG, "Initializing Controller for: L-" + levelName + ", T-" + topicTitle + ", E-" + exerciseTitle);
        if (view == null) { Log.e(TAG, "View is null in initialize"); return; }

        currentButtonState = STATE_SUBMIT_WRITING;
        isUserEditingAfterFeedback = false;
        allCriteriaSuccess = false;
        submittedTextForCurrentFeedback = "";
        hasLoadedSavedAnswer = false;
        isFeedbackPanelVisible = false;
        editButtonForcedByLoad = false;

        view.setUIElementsVisibility(false);
        view.setFeedbackPanelVisibility(false);
        view.setFeedbackTriggerVisibility(false);
        view.setAnswerEditTextEnabled(true); // Default to enabled

        loadExerciseDataFromFirebase();
        loadAllExerciseTitlesFromFirebase();
    }

    private void handleInitializationError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (view != null) { view.showFailToast(errorMessage); view.finishActivity(); }
    }

    private void loadExerciseDataFromFirebase() {
        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            if(view != null) { Log.e(TAG, "Cannot load exercise data: Level/Topic/ExerciseTitle is null."); view.showFailToast("Error: Missing data to load exercise."); view.finishActivity(); }
            exerciseDataLoaded = true; // Still set true to allow checkIfAllDataLoadedAndReady to proceed
            checkIfAllDataLoadedAndReady();
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
                        view.displayExerciseTitle(exerciseTitle != null ? exerciseTitle : "Exercise");
                    }
                    currentWritingExercise = null; // Explicitly null
                } else {
                    String scriptPrompt = snapshot.child("script").getValue(String.class);
                    String titleFromDb = snapshot.child("title").getValue(String.class); // Assuming 'title' field exists
                    String currentExTitle = titleFromDb != null ? titleFromDb : exerciseTitle;


                    if (scriptPrompt != null && !scriptPrompt.isEmpty()) {
                        currentWritingExercise = new WritingExercise(snapshot.getKey(), currentExTitle, scriptPrompt);
                        if (view != null) {
                            view.displayExerciseTitle(currentWritingExercise.getTitle());
                            view.displayQuestionPrompt(currentWritingExercise.getScript());
                        }
                    } else {
                        currentWritingExercise = new WritingExercise(snapshot.getKey(), currentExTitle, "Writing prompt not available for this exercise.");
                        if (view != null) {
                            view.displayExerciseTitle(currentExTitle); // Display title even if prompt is missing
                            view.displayQuestionPrompt("Writing prompt not available for this exercise.");
                        }
                    }
                }
                exerciseDataLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase loading exercise cancelled/failed: " + error.getMessage(), error.toException());
                if (view != null) {
                    view.showFailToast("Error loading exercise: " + error.getMessage());
                    view.displayExerciseTitle(exerciseTitle != null ? exerciseTitle : "Exercise"); // Show something
                    view.displayQuestionPrompt("Content loading error.");
                }
                currentWritingExercise = null; // Explicitly null
                exerciseDataLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void loadAllExerciseTitlesFromFirebase() {
        if (levelName == null || topicTitle == null) {
            Log.w(TAG, "Cannot load all exercise titles: Level or Topic is null.");
            titlesLoaded = true;
            checkIfAllDataLoadedAndReady();
            return;
        }
        DatabaseReference exercisesRef = databaseReference.child("Lessons").child("Levels").child(levelName).child("Writing").child("Topics").child(topicTitle).child("Exercises");
        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                allExerciseTitles.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot exSnap : snapshot.getChildren()) {
                        allExerciseTitles.add(exSnap.getKey());
                    }
                }
                titlesLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase loading titles cancelled/failed: " + error.getMessage());
                titlesLoaded = true; // Still set true
                checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void loadSavedUserAnswerFromFirebase() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        // Ensure exercise identifiers are valid
        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            Log.w(TAG, "Cannot load saved answer: Missing exercise identifiers.");
            hasLoadedSavedAnswer = false; // Explicitly false as we can't proceed
            checkIfAllDataLoadedAndReady(); // Allow UI setup with no saved answer
            return;
        }

        final String userIdForPath;
        if (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null && !currentMicrosoftUser.getUserId().isEmpty()) {
            userIdForPath = currentMicrosoftUser.getUserId();
            Log.d(TAG, "Loading saved answer using resolved User ID (MS or Firebase): " + userIdForPath);
        } else if (firebaseUser != null) {
            userIdForPath = firebaseUser.getUid(); // Fallback to Firebase UID if currentMicrosoftUser wasn't resolved
            Log.d(TAG, "Loading saved answer using Firebase User ID (fallback): " + userIdForPath);
        }
        else {
            Log.w(TAG, "Cannot load saved answer: No user context (Firebase or MS).");
            hasLoadedSavedAnswer = false;
            checkIfAllDataLoadedAndReady();
            return;
        }


        DatabaseReference answerRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers") // Path remains Users/MicrosoftUsers
                .child(userIdForPath)     // userIdForPath is either MS ID or Firebase UID
                .child("Progress")
                .child("WritingAnswers")
                .child(exerciseTitle);

        answerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String savedAnswer = snapshot.child("userAnswer").getValue(String.class);
                String feedbackSummary = snapshot.child("feedbackSummary").getValue(String.class);

                if (savedAnswer != null && !savedAnswer.trim().isEmpty()) {
                    if (view != null) {
                        view.displaySavedAnswer(savedAnswer);
                    }
                    hasLoadedSavedAnswer = true;
                } else {
                    hasLoadedSavedAnswer = false; // No answer found
                    Log.d(TAG, "No saved answer found for user " + userIdForPath + ", exercise " + exerciseTitle);
                }
                // Always call checkIfAllDataLoadedAndReady to proceed with UI setup
                checkIfAllDataLoadedAndReady();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load saved user answer.", error.toException());
                hasLoadedSavedAnswer = false; // Treat as no answer found on error
                checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void checkIfAllDataLoadedAndReady() {
        Log.d(TAG, "checkIfAllDataLoadedAndReady: exerciseDataLoaded=" + exerciseDataLoaded +
                ", titlesLoaded=" + titlesLoaded + ". Saved answer status known (hasLoadedSavedAnswer=" + hasLoadedSavedAnswer +")");
        if (exerciseDataLoaded && titlesLoaded) {
            Log.d(TAG, "All initial data (exercise, titles) confirmed loaded.");
            if (view == null) return;

            view.setUIElementsVisibility(true); // Make core UI visible
            view.setAnswerInputVisibility(true); // Ensure answer input area is potentially visible

            boolean isContentEffectivelyUnavailable = currentWritingExercise == null ||
                    currentWritingExercise.getScript() == null ||
                    currentWritingExercise.getScript().isEmpty() ||
                    currentWritingExercise.getScript().contains("not available") ||
                    currentWritingExercise.getScript().contains("not found") ||
                    currentWritingExercise.getScript().equals("Content loading error.");


            if (isContentEffectivelyUnavailable) {
                Log.d(TAG, "Content is unavailable for the exercise.");
                currentButtonState = STATE_SUBMIT_WRITING; // Or a specific "unavailable" state if you add one
            } else if (hasLoadedSavedAnswer) {
                Log.d(TAG, "Content available AND saved answer loaded. Setting state to RETRY_WRITING.");
                currentButtonState = STATE_RETRY_WRITING;
                String savedAnswerText = view.getCurrentAnswerText(); // Get it from view as it was just set by displaySavedAnswer
                submittedTextForCurrentFeedback = (savedAnswerText != null) ? savedAnswerText : "";
                FirebaseUser fbUser = mAuth.getCurrentUser();
                String userId = (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null) ? currentMicrosoftUser.getUserId() : (fbUser != null ? fbUser.getUid() : null);
                if (userId != null && exerciseTitle != null) {
                    databaseReference.child("Users").child("MicrosoftUsers").child(userId)
                            .child("Progress").child("WritingAnswers").child(exerciseTitle)
                            .child("feedbackSummary").addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    String summary = snapshot.getValue(String.class);
                                    if (summary != null && summary.equalsIgnoreCase("All criteria success")) {
                                        allCriteriaSuccess = true;
                                    } else {
                                        allCriteriaSuccess = false;
                                    }
                                    Log.d(TAG, "Retrieved feedbackSummary for loaded answer: " + summary + ", allCriteriaSuccess: " + allCriteriaSuccess);
                                    editButtonForcedByLoad = true; // THIS IS KEY: loaded answer means "Edit" button state
                                    updateSubmitButtonBasedOnState(); // Update now that allCriteriaSuccess is potentially known
                                }
                                @Override public void onCancelled(@NonNull DatabaseError error) {
                                    allCriteriaSuccess = false; // Default on error
                                    Log.w(TAG, "Error fetching feedbackSummary for loaded answer: " + error.getMessage());
                                    editButtonForcedByLoad = true;
                                    updateSubmitButtonBasedOnState();
                                }
                            });
                } else {
                    allCriteriaSuccess = false; // Default if cannot fetch
                    editButtonForcedByLoad = true;
                    updateSubmitButtonBasedOnState();
                }
                return; // Important: return here to wait for async feedbackSummary load
            } else {
                Log.d(TAG, "Content available, NO saved answer. Setting state to SUBMIT_WRITING.");
                currentButtonState = STATE_SUBMIT_WRITING;
                isUserEditingAfterFeedback = false; // Fresh start
                allCriteriaSuccess = false;
                editButtonForcedByLoad = false;
            }
            updateSubmitButtonBasedOnState(); // Call for non-loaded answer case or content unavailable
        } else {
            Log.d(TAG, "Still waiting for some initial data (exercise/titles) to load before full UI setup.");
        }
    }


    private void updateSubmitButtonBasedOnState() {
        if (view == null) return;
        Log.d(TAG, "updateSubmitButtonBasedOnState - CurrentState: " + currentButtonState +
                ", isEditingAfterFeedback: " + isUserEditingAfterFeedback +
                ", allSuccess: " + allCriteriaSuccess +
                ", isFeedbackPanelVisible (flag): " + isFeedbackPanelVisible +
                ", editButtonForcedByLoad (flag): " + editButtonForcedByLoad);


        boolean contentNotAvailable = currentWritingExercise == null || currentWritingExercise.getScript() == null ||
                currentWritingExercise.getScript().equals("Writing prompt not available for this exercise.") ||
                currentWritingExercise.getScript().equals("Writing prompt not found.") ||
                currentWritingExercise.getScript().equals("Content not available for this exercise.") ||
                currentWritingExercise.getScript().equals("Content loading error.");


        if (contentNotAvailable) {
            view.setSubmitButtonState("N/A", false);
            view.setFeedbackPanelVisibility(false);
            view.setFeedbackTriggerVisibility(false);
            // view.setSeeRevisedVersionButtonVisibility(false); // REMOVED
            view.setAnswerEditTextEnabled(false);
            if (currentWritingExercise == null && exerciseDataLoaded) { // If exerciseData was "loaded" but object is null
                view.displayQuestionPrompt("Content not available for this exercise.");
            }
            isFeedbackPanelVisible = false; // Ensure flag is also false
            return;
        }

        if (currentButtonState == STATE_SUBMIT_WRITING) {
            view.setAnswerEditTextEnabled(true);
            view.setFeedbackPanelVisibility(false); // Hide feedback panel
            view.setFeedbackTriggerVisibility(false); // Hide "Review Feedback"
            // view.setSeeRevisedVersionButtonVisibility(false); // REMOVED
            String currentText = view.getCurrentAnswerText();
            onAnswerTextChanged(currentText != null ? currentText : ""); // This will enable/disable submit button
            isFeedbackPanelVisible = false; // Update flag
            // editButtonForcedByLoad is not relevant for SUBMIT_WRITING state by definition
        } else if (currentButtonState == STATE_RETRY_WRITING) {
            // This state means feedback HAS been given OR an answer was loaded.
            view.setAnswerEditTextEnabled(false); // Generally, disable editing until "Edit" is clicked.

            if (editButtonForcedByLoad) { // Just loaded a saved answer
                Log.d(TAG, "STATE_RETRY_WRITING (Post-Load/editButtonForcedByLoad=true): Setting button to 'Edit'.");
                view.setSubmitButtonState("Edit", true);
                view.setFeedbackPanelVisibility(false); // Initially hide feedback when loaded
                view.setFeedbackTriggerVisibility(false); // And trigger
                isFeedbackPanelVisible = false;
            } else { // Feedback has been processed from a submission (not a load)
                Log.d(TAG, "STATE_RETRY_WRITING (Post-Submit/editButtonForcedByLoad=false): Setting button based on allCriteriaSuccess.");
                if (allCriteriaSuccess) {
                    view.setSubmitButtonState("Done", true);
                } else {
                    view.setSubmitButtonState("Edit", true);
                }
                view.setFeedbackTriggerVisibility(true); // Show "Review Feedback"
                view.setFeedbackPanelVisibility(isFeedbackPanelVisible);
            }
        }

    }


    public void onAnswerTextChanged(String currentText) {
        if (view == null) return;
        if (currentButtonState == STATE_SUBMIT_WRITING) {
            boolean canSubmit = !TextUtils.isEmpty(currentText == null ? "" : currentText.trim());
            if (isUserEditingAfterFeedback) {
                // If editing after feedback, also check if text has actually changed from submitted
                boolean hasChanged = !currentText.trim().equals(submittedTextForCurrentFeedback.trim());
                view.setSubmitButtonState("Submit", canSubmit && hasChanged);
            } else {
                view.setSubmitButtonState("Submit", canSubmit);
            }
        }
    }


    public void onSubmitButtonClicked(String userAnswerFromView) {
        if (view == null) return;
        Log.d(TAG, "Submit button clicked. State: " + currentButtonState + ", AllSuccess: " + allCriteriaSuccess +
                ", EditingAfterFeedback: " + isUserEditingAfterFeedback + ", editButtonForcedByLoad: " + editButtonForcedByLoad);

        if (inlineAnalysisRunnable != null) {
            inlineAnalysisHandler.removeCallbacks(inlineAnalysisRunnable);
        }
        view.clearInlineErrorHighlighting();

        switch (currentButtonState) {
            case STATE_SUBMIT_WRITING:
                allCriteriaSuccess = false; // Reset for new submission
                if (userAnswerFromView.trim().isEmpty()) {
                    view.showFailToast("Please write something before submitting.");
                    return;
                }
                if (currentWritingExercise == null || currentWritingExercise.getScript() == null || currentWritingExercise.getScript().isEmpty() ||
                        currentWritingExercise.getScript().contains("not available") || currentWritingExercise.getScript().contains("not found")) {
                    view.showFailToast("Cannot submit without a valid writing prompt.");
                    return;
                }
                view.showConfirmationDialog("Confirm Submission", "Are you sure you want to submit your writing for feedback?", () -> {
                    proceedWithWritingSubmission(userAnswerFromView, currentWritingExercise.getScript());
                });
                break;

            case STATE_RETRY_WRITING:
                if (allCriteriaSuccess && !editButtonForcedByLoad) { // If "Done" button is clicked
                    Log.i(TAG, "'Done' button clicked. Finishing activity.");
                    view.finishActivity();
                } else { // If "Edit" button is clicked (either from loaded state or after non-success feedback)
                    Log.i(TAG, "'Edit' button clicked. Enabling edit mode.");
                    currentButtonState = STATE_SUBMIT_WRITING; // Switch to submission mode
                    isUserEditingAfterFeedback = true;         // Mark that user is editing post-feedback/load
                    isFeedbackPanelVisible = false;          // Hide feedback panel when editing starts
                    if(editButtonForcedByLoad) {
                        submittedTextForCurrentFeedback = view.getCurrentAnswerText().trim(); // Ensure this is set if loading
                    }
                    editButtonForcedByLoad = false;            // CRITICAL: Reset this flag as we are now actively editing

                    updateSubmitButtonBasedOnState();          // Update button to "Submit" (likely disabled initially)

                    // Ensure submit button state reflects current text (might be empty or unchanged)
                    String currentAnswerInView = view.getCurrentAnswerText();
                    onAnswerTextChanged(currentAnswerInView != null ? currentAnswerInView : userAnswerFromView);

                    view.requestFocusOnAnswerInput();        // Focus on the input field
                }
                break;
            default:
                Log.w(TAG, "Unknown button state clicked: " + currentButtonState);
                // Reset to a known safe state
                currentButtonState = STATE_SUBMIT_WRITING;
                isUserEditingAfterFeedback = false;
                allCriteriaSuccess = false;
                submittedTextForCurrentFeedback = "";
                isFeedbackPanelVisible = false;
                editButtonForcedByLoad = false;
                updateSubmitButtonBasedOnState();
                break;
        }
    }

    private void proceedWithWritingSubmission(String userAnswer, String originalPrompt) {
        if (view == null) return;
        Log.i(TAG, "Proceeding with writing submission for text: " + userAnswer);
        view.showLoading("Getting feedback...");
        final String textBeingSubmitted = userAnswer; // Capture the text at this moment

        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        String userIdToUseForSaving = null;

        // Determine userIdForPath for saving
        if (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null && !currentMicrosoftUser.getUserId().isEmpty()) {
            userIdToUseForSaving = currentMicrosoftUser.getUserId();
        } else if (firebaseUser != null) {
            userIdToUseForSaving = firebaseUser.getUid();
        }


        if (userIdToUseForSaving != null && currentWritingExercise != null) {
            saveUserWritingAnswerToFirebase(
                    userIdToUseForSaving,
                    currentWritingExercise.getId(), // Should be exerciseTitle or a specific ID from DB
                    levelName,
                    topicTitle,
                    textBeingSubmitted, // Use the captured text
                    "Feedback pending..." // Initial summary
            );
        } else {
            Log.w(TAG, "Cannot save user answer: User ID or Exercise context is missing.");
            if (currentWritingExercise == null) Log.w(TAG, "currentWritingExercise is null");
            if (userIdToUseForSaving == null) Log.w(TAG, "userIdToUseForSaving is null");
        }

        final String finalUserIdForFeedbackUpdate = userIdToUseForSaving;

        getFeedbackFromGemini(originalPrompt, textBeingSubmitted, new FeedbackCallback() { // Use captured text
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
                        currentButtonState = STATE_RETRY_WRITING;    // Move to state where user can see feedback/edit
                        isUserEditingAfterFeedback = false;         // IMPORTANT: Reset, as feedback is now shown. If user edits, this becomes true.
                        submittedTextForCurrentFeedback = textBeingSubmitted; // Store the text for which feedback was given
                        isFeedbackPanelVisible = true;              // Show feedback panel by default after getting feedback
                        editButtonForcedByLoad = false;             // This was a submission, not a load

                        updateSubmitButtonBasedOnState();           // Update button to "Edit" or "Done"
                        if (isFeedbackPanelVisible) view.focusOnFeedbackPanel(); // Scroll to feedback

                        // Update Firebase with the feedback summary
                        if (finalUserIdForFeedbackUpdate != null && currentWritingExercise != null) {
                            String feedbackSummary = allCriteriaSuccess ? "All criteria success" : "Needs improvement";
                            updateUserWritingAnswerFeedback(
                                    finalUserIdForFeedbackUpdate,
                                    currentWritingExercise.getId(), // exerciseTitle or ID
                                    feedbackSummary
                            );
                        }

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing overall feedback JSON from Gemini", e);
                        if (view != null) view.showFailToast("Error processing feedback: " + e.getMessage());
                        isFeedbackPanelVisible = false; // Hide panel on error
                        currentButtonState = STATE_SUBMIT_WRITING; // Revert to submit if feedback processing fails
                        // editButtonForcedByLoad remains false
                        updateSubmitButtonBasedOnState();
                    }
                });
            }
            @Override
            public void onError(String error) {
                mainThreadHandler.post(() -> {
                    if (view == null) return;
                    view.hideLoading();
                    Log.e(TAG, "Error getting feedback from Gemini: " + error);
                    view.showFailToast("Failed to get feedback: " + error);
                    isFeedbackPanelVisible = false; // Hide panel
                    // Don't change currentButtonState here, allow user to retry submission if they wish,
                    // or it might revert to SUBMIT_WRITING if appropriate.
                    // For now, keep current state, button would still be "Submit"
                    // editButtonForcedByLoad remains false
                    updateSubmitButtonBasedOnState(); // Reflect that loading is hidden, button might re-enable
                });
            }
        });
    }

    private void saveUserWritingAnswerToFirebase(String userId, String exerciseId, String level, String topic, String answer, String initialFeedbackSummary) {
        if (userId == null || exerciseId == null) {
            Log.w(TAG, "Cannot save user writing answer: userId or exerciseId is null.");
            return;
        }
        // Ensure exerciseId from currentWritingExercise is used if available and valid
        String currentExerciseId = (currentWritingExercise != null && currentWritingExercise.getId() != null)
                ? currentWritingExercise.getId() : exerciseId;
        if (currentExerciseId == null) {
            Log.e(TAG, "Exercise ID is null, cannot save answer.");
            return;
        }


        DatabaseReference userAnswersRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers")
                .child(userId)
                .child("Progress")
                .child("WritingAnswers")
                .child(currentExerciseId); // Use the resolved ID

        UserWritingAnswer userAnswer = new UserWritingAnswer(
                currentExerciseId,
                level,
                topic,
                answer,
                System.currentTimeMillis(),
                initialFeedbackSummary
        );

        userAnswersRef.setValue(userAnswer.toMap())
                .addOnSuccessListener(aVoid -> Log.i(TAG, "User writing answer saved successfully for user: " + userId + ", exercise: " + currentExerciseId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save user writing answer for user: " + userId + ", exercise: " + currentExerciseId, e));
    }

    private void updateUserWritingAnswerFeedback(String userId, String exerciseId, String feedbackSummary) {
        if (userId == null || exerciseId == null) {
            Log.w(TAG, "Cannot update feedback summary: userId or exerciseId is null.");
            return;
        }
        String currentExerciseId = (currentWritingExercise != null && currentWritingExercise.getId() != null)
                ? currentWritingExercise.getId() : exerciseId;
        if (currentExerciseId == null) {
            Log.e(TAG, "Exercise ID is null, cannot update feedback.");
            return;
        }

        DatabaseReference userAnswerRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers")
                .child(userId)
                .child("Progress")
                .child("WritingAnswers")
                .child(currentExerciseId); // Use the resolved ID

        Map<String, Object> updates = new HashMap<>();
        updates.put("feedbackSummary", feedbackSummary);
        updates.put("lastUpdatedTimestamp", System.currentTimeMillis());

        userAnswerRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.i(TAG, "Feedback summary updated successfully for user: " + userId + ", exercise: " + currentExerciseId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update feedback summary for user: " + userId + ", exercise: " + currentExerciseId, e));
    }

    interface FeedbackCallback {
        void onSuccess(JSONObject feedbackJson);
        void onError(String error);
    }

    private void getFeedbackFromGemini(String originalPrompt, String userAnswer, FeedbackCallback callback) {
        String promptForGemini = "You are an English language learning assistant. Evaluate the following written response to the prompt.\n" +
                "Provide constructive feedback for a language learner.\n" +
                "Your response MUST be a JSON object with the following exact structure:\n" +
                "{\n" +
                "  \"taskResponse\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>},\n" +
                "  \"coherenceCohesion\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>},\n" +
                "  \"grammarVocabulary\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>},\n" +
                "  \"length\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>}\n" +
                "}\n" +
                "Be concise in your feedback strings.\n\n" +
                "IMPORTANT rules for iconType:\n" +
                "- For \"taskResponse\", \"coherenceCohesion\", and \"length\", iconType should be 1 if the user generally did well in that aspect, 0 if there are notable issues.\n" +
                "- For \"grammarVocabulary\": Be very strict. iconType MUST be 0 if ANY grammatical errors (e.g., subject-verb agreement, tense, articles, prepositions, sentence structure) OR vocabulary errors (e.g., incorrect word choice, wrong form of word, significant spelling mistakes that change word meaning or make it unrecognizable) are present. iconType for grammarVocabulary should only be 1 if the response is free of such errors and uses appropriate vocabulary.\n" +
                "- If you identify errors for grammarVocabulary and set its iconType to 0, ensure your feedback string for grammarVocabulary briefly mentions the type or an example of the error.\n\n" +
                "Original Prompt: \"" + originalPrompt + "\"\n\n" +
                "User's Response: \"" + userAnswer + "\"\n\n" +
                "Provide only the JSON object as your response.";

        callGeminiAPI(promptForGemini, responseString -> {
            try {
                callback.onSuccess(new JSONObject(responseString));
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing overall feedback JSON from Gemini", e);
                callback.onError("Invalid JSON format from AI for overall feedback.");
            }
        }, callback::onError);
    }


    private interface GeminiApiSuccessListener {
        void onResult(String responseString) throws JSONException;
    }

    private interface GeminiApiErrorListener {
        void onError(String errorMessage);
    }

    private void callGeminiAPI(String promptText, GeminiApiSuccessListener successListener, GeminiApiErrorListener errorListener) {
        if (GEMINI_API_KEY.equals("AIzaSyAKitUIzcsW3Gd5SyeTLTrcGnJcPTDG09c") || GEMINI_API_KEY.isEmpty() || GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY") ) { // Check for placeholder
            Log.e(TAG, "Gemini API Key is a placeholder, empty, or a sample key. Please set a valid API key.");
            String simulatedError = "AI Feedback service is temporarily unavailable (API Key issue).";
            // Simulate async error
            mainThreadHandler.postDelayed(() -> errorListener.onError(simulatedError), 200);
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

                JSONObject jsonBody = new JSONObject();
                JSONArray contentsArray = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray partsArray = new JSONArray();
                JSONObject part = new JSONObject();
                part.put("text", promptText);
                partsArray.put(part);
                content.put("parts", partsArray);
                contentsArray.put(content);
                jsonBody.put("contents", contentsArray);

                // Ensure response is JSON
                JSONObject generationConfig = new JSONObject();
                generationConfig.put("response_mime_type", "application/json");
                jsonBody.put("generationConfig", generationConfig);


                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes("utf-8"));
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

                        Log.d(TAG, "Raw Gemini Response: " + response.toString());
                        JSONObject fullJsonResponse = new JSONObject(response.toString());

                        // Standard Gemini 1.5 Flash format
                        if (fullJsonResponse.has("candidates") && fullJsonResponse.getJSONArray("candidates").length() > 0) {
                            JSONObject firstCandidate = fullJsonResponse.getJSONArray("candidates").getJSONObject(0);
                            if (firstCandidate.has("content") && firstCandidate.getJSONObject("content").has("parts") &&
                                    firstCandidate.getJSONObject("content").getJSONArray("parts").length() > 0) {
                                String resultText = firstCandidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");

                                // Remove markdown ```json ... ``` if present
                                if (resultText.startsWith("```json")) {
                                    resultText = resultText.substring(7); // Length of "```json\n" or "```json "
                                    if (resultText.endsWith("```")) {
                                        resultText = resultText.substring(0, resultText.length() - 3);
                                    }
                                } else if (resultText.startsWith("```")) { // For just ```
                                    resultText = resultText.substring(3);
                                    if (resultText.endsWith("```")) {
                                        resultText = resultText.substring(0, resultText.length() - 3);
                                    }
                                }
                                successListener.onResult(resultText.trim()); // Pass the cleaned JSON string
                            } else {
                                throw new JSONException("Parts array is missing or empty in Gemini response candidate.");
                            }
                        } else {
                            // Check for prompt feedback (e.g. blocked due to safety)
                            if (fullJsonResponse.has("promptFeedback")) {
                                String blockReason = "Blocked by API (Safety Settings)";
                                if(fullJsonResponse.getJSONObject("promptFeedback").has("blockReason")){
                                    blockReason += ": " + fullJsonResponse.getJSONObject("promptFeedback").getString("blockReason");
                                }
                                Log.e(TAG, "Gemini API blocked the prompt: " + fullJsonResponse.getJSONObject("promptFeedback").toString());
                                throw new JSONException(blockReason);
                            }
                            throw new JSONException("Candidates array is missing or empty in Gemini response.");
                        }
                    }
                } else {
                    // Handle error stream
                    StringBuilder errorResponse = new StringBuilder();
                    if (conn.getErrorStream() != null) {
                        try (BufferedReader brError = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                            String line;
                            while ((line = brError.readLine()) != null) {
                                errorResponse.append(line.trim());
                            }
                        }
                    } else {
                        errorResponse.append("No error stream data. HTTP Status: ").append(conn.getResponseMessage());
                    }
                    Log.e(TAG, "Gemini API Error Response (HTTP " + responseCode + "): " + errorResponse.toString());
                    errorListener.onError("Server error: " + responseCode + ". Details: " + errorResponse.toString());
                }
            } catch (Exception e) { // Catch broader exceptions like IOException, JSONException
                Log.e(TAG, "Error calling/processing Gemini API", e);
                errorListener.onError("Client-side error during API call: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }


    public void onPause() {
        Log.d(TAG, "onPause called by View.");
        if (inlineAnalysisRunnable != null) {
            inlineAnalysisHandler.removeCallbacks(inlineAnalysisRunnable);
        }
        // Any other cleanup specific to pausing the controller's operations
    }

    public void onResume() {
        Log.d(TAG, "onResume. Re-evaluating UI state if necessary.");
        if (exerciseDataLoaded && titlesLoaded && view != null) {
            updateSubmitButtonBasedOnState();
            if (currentButtonState == STATE_SUBMIT_WRITING) { // If user can type
                String currentText = view.getCurrentAnswerText();
                onAnswerTextChanged(currentText != null ? currentText : ""); // Re-check submit button enabled state
            }
        } else {
            Log.d(TAG, "onResume: Core data not yet fully loaded, UI update will be handled by loading callbacks.");
        }
    }


    public void onDestroy() {
        Log.d(TAG, "onDestroy called.");
        if (inlineAnalysisRunnable != null) {
            inlineAnalysisHandler.removeCallbacks(inlineAnalysisRunnable);
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // Attempt to stop all actively executing tasks
        }
        this.view = null; // Important to prevent memory leaks
    }

    public void onReviewFeedbackClicked() {
        if (view == null) return;
        Log.d(TAG, "Review Feedback clicked. Current isFeedbackPanelVisible (flag): " + isFeedbackPanelVisible);
        isFeedbackPanelVisible = !isFeedbackPanelVisible; // Toggle the flag
        view.setFeedbackPanelVisibility(isFeedbackPanelVisible);
        if (isFeedbackPanelVisible) {
            view.focusOnFeedbackPanel(); // Scroll to panel if it's now visible
        }
    }

    // Getter methods for View to query controller state for onBackPressed logic
    public boolean handleBackPressed() {
        return false;
    }
    public int getCurrentButtonState() { return currentButtonState; }
    public boolean areAllCriteriaSuccess() { return this.allCriteriaSuccess; }
    public boolean isUserEditingAfterFeedback() { return this.isUserEditingAfterFeedback; }
    public String getSubmittedTextForCurrentFeedback() { return this.submittedTextForCurrentFeedback; }
    public boolean isEditButtonForcedByLoad() { return this.editButtonForcedByLoad; }
}
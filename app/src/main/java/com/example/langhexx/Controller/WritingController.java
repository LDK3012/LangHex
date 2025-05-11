package com.example.langhexx.Controller;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.langhexx.Model.MicrosoftUser;
// import com.example.langhexx.Model.UserWritingAnswer; // REMOVE THIS IMPORT
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
    private String exerciseTitle; // This is typically the ID/key for the exercise
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
            exerciseTitle = intent.getStringExtra("EXERCISE_TITLE"); // This is the KEY for the exercise
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
            // Even if no user, we might still want to load exercise content if public
            // but loadSavedUserAnswerFromFirebase() will not find anything.
            // For this refactor, assuming user context is usually present for progress.
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
                        currentMicrosoftUser = new MicrosoftUser(firebaseUid, email, displayName);
                        Log.d(TAG, "Using Firebase UID as key for user data: " + firebaseUid);
                    }
                } else {
                    Log.w(TAG, "User data node not found for UID: " + firebaseUid);
                    // Create a shell MicrosoftUser object if main user node doesn't exist but we have UID
                    // This might happen for a new user who hasn't had their /Users/{uid} node created yet
                    // but has authenticated. For saving progress, we need a currentMicrosoftUser object.
                    FirebaseUser fbUser = mAuth.getCurrentUser();
                    if (fbUser != null) {
                        currentMicrosoftUser = new MicrosoftUser(firebaseUid, fbUser.getEmail(), fbUser.getDisplayName());
                        Log.d(TAG, "Created a shell MicrosoftUser for UID: " + firebaseUid);
                    }
                }
                loadSavedUserAnswerFromFirebase(); // Load saved answer after user identity is established
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load Microsoft user data.", error.toException());
                // Still try to load saved answer, though it might not find user-specific path correctly
                // if currentMicrosoftUser is null.
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
        view.setAnswerEditTextEnabled(true);

        loadExerciseDataFromFirebase();
        loadAllExerciseTitlesFromFirebase();
        // loadSavedUserAnswerFromFirebase() is now called after loadMicrosoftUserData()
    }

    private void handleInitializationError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (view != null) { view.showFailToast(errorMessage); view.finishActivity(); }
    }

    private void loadExerciseDataFromFirebase() {
        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            if(view != null) { Log.e(TAG, "Cannot load exercise data: Level/Topic/ExerciseTitle is null."); view.showFailToast("Error: Missing data to load exercise."); view.finishActivity(); }
            exerciseDataLoaded = true;
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
                    currentWritingExercise = null;
                } else {
                    String scriptPrompt = snapshot.child("script").getValue(String.class);
                    String titleFromDb = snapshot.child("title").getValue(String.class);
                    String currentExKey = snapshot.getKey(); // This should be == exerciseTitle

                    // Use titleFromDb for display, but currentExKey (==exerciseTitle) for identification
                    String displayTitle = titleFromDb != null ? titleFromDb : exerciseTitle;


                    if (scriptPrompt != null && !scriptPrompt.isEmpty()) {
                        currentWritingExercise = new WritingExercise(currentExKey, displayTitle, scriptPrompt);
                        if (view != null) {
                            view.displayExerciseTitle(currentWritingExercise.getTitle());
                            view.displayQuestionPrompt(currentWritingExercise.getScript());
                        }
                    } else {
                        currentWritingExercise = new WritingExercise(currentExKey, displayTitle, "Writing prompt not available for this exercise.");
                        if (view != null) {
                            view.displayExerciseTitle(displayTitle);
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
                    view.displayExerciseTitle(exerciseTitle != null ? exerciseTitle : "Exercise");
                    view.displayQuestionPrompt("Content loading error.");
                }
                currentWritingExercise = null;
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
                titlesLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void loadSavedUserAnswerFromFirebase() {
        if (currentMicrosoftUser == null || currentMicrosoftUser.getUserId() == null || currentMicrosoftUser.getUserId().isEmpty()) {
            Log.w(TAG, "Cannot load saved answer: MicrosoftUser or UserId is null/empty.");
            hasLoadedSavedAnswer = false;
            checkIfAllDataLoadedAndReady();
            return;
        }
        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            Log.w(TAG, "Cannot load saved answer: Missing exercise identifiers.");
            hasLoadedSavedAnswer = false;
            checkIfAllDataLoadedAndReady();
            return;
        }

        final String userIdForPath = currentMicrosoftUser.getUserId();
        Log.d(TAG, "Loading saved answer for user: " + userIdForPath + ", exercise: " + exerciseTitle);

        DatabaseReference answerRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers")
                .child(userIdForPath)
                .child("Progress")
                .child("WritingAnswers")
                .child(exerciseTitle); // exerciseTitle is the key for the answer

        answerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String savedAnswer = snapshot.child("userAnswer").getValue(String.class);
                    String feedbackSummary = snapshot.child("feedbackSummary").getValue(String.class);
                    Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                    // String dbExerciseId = snapshot.child("exerciseId").getValue(String.class); // This should match exerciseTitle
                    // String dbLevelName = snapshot.child("levelName").getValue(String.class); // Match levelName
                    // String dbTopicTitle = snapshot.child("topicTitle").getValue(String.class); // Match topicTitle

                    // Populate the writing-specific fields in currentMicrosoftUser
                    currentMicrosoftUser.setWritingExerciseId(exerciseTitle); // The key of the exercise itself
                    currentMicrosoftUser.setWritingLevelName(levelName);
                    currentMicrosoftUser.setWritingTopicTitle(topicTitle);
                    currentMicrosoftUser.setWritingUserAnswer(savedAnswer != null ? savedAnswer : "");
                    currentMicrosoftUser.setWritingFeedbackSummary(feedbackSummary != null ? feedbackSummary : "");
                    currentMicrosoftUser.setWritingTimestamp(timestamp != null ? timestamp : 0L);

                    if (savedAnswer != null && !savedAnswer.trim().isEmpty()) {
                        if (view != null) {
                            view.displaySavedAnswer(savedAnswer);
                        }
                        hasLoadedSavedAnswer = true;
                        Log.d(TAG, "Loaded saved answer for user " + userIdForPath + ", exercise " + exerciseTitle);
                    } else {
                        hasLoadedSavedAnswer = false;
                        Log.d(TAG, "No saved answer text found for user " + userIdForPath + ", exercise " + exerciseTitle);
                    }
                } else {
                    hasLoadedSavedAnswer = false;
                    Log.d(TAG, "No saved answer node found for user " + userIdForPath + ", exercise " + exerciseTitle);
                }
                checkIfAllDataLoadedAndReady();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load saved user answer.", error.toException());
                hasLoadedSavedAnswer = false;
                checkIfAllDataLoadedAndReady();
            }
        });
    }


    private void checkIfAllDataLoadedAndReady() {
        Log.d(TAG, "checkIfAllDataLoadedAndReady: exerciseDataLoaded=" + exerciseDataLoaded +
                ", titlesLoaded=" + titlesLoaded + ". Saved answer status known (hasLoadedSavedAnswer=" + hasLoadedSavedAnswer +")");
        if (exerciseDataLoaded && titlesLoaded && currentMicrosoftUser != null) { // Ensure currentMicrosoftUser is also available
            Log.d(TAG, "All initial data (exercise, titles, user context) confirmed loaded.");
            if (view == null) return;

            view.setUIElementsVisibility(true);
            view.setAnswerInputVisibility(true);

            boolean isContentEffectivelyUnavailable = currentWritingExercise == null ||
                    currentWritingExercise.getScript() == null ||
                    currentWritingExercise.getScript().isEmpty() ||
                    currentWritingExercise.getScript().contains("not available") ||
                    currentWritingExercise.getScript().contains("not found") ||
                    currentWritingExercise.getScript().equals("Content loading error.");


            if (isContentEffectivelyUnavailable) {
                Log.d(TAG, "Content is unavailable for the exercise.");
                currentButtonState = STATE_SUBMIT_WRITING;
            } else if (hasLoadedSavedAnswer) {
                Log.d(TAG, "Content available AND saved answer loaded. Setting state to RETRY_WRITING.");
                currentButtonState = STATE_RETRY_WRITING;
                // User's answer already loaded into currentMicrosoftUser.writingUserAnswer
                // and view.displaySavedAnswer called from loadSavedUserAnswerFromFirebase
                submittedTextForCurrentFeedback = currentMicrosoftUser.getWritingUserAnswer() != null ? currentMicrosoftUser.getWritingUserAnswer() : "";

                // Check feedback summary directly from the populated currentMicrosoftUser or fetch again if needed
                // For this refactor, assume currentMicrosoftUser.getWritingFeedbackSummary() is sufficient.
                if (currentMicrosoftUser.getWritingFeedbackSummary() != null &&
                        currentMicrosoftUser.getWritingFeedbackSummary().equalsIgnoreCase("All criteria success")) {
                    allCriteriaSuccess = true;
                } else {
                    allCriteriaSuccess = false;
                }
                Log.d(TAG, "Retrieved feedbackSummary for loaded answer: " + currentMicrosoftUser.getWritingFeedbackSummary() + ", allCriteriaSuccess: " + allCriteriaSuccess);
                editButtonForcedByLoad = true;
                updateSubmitButtonBasedOnState();
                // No need to return for async here as data is already in currentMicrosoftUser.
            } else {
                Log.d(TAG, "Content available, NO saved answer. Setting state to SUBMIT_WRITING.");
                currentButtonState = STATE_SUBMIT_WRITING;
                isUserEditingAfterFeedback = false;
                allCriteriaSuccess = false;
                editButtonForcedByLoad = false;
                // Clear any previous writing data in currentMicrosoftUser for this context
                if(currentMicrosoftUser != null) {
                    currentMicrosoftUser.setWritingUserAnswer("");
                    currentMicrosoftUser.setWritingFeedbackSummary("");
                    currentMicrosoftUser.setWritingTimestamp(0L);
                    // exerciseId, levelName, topicTitle for writing context are already set or will be set on save
                    currentMicrosoftUser.setWritingExerciseId(exerciseTitle);
                    currentMicrosoftUser.setWritingLevelName(levelName);
                    currentMicrosoftUser.setWritingTopicTitle(topicTitle);
                }
            }
            updateSubmitButtonBasedOnState();
        } else {
            Log.d(TAG, "Still waiting for some initial data (exercise/titles/user context) to load before full UI setup. CurrentMicrosoftUser null? " + (currentMicrosoftUser == null));
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
            view.setAnswerEditTextEnabled(false);
            if (currentWritingExercise == null && exerciseDataLoaded) {
                view.displayQuestionPrompt("Content not available for this exercise.");
            }
            isFeedbackPanelVisible = false;
            return;
        }

        if (currentButtonState == STATE_SUBMIT_WRITING) {
            view.setAnswerEditTextEnabled(true);
            view.setFeedbackPanelVisibility(false);
            view.setFeedbackTriggerVisibility(false);
            String currentText = view.getCurrentAnswerText();
            onAnswerTextChanged(currentText != null ? currentText : "");
            isFeedbackPanelVisible = false;
        } else if (currentButtonState == STATE_RETRY_WRITING) {
            view.setAnswerEditTextEnabled(false);

            if (editButtonForcedByLoad) {
                Log.d(TAG, "STATE_RETRY_WRITING (Post-Load/editButtonForcedByLoad=true): Setting button to 'Edit'.");
                view.setSubmitButtonState("Edit", true);
                view.setFeedbackPanelVisibility(false);
                view.setFeedbackTriggerVisibility(false);
                isFeedbackPanelVisible = false;
            } else {
                Log.d(TAG, "STATE_RETRY_WRITING (Post-Submit/editButtonForcedByLoad=false): Setting button based on allCriteriaSuccess.");
                if (allCriteriaSuccess) {
                    view.setSubmitButtonState("Done", true);
                } else {
                    view.setSubmitButtonState("Edit", true);
                }
                view.setFeedbackTriggerVisibility(true);
                view.setFeedbackPanelVisibility(isFeedbackPanelVisible);
            }
        }
    }


    public void onAnswerTextChanged(String currentText) {
        if (view == null) return;
        if (currentButtonState == STATE_SUBMIT_WRITING) {
            boolean canSubmit = !TextUtils.isEmpty(currentText == null ? "" : currentText.trim());
            if (isUserEditingAfterFeedback) {
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
                allCriteriaSuccess = false;
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
                if (allCriteriaSuccess && !editButtonForcedByLoad) {
                    Log.i(TAG, "'Done' button clicked. Finishing activity.");
                    view.finishActivity();
                } else {
                    Log.i(TAG, "'Edit' button clicked. Enabling edit mode.");
                    currentButtonState = STATE_SUBMIT_WRITING;
                    isUserEditingAfterFeedback = true;
                    isFeedbackPanelVisible = false;
                    if(editButtonForcedByLoad && currentMicrosoftUser != null) {
                        // Ensure submittedTextForCurrentFeedback is from the loaded answer
                        submittedTextForCurrentFeedback = currentMicrosoftUser.getWritingUserAnswer() !=null ? currentMicrosoftUser.getWritingUserAnswer().trim() : "";
                    }
                    editButtonForcedByLoad = false;

                    updateSubmitButtonBasedOnState();

                    String currentAnswerInView = view.getCurrentAnswerText();
                    onAnswerTextChanged(currentAnswerInView != null ? currentAnswerInView : userAnswerFromView);

                    view.requestFocusOnAnswerInput();
                }
                break;
            default:
                Log.w(TAG, "Unknown button state clicked: " + currentButtonState);
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
        final String textBeingSubmitted = userAnswer;

        String userIdToUseForSaving = null;
        if (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null && !currentMicrosoftUser.getUserId().isEmpty()) {
            userIdToUseForSaving = currentMicrosoftUser.getUserId();
        } else {
            Log.w(TAG, "Cannot save user answer: User ID from currentMicrosoftUser is missing.");
            // Attempt to get from FirebaseAuth directly as a last resort, though currentMicrosoftUser should be source of truth
            FirebaseUser firebaseUser = mAuth.getCurrentUser();
            if (firebaseUser != null) {
                userIdToUseForSaving = firebaseUser.getUid();
                Log.w(TAG, "Falling back to FirebaseAuth UID for saving: " + userIdToUseForSaving);
            }
        }


        if (userIdToUseForSaving != null && currentWritingExercise != null && currentMicrosoftUser != null) {
            // Populate currentMicrosoftUser with the data to be saved
            currentMicrosoftUser.setWritingExerciseId(currentWritingExercise.getId()); // Actual ID of the exercise
            currentMicrosoftUser.setWritingLevelName(levelName);
            currentMicrosoftUser.setWritingTopicTitle(topicTitle);
            currentMicrosoftUser.setWritingUserAnswer(textBeingSubmitted);
            currentMicrosoftUser.setWritingTimestamp(System.currentTimeMillis());
            currentMicrosoftUser.setWritingFeedbackSummary("Feedback pending...");

            saveUserWritingAnswerToFirebase(userIdToUseForSaving, currentMicrosoftUser);
        } else {
            Log.w(TAG, "Cannot save user answer: User ID, Exercise context, or currentMicrosoftUser is missing.");
            if (currentWritingExercise == null) Log.w(TAG, "currentWritingExercise is null");
            if (userIdToUseForSaving == null) Log.w(TAG, "userIdToUseForSaving is null");
            if (currentMicrosoftUser == null) Log.w(TAG, "currentMicrosoftUser is null");
        }

        final String finalUserIdForFeedbackUpdate = userIdToUseForSaving;

        getFeedbackFromGemini(originalPrompt, textBeingSubmitted, new FeedbackCallback() {
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

                        if (currentMicrosoftUser != null) { // Update feedback summary in our model
                            currentMicrosoftUser.setWritingFeedbackSummary(allCriteriaSuccess ? "All criteria success" : "Needs improvement");
                        }


                        view.displayStructuredAIFeedback(
                                taskResponse.getString("feedback"), taskResponse.getInt("iconType"),
                                coherenceCohesion.getString("feedback"), coherenceCohesion.getInt("iconType"),
                                grammarVocabulary.getString("feedback"), grammarVocabulary.getInt("iconType"),
                                length.getString("feedback"), length.getInt("iconType")
                        );
                        currentButtonState = STATE_RETRY_WRITING;
                        isUserEditingAfterFeedback = false;
                        submittedTextForCurrentFeedback = textBeingSubmitted;
                        isFeedbackPanelVisible = true;
                        editButtonForcedByLoad = false;

                        updateSubmitButtonBasedOnState();
                        if (isFeedbackPanelVisible) view.focusOnFeedbackPanel();

                        if (finalUserIdForFeedbackUpdate != null && currentWritingExercise != null && currentMicrosoftUser != null) {
                            updateUserWritingAnswerFeedback(
                                    finalUserIdForFeedbackUpdate,
                                    currentWritingExercise.getId(),
                                    currentMicrosoftUser.getWritingFeedbackSummary() // Use summary from model
                            );
                        }

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing overall feedback JSON from Gemini", e);
                        if (view != null) view.showFailToast("Error processing feedback: " + e.getMessage());
                        isFeedbackPanelVisible = false;
                        currentButtonState = STATE_SUBMIT_WRITING;
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
                    isFeedbackPanelVisible = false;
                    updateSubmitButtonBasedOnState();
                });
            }
        });
    }

    private void saveUserWritingAnswerToFirebase(String userId, MicrosoftUser userWithWritingData) {
        if (userId == null || userWithWritingData == null || userWithWritingData.getWritingExerciseId() == null) {
            Log.w(TAG, "Cannot save user writing answer: userId, userWithWritingData, or writingExerciseId is null.");
            return;
        }
        String exerciseKeyForPath = userWithWritingData.getWritingExerciseId();

        DatabaseReference userAnswersRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers")
                .child(userId)
                .child("Progress")
                .child("WritingAnswers")
                .child(exerciseKeyForPath);

        // Ensure timestamp is current for this save operation
        userWithWritingData.setWritingTimestamp(System.currentTimeMillis());

        userAnswersRef.setValue(userWithWritingData.toMapForWritingAnswer())
                .addOnSuccessListener(aVoid -> Log.i(TAG, "User writing answer saved successfully for user: " + userId + ", exercise: " + exerciseKeyForPath))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save user writing answer for user: " + userId + ", exercise: " + exerciseKeyForPath, e));
    }

    private void updateUserWritingAnswerFeedback(String userId, String exerciseId, String feedbackSummary) {
        if (userId == null || exerciseId == null) {
            Log.w(TAG, "Cannot update feedback summary: userId or exerciseId is null.");
            return;
        }

        DatabaseReference userAnswerRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers")
                .child(userId)
                .child("Progress")
                .child("WritingAnswers")
                .child(exerciseId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("feedbackSummary", feedbackSummary);
        updates.put("timestamp", System.currentTimeMillis()); // Update timestamp on feedback update

        userAnswerRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.i(TAG, "Feedback summary updated successfully for user: " + userId + ", exercise: " + exerciseId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update feedback summary for user: " + userId + ", exercise: " + exerciseId, e));
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
        if (GEMINI_API_KEY.equals("AIzaSyAKitUIzcsW3Gd5SyeTLTrcGnJcPTDG09c") || GEMINI_API_KEY.isEmpty() || GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY") ) {
            Log.e(TAG, "Gemini API Key is a placeholder, empty, or a sample key. Please set a valid API key.");
            String simulatedError = "AI Feedback service is temporarily unavailable (API Key issue).";
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
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);

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

                        if (fullJsonResponse.has("candidates") && fullJsonResponse.getJSONArray("candidates").length() > 0) {
                            JSONObject firstCandidate = fullJsonResponse.getJSONArray("candidates").getJSONObject(0);
                            if (firstCandidate.has("content") && firstCandidate.getJSONObject("content").has("parts") &&
                                    firstCandidate.getJSONObject("content").getJSONArray("parts").length() > 0) {
                                String resultText = firstCandidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");

                                if (resultText.startsWith("```json")) {
                                    resultText = resultText.substring(7);
                                    if (resultText.endsWith("```")) {
                                        resultText = resultText.substring(0, resultText.length() - 3);
                                    }
                                } else if (resultText.startsWith("```")) {
                                    resultText = resultText.substring(3);
                                    if (resultText.endsWith("```")) {
                                        resultText = resultText.substring(0, resultText.length() - 3);
                                    }
                                }
                                successListener.onResult(resultText.trim());
                            } else {
                                throw new JSONException("Parts array is missing or empty in Gemini response candidate.");
                            }
                        } else {
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
            } catch (Exception e) {
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
    }

    public void onResume() {
        Log.d(TAG, "onResume. Re-evaluating UI state if necessary.");
        if (exerciseDataLoaded && titlesLoaded && currentMicrosoftUser != null && view != null) { // Added currentMicrosoftUser check
            updateSubmitButtonBasedOnState();
            if (currentButtonState == STATE_SUBMIT_WRITING) {
                String currentText = view.getCurrentAnswerText();
                onAnswerTextChanged(currentText != null ? currentText : "");
            }
        } else {
            Log.d(TAG, "onResume: Core data not yet fully loaded or view/user context missing, UI update will be handled by loading callbacks.");
        }
    }


    public void onDestroy() {
        Log.d(TAG, "onDestroy called.");
        if (inlineAnalysisRunnable != null) {
            inlineAnalysisHandler.removeCallbacks(inlineAnalysisRunnable);
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        this.view = null;
    }

    public void onReviewFeedbackClicked() {
        if (view == null) return;
        Log.d(TAG, "Review Feedback clicked. Current isFeedbackPanelVisible (flag): " + isFeedbackPanelVisible);
        isFeedbackPanelVisible = !isFeedbackPanelVisible;
        view.setFeedbackPanelVisibility(isFeedbackPanelVisible);
        if (isFeedbackPanelVisible) {
            view.focusOnFeedbackPanel();
        }
    }

    public boolean handleBackPressed() {
        return false;
    }
    public int getCurrentButtonState() { return currentButtonState; }
    public boolean areAllCriteriaSuccess() { return this.allCriteriaSuccess; }
    public boolean isUserEditingAfterFeedback() { return this.isUserEditingAfterFeedback; }
    public String getSubmittedTextForCurrentFeedback() { return this.submittedTextForCurrentFeedback; }
    public boolean isEditButtonForcedByLoad() { return this.editButtonForcedByLoad; }
}
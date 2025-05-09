//package com.example.langhexx.Controller;
//
//import android.content.Intent;
//import android.os.Handler;
//import android.os.Looper;
//import android.util.Log;
//import android.text.TextUtils;
//
//import androidx.annotation.NonNull;
//
//
//import com.example.langhexx.Model.MicrosoftUser;
//import com.example.langhexx.Model.UserWritingAnswer;
//import com.example.langhexx.Model.WritingExercise;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseUser;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//
//import org.json.JSONArray;
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class WritingController {
//
//    private static final String TAG = "WritingController";
//    // API Key bạn cung cấp
//    private static final String GEMINI_API_KEY = "AIzaSyCf-9jplfin2aWdFAdxWcCdzox5wzIkBbQ";
//    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;
//    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
//    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
//
//    // Handler cho inline analysis debouncing
//    private final Handler inlineAnalysisHandler = new Handler(Looper.getMainLooper());
//    private Runnable inlineAnalysisRunnable;
//    private static final long INLINE_ANALYSIS_DEBOUNCE_MS = 1500; // 1.5 giây
//
//    //
//    private MicrosoftUser currentMicrosoftUser; // Giả sử bạn có cách lấy thông tin này khi đăng nhập
//    private FirebaseAuth mAuth;
//
//    // New flag to track if a saved answer was successfully loaded
//    private boolean hasLoadedSavedAnswer = false;
//
//    // ADDED: Flag to control feedback panel visibility
//    private boolean isFeedbackPanelVisible = false;
//
//    public interface ViewInterface {
//        // ... các phương thức hiện có ...
//        void displayExerciseTitle(String title);
//        void displayQuestionPrompt(String prompt);
//        void showToast(String message);
//        void showFailToast(String message);
//        void showConfirmationDialog(String title, String message, Runnable onConfirm);
//        void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle);
//        void finishActivity();
//        void setSubmitButtonState(String text, boolean enabled);
//        void setUIElementsVisibility(boolean visible);
//        void showLoading(String message); // Để dùng cho inline analysis loading nếu cần
//        void hideLoading();
//        void clearAnswerInput();
//        void displayStructuredAIFeedback(
//                String taskResponseFeedback, int taskResponseIconType,
//                String coherenceCohesionFeedback, int coherenceCohesionIconType,
//                String grammarVocabularyFeedback, int grammarVocabularyIconType,
//                String lengthFeedback, int lengthIconType
//        );
//        void setFeedbackPanelVisibility(boolean visible);
//        void setFeedbackTriggerVisibility(boolean visible);
//        void setAnswerInputVisibility(boolean visible);
//        void setAnswerEditTextEnabled(boolean enabled);
//        void requestFocusOnAnswerInput();
//        void focusOnFeedbackPanel();
//        void setSeeRevisedVersionButtonVisibility(boolean visible);
//
//        void clearInlineErrorHighlighting();
//
//        // Phương thức mới để hiển thị câu trả lời đã lưu
//        void displaySavedAnswer(String answer);
//    }
//
//    // ... các hằng số và biến thành viên hiện có ...
//    public static final int STATE_SUBMIT_WRITING = 0;
//    public static final int STATE_RETRY_WRITING = 1;
//    private int currentButtonState = STATE_SUBMIT_WRITING;
//
//    private ViewInterface view;
//    private String levelName;
//    private String topicTitle;
//    private String exerciseTitle;
//    private WritingExercise currentWritingExercise;
//    private ArrayList<String> allExerciseTitles;
//
//    private boolean exerciseDataLoaded = false;
//    private boolean titlesLoaded = false;
//    private boolean isUserEditingAfterFeedback = false;
//    private boolean allCriteriaSuccess = false;
//    private String submittedTextForCurrentFeedback = "";
//
//    private DatabaseReference databaseReference;
//
//
//    public WritingController(ViewInterface view, Intent intent) {
//        this.view = view;
//        this.allExerciseTitles = new ArrayList<>();
//        this.databaseReference = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
//        this.mAuth = FirebaseAuth.getInstance(); // Khởi tạo FirebaseAuth
//
//        if (intent != null) {
//            levelName = intent.getStringExtra("LEVEL_NAME");
//            topicTitle = intent.getStringExtra("TOPIC_TITLE");
//            exerciseTitle = intent.getStringExtra("EXERCISE_TITLE");
//        } else {
//            handleInitializationError("Error: Intent is null.");
//            return;
//        }
//        if (levelName == null || topicTitle == null || exerciseTitle == null) {
//            handleInitializationError("Error: Missing exercise identifiers in Intent.");
//        }
//        FirebaseUser firebaseUser = mAuth.getCurrentUser();
//        if (firebaseUser != null) {
//            // Giả sử bạn đã lưu thông tin người dùng Microsoft trong Realtime Database
//            // với key là UID của FirebaseUser
//            loadMicrosoftUserData(firebaseUser.getUid());
//        }
//    }
//
//    private void loadMicrosoftUserData(String firebaseUid) {
//        DatabaseReference userRef = databaseReference.child("Users").child(firebaseUid);
//        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (snapshot.exists()) {
//                    // Giả sử cấu trúc User trong Firebase của bạn có các trường tương ứng
//                    // hoặc bạn có một node con "microsoftInfo"
//                    String msUserId = snapshot.child("microsoftGraphId").getValue(String.class); // Lấy từ cấu trúc ảnh bạn gửi
//                    String email = snapshot.child("email").getValue(String.class);
//                    String displayName = snapshot.child("name").getValue(String.class); // Hoặc "displayName"
//
//                    if (msUserId != null) { // Kiểm tra xem có phải là user Microsoft không dựa trên sự tồn tại của microsoftGraphId
//                        currentMicrosoftUser = new MicrosoftUser(msUserId, email, displayName);
//                        Log.d(TAG, "Microsoft user data loaded: " + displayName);
//                    } else {
//                        Log.d(TAG, "User " + firebaseUid + " is not a Microsoft Graph linked user or data is missing.");
//                    }
//                } else {
//                    Log.w(TAG, "Microsoft user data not found for UID: " + firebaseUid);
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e(TAG, "Failed to load Microsoft user data.", error.toException());
//            }
//        });
//    }
//
//
//    public void initialize() {
//        Log.d(TAG, "Initializing Controller for: L-" + levelName + ", T-" + topicTitle + ", E-" + exerciseTitle);
//        if (view == null) { Log.e(TAG, "View is null in initialize"); return; }
//
//        // Set initial defaults. These might be overridden by loadSavedUserAnswerFromFirebase if a saved answer is found.
//        currentButtonState = STATE_SUBMIT_WRITING;
//        isUserEditingAfterFeedback = false;
//        allCriteriaSuccess = false;
//        submittedTextForCurrentFeedback = "";
//        hasLoadedSavedAnswer = false; // Reset this flag
//        isFeedbackPanelVisible = false; // ADDED: Initialize the new flag
//
//        view.setUIElementsVisibility(false);
//        view.setFeedbackPanelVisibility(false);
//        view.setFeedbackTriggerVisibility(false);
//        view.setSeeRevisedVersionButtonVisibility(false);
//        view.setAnswerEditTextEnabled(true); // Default to enabled for new input
//
//        loadExerciseDataFromFirebase();
//        loadAllExerciseTitlesFromFirebase();
//        // loadSavedUserAnswerFromFirebase will now call checkIfAllDataLoadedAndReady
//        loadSavedUserAnswerFromFirebase(); // MODIFIED - This will be the last loading step
//    }
//
//    private void handleInitializationError(String errorMessage) {
//        Log.e(TAG, errorMessage);
//        if (view != null) { view.showFailToast(errorMessage); view.finishActivity(); }
//    }
//
//    private void loadExerciseDataFromFirebase() {
//        if (levelName == null || topicTitle == null || exerciseTitle == null) {
//            if(view != null) { Log.e(TAG, "Cannot load exercise data: Level/Topic/ExerciseTitle is null."); view.showFailToast("Error: Missing data to load exercise."); view.finishActivity(); }
//            exerciseDataLoaded = true; // Mark as loaded to not block checkIfAllDataLoadedAndReady entirely
//            return;
//        }
//        DatabaseReference exerciseRef = databaseReference.child("Lessons").child("Levels").child(levelName).child("Writing").child("Topics").child(topicTitle).child("Exercises").child(exerciseTitle);
//        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (!snapshot.exists()) {
//                    Log.e(TAG, "Writing exercise data not found at path: " + exerciseRef);
//                    if (view != null) {
//                        view.displayQuestionPrompt("Content not available for this exercise.");
//                        view.setSubmitButtonState("N/A", false);
//                        view.setAnswerEditTextEnabled(false);
//                    }
//                    currentWritingExercise = null; // Indicate no valid exercise
//                    exerciseDataLoaded = true;
//                    // Removed checkIfAllDataLoadedAndReady here; loadSavedUserAnswerFromFirebase will trigger it.
//                    return;
//                }
//                String scriptPrompt = snapshot.child("script").getValue(String.class);
//                if (scriptPrompt != null && !scriptPrompt.isEmpty()) {
//                    currentWritingExercise = new WritingExercise(snapshot.getKey(), exerciseTitle, scriptPrompt);
//                    if (view != null) { view.displayExerciseTitle(currentWritingExercise.getTitle()); view.displayQuestionPrompt(currentWritingExercise.getScript()); }
//                } else {
//                    currentWritingExercise = new WritingExercise(snapshot.getKey(), exerciseTitle, "Writing prompt not found.");
//                    if (view != null) {
//                        view.displayExerciseTitle(exerciseTitle);
//                        view.displayQuestionPrompt("Writing prompt not available for this exercise.");
//                        view.setAnswerEditTextEnabled(false);
//                    }
//                }
//                exerciseDataLoaded = true;
//                // Removed checkIfAllDataLoadedAndReady here; loadSavedUserAnswerFromFirebase will trigger it.
//            }
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e(TAG, "Firebase loading cancelled/failed: " + error.getMessage(), error.toException());
//                if (view != null) {
//                    view.showFailToast("Error loading exercise: " + error.getMessage());
//                    view.setAnswerEditTextEnabled(false);
//                }
//                exerciseDataLoaded = true;
//                currentWritingExercise = null; // Indicate no valid exercise
//                // Removed checkIfAllDataLoadedAndReady here; loadSavedUserAnswerFromFirebase will trigger it.
//            }
//        });
//    }
//
//    private void loadAllExerciseTitlesFromFirebase() {
//        if (levelName == null || topicTitle == null) { titlesLoaded = true; return; } // Removed checkIfAllDataLoadedAndReady here.
//        DatabaseReference exercisesRef = databaseReference.child("Lessons").child("Levels").child(levelName).child("Writing").child("Topics").child(topicTitle).child("Exercises");
//        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
//                allExerciseTitles.clear();
//                if (snapshot.exists()) { for (DataSnapshot exSnap : snapshot.getChildren()) { allExerciseTitles.add(exSnap.getKey()); } }
//                titlesLoaded = true;
//                // Removed checkIfAllDataLoadedAndReady here.
//            }
//            @Override public void onCancelled(@NonNull DatabaseError error) {
//                titlesLoaded = true;
//                // Removed checkIfAllDataLoadedAndReady here.
//            }
//        });
//    }
//
//    private void loadSavedUserAnswerFromFirebase() {
//        FirebaseUser firebaseUser = mAuth.getCurrentUser();
//        if (firebaseUser != null && levelName != null && topicTitle != null && exerciseTitle != null) {
//            String userId = null;
//            if (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null) {
//                userId = currentMicrosoftUser.getUserId();
//            } else {
//                userId = firebaseUser.getUid();
//            }
//
//            if (userId != null) {
//                DatabaseReference answerRef = databaseReference
//                        .child("Users")
//                        .child("MicrosoftUsers") // Assuming all users are under this node as per the image
//                        .child(userId)
//                        .child("Progress")
//                        .child("WritingAnswers")
//                        .child(exerciseTitle); // Target the specific exercise node
//
//                answerRef.addListenerForSingleValueEvent(new ValueEventListener() {
//                    @Override
//                    public void onDataChange(@NonNull DataSnapshot snapshot) {
//                        String savedAnswer = snapshot.child("userAnswer").getValue(String.class); // Get the actual answer text
//                        String feedbackSummary = snapshot.child("feedbackSummary").getValue(String.class); // Get feedback summary
//
//                        if (savedAnswer != null && !savedAnswer.trim().isEmpty()) {
//                            if (view != null) {
//                                view.displaySavedAnswer(savedAnswer);
//                            }
//                            hasLoadedSavedAnswer = true; // Set flag
//                            // If a saved answer exists, set the initial state to "retry/edit" mode
//                            currentButtonState = STATE_RETRY_WRITING;
//                            isUserEditingAfterFeedback = false; // User is not actively editing yet
//                            submittedTextForCurrentFeedback = savedAnswer; // Store the loaded answer for comparison
//
//                            if (feedbackSummary != null) {
//                                allCriteriaSuccess = feedbackSummary.equalsIgnoreCase("All criteria success");
//                            } else {
//                                allCriteriaSuccess = false; // Assume not all success if no summary
//                            }
//                            Log.d(TAG, "Saved answer loaded. Initial state: STATE_RETRY_WRITING, AllSuccess: " + allCriteriaSuccess);
//                        } else {
//                            hasLoadedSavedAnswer = false; // No saved answer found or it's empty
//                            // Keep the default state for new writing (STATE_SUBMIT_WRITING)
//                            //Log.d(TAG, "No saved answer found for user " + userId + ", exercise " + exerciseTitle);
//                        }
//                        // IMPORTANT: Call checkIfAllDataLoadedAndReady here to ensure UI updates
//                        checkIfAllDataLoadedAndReady();
//                    }
//
//                    @Override
//                    public void onCancelled(@NonNull DatabaseError error) {
//                        Log.e(TAG, "Failed to load saved user answer.", error.toException());
//                        hasLoadedSavedAnswer = false; // Failed to load, treat as no saved answer
//                        checkIfAllDataLoadedAndReady(); // Still proceed to update UI
//                    }
//                });
//            } else {
//                // If userId is null, cannot load saved answer, proceed with default state
//                Log.w(TAG, "Cannot load saved user answer: userId is null.");
//                hasLoadedSavedAnswer = false;
//                checkIfAllDataLoadedAndReady(); // Still proceed to update UI
//            }
//        } else {
//            // If any pre-conditions are not met, proceed with default state
//            Log.w(TAG, "Cannot load saved user answer: FirebaseUser or exercise details are null.");
//            hasLoadedSavedAnswer = false;
//            checkIfAllDataLoadedAndReady(); // Still proceed to update UI
//        }
//    }
//
//
//    private void checkIfAllDataLoadedAndReady() {
//        // Ensure all necessary data (exercise, titles) are loaded.
//        // saved answer status is now determined by the 'hasLoadedSavedAnswer' flag set in loadSavedUserAnswerFromFirebase
//        if (exerciseDataLoaded && titlesLoaded) {
//            Log.d(TAG, "All initial data loaded (exercise, titles, saved answer status).");
//
//            view.setUIElementsVisibility(true); // Ensure main UI is visible
//            view.setAnswerInputVisibility(true); // Ensure EditText is visible
//
//            // Determine initial UI state based on whether a saved answer was loaded
//            if (hasLoadedSavedAnswer && currentWritingExercise != null && currentWritingExercise.getScript() != null && !currentWritingExercise.getScript().contains("not available")) {
//                // If a saved answer was loaded AND the exercise content is valid, set UI to "Edit" mode
//                Log.d(TAG, "Saved answer detected. Setting UI to 'Edit' mode.");
//                currentButtonState = STATE_RETRY_WRITING; // Set to STATE_RETRY_WRITING which will display "Edit" button text
//                view.setAnswerEditTextEnabled(false);     // Disable EditText initially
//                view.setFeedbackTriggerVisibility(false);  // <--- Already sets it to false
//                view.setFeedbackPanelVisibility(false);   // <--- Already sets it to false
//                isFeedbackPanelVisible = false; // ADDED: Ensure feedback panel is hidden initially when a saved answer is loaded
//                view.setSeeRevisedVersionButtonVisibility(!allCriteriaSuccess); // Show if not all success
//
//            } else {
//                // No saved answer, or exercise content is not available, fresh start
//                Log.d(TAG, "No saved answer or content unavailable. Setting UI to 'Submit' mode.");
//                currentButtonState = STATE_SUBMIT_WRITING;
//                view.setAnswerEditTextEnabled(true); // Enabled for new input
//                view.setFeedbackTriggerVisibility(false);
//                view.setFeedbackPanelVisibility(false);
//                isFeedbackPanelVisible = false; // ADDED: Ensure feedback panel is hidden for new writing
//                view.setSeeRevisedVersionButtonVisibility(false);
//            }
//
//            updateSubmitButtonBasedOnState(); // Call to ensure button text/state is correct
//            onAnswerTextChanged(view != null ? "" : ""); // Trigger initial check for submit button enabled state
//        }
//    }
//
//    // REPLACED: Entire updateSubmitButtonBasedOnState method
//    private void updateSubmitButtonBasedOnState() {
//        if (view == null) return;
//        Log.d(TAG, "updateSubmitButtonBasedOnState - CurrentState: " + currentButtonState + ", isEditingAfterFeedback: " + isUserEditingAfterFeedback + ", allSuccess: " + allCriteriaSuccess + ", isFeedbackPanelVisible: " + isFeedbackPanelVisible + ", hasLoadedSavedAnswer: " + hasLoadedSavedAnswer);
//
//        boolean contentNotAvailable = currentWritingExercise == null || currentWritingExercise.getScript() == null ||
//                currentWritingExercise.getScript().equals("Writing prompt not available for this exercise.") ||
//                currentWritingExercise.getScript().equals("Writing prompt not found.") ||
//                currentWritingExercise.getScript().equals("Content not available for this exercise.");
//
//        if (contentNotAvailable) {
//            view.setSubmitButtonState("N/A", false);
//            view.setFeedbackPanelVisibility(false);
//            view.setFeedbackTriggerVisibility(false);
//            view.setSeeRevisedVersionButtonVisibility(false);
//            view.setAnswerEditTextEnabled(false);
//            view.displayQuestionPrompt("Content not available for this exercise.");
//            isFeedbackPanelVisible = false; // Ensure flag is false
//            return;
//        }
//
//        // Determine visibility of feedback related elements based on the state.
//        // User's request: "khi vào trang writing nếu data đã có và load lên thì tôi muốn ẩn "Review" và dialog feedback luôn ! chỉ hiển thị cái này khi submit !"
//        // This means:
//        // 1. If `hasLoadedSavedAnswer` is true (initial load of a saved answer), hide "Review" and feedback panel.
//        // 2. If it's `STATE_SUBMIT_WRITING` (fresh writing or editing), hide "Review" and feedback panel.
//        // 3. If it's `STATE_RETRY_WRITING` AND it's NOT an initial load (i.e., user just submitted), show "Review" and feedback panel.
//
//        if (currentButtonState == STATE_SUBMIT_WRITING) { // User is in writing mode (fresh or editing)
//            view.setAnswerEditTextEnabled(true);
//            view.setFeedbackPanelVisibility(false); // Panel should be hidden while writing/editing
//            view.setFeedbackTriggerVisibility(false); // Trigger should be hidden while writing/editing
//            view.setSeeRevisedVersionButtonVisibility(false); // No revised version button while writing new/editing
//        } else if (currentButtonState == STATE_RETRY_WRITING) { // User has submitted, viewing old answer or feedback
//            view.setAnswerEditTextEnabled(false); // Answer input is disabled
//
//            // Apply user's specific request: hide on initial load of saved answer
//            if (hasLoadedSavedAnswer) { // This means it's the first time entering this state with a saved answer
//                view.setFeedbackTriggerVisibility(false);
//                view.setFeedbackPanelVisibility(false);
//                // The flag hasLoadedSavedAnswer will be consumed at the end of this method.
//            } else { // This means it's STATE_RETRY_WRITING due to a recent submission
//                view.setFeedbackTriggerVisibility(true); // "Review" link is visible
//                view.setFeedbackPanelVisibility(isFeedbackPanelVisible); // Panel visibility controlled by the flag
//            }
//            view.setSeeRevisedVersionButtonVisibility(!allCriteriaSuccess); // Show revised version if not all success
//        }
//
//        // Update button text based on state
//        switch (currentButtonState) {
//            case STATE_SUBMIT_WRITING:
//                view.setSubmitButtonState("Submit", true); // Will be disabled by onAnswerTextChanged if text is empty
//                break;
//            case STATE_RETRY_WRITING:
//                if (allCriteriaSuccess) {
//                    view.setSubmitButtonState("Done", true);
//                } else {
//                    view.setSubmitButtonState("Edit", true);
//                }
//                break;
//            default:
//                view.setSubmitButtonState("Submit", true);
//                // Default also set visibility above, so no need to repeat here for panel/trigger
//                break;
//        }
//
//        // After the initial load and setting up the state, reset hasLoadedSavedAnswer
//        // so that subsequent calls to updateSubmitButtonBasedOnState (e.g., after an edit)
//        // don't keep hiding feedback elements based on this initial load flag.
//        if (hasLoadedSavedAnswer) {
//            hasLoadedSavedAnswer = false; // Consume the flag
//            Log.d(TAG, "hasLoadedSavedAnswer flag consumed.");
//        }
//    }
//
//    public void onAnswerTextChanged(String currentText) {
//        if (view == null) return;
//
//        // Xử lý cho nút Submit
//        if (currentButtonState == STATE_SUBMIT_WRITING) {
//            if (isUserEditingAfterFeedback) {
//                boolean hasChanged = !currentText.trim().equals(submittedTextForCurrentFeedback.trim());
//                view.setSubmitButtonState("Submit", hasChanged);
//            } else {
//                view.setSubmitButtonState("Submit", !TextUtils.isEmpty(currentText.trim()));
//            }
//        }
//
//    }
//    // Phương thức trợ giúp để kiểm tra xem view có cho phép edit không (để tránh gọi khi view bị disable)
//    private boolean viewIsEditTextEnabled() {
//        // Cần một cách để view báo cho controller biết trạng thái của EditText
//        // Tạm thời giả định là true nếu controller nghĩ là nó nên enabled
//        return currentButtonState == STATE_SUBMIT_WRITING;
//    }
//
//
//    public void onSubmitButtonClicked(String userAnswer) {
//        if (view == null) return;
//        Log.d(TAG, "Submit button clicked. State: " + currentButtonState + ", AllSuccess: " + allCriteriaSuccess + ", EditingAfterFeedback: " + isUserEditingAfterFeedback);
//
//        // Hủy bỏ phân tích inline đang chờ (nếu có) khi submit
//        if (inlineAnalysisRunnable != null) {
//            inlineAnalysisHandler.removeCallbacks(inlineAnalysisRunnable);
//        }
//        view.clearInlineErrorHighlighting(); // Xóa gạch chân lỗi khi submit
//
//        switch (currentButtonState) {
//            case STATE_SUBMIT_WRITING:
//                allCriteriaSuccess = false;
//                if (userAnswer.trim().isEmpty()) {
//                    view.showFailToast("Please write something before submitting."); return;
//                }
//                if (currentWritingExercise == null || currentWritingExercise.getScript() == null || currentWritingExercise.getScript().isEmpty() || currentWritingExercise.getScript().contains("not available")) {
//                    view.showFailToast("Cannot submit without a valid writing prompt."); return;
//                }
//                view.showConfirmationDialog("Confirm Submission", "Are you sure you want to submit your writing for feedback?", () -> {
//                    proceedWithWritingSubmission(userAnswer, currentWritingExercise.getScript());
//                });
//                break;
//            case STATE_RETRY_WRITING:
//                if (allCriteriaSuccess) {
//                    Log.i(TAG, "'Done' button clicked. Finishing activity.");
//                    isUserEditingAfterFeedback = false;
//                    isFeedbackPanelVisible = false; // ADDED: Reset for next exercise or activity finish
//                    view.finishActivity();
//                } else {
//                    Log.i(TAG, "'Edit' button clicked. Enabling edit mode.");
//                    currentButtonState = STATE_SUBMIT_WRITING;
//                    isUserEditingAfterFeedback = true;
//                    allCriteriaSuccess = false;
//                    isFeedbackPanelVisible = false; // ADDED: Hide feedback panel when entering edit mode
//                    updateSubmitButtonBasedOnState();
//                    view.setSubmitButtonState("Submit", false); // Will be updated by onAnswerTextChanged
//                    view.requestFocusOnAnswerInput();
//                }
//                break;
//            default:
//                Log.w(TAG, "Unknown button state clicked: " + currentButtonState);
//                currentButtonState = STATE_SUBMIT_WRITING;
//                isUserEditingAfterFeedback = false;
//                allCriteriaSuccess = false;
//                submittedTextForCurrentFeedback = "";
//                isFeedbackPanelVisible = false; // ADDED: Ensure feedback panel is hidden
//                updateSubmitButtonBasedOnState();
//                break;
//        }
//    }
//    private void proceedWithWritingSubmission(String userAnswer, String originalPrompt) {
//        if (view == null) return;
//        Log.i(TAG, "Proceeding with writing submission for text: " + userAnswer);
//        view.showLoading("Getting feedback...");
//        final String textBeingSubmitted = userAnswer;
//
//        // Lưu câu trả lời vào Firebase NẾU là người dùng Microsoft
//        if (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null && currentWritingExercise != null) {
//            saveUserWritingAnswerToFirebase(
//                    currentMicrosoftUser.getUserId(), // Sử dụng ID người dùng Microsoft (hoặc Firebase UID nếu bạn muốn key là Firebase UID)
//                    currentWritingExercise.getId(), // Hoặc exerciseTitle nếu đó là ID duy nhất
//                    levelName,
//                    topicTitle,
//                    textBeingSubmitted,
//                    "Feedback pending..." // Trạng thái ban đầu
//            );
//        } else if (mAuth.getCurrentUser() != null && currentWritingExercise != null) {
//            // Nếu không phải là Microsoft user nhưng vẫn đăng nhập Firebase (ví dụ: email/password)
//            // và bạn vẫn muốn lưu, bạn có thể sử dụng mAuth.getCurrentUser().getUid()
//            Log.d(TAG, "Current user is Firebase user but not identified as Microsoft Graph linked user, or Microsoft user data not loaded yet.");
//            // Tùy chọn: lưu với Firebase UID nếu currentMicrosoftUser là null nhưng FirebaseUser tồn tại
//            saveUserWritingAnswerToFirebase(
//                    mAuth.getCurrentUser().getUid(), // Sử dụng Firebase UID làm key
//                    currentWritingExercise.getId(),
//                    levelName,
//                    topicTitle,
//                    textBeingSubmitted,
//                    "Feedback pending..."
//            );
//        }
//
//
//        getFeedbackFromGemini(originalPrompt, userAnswer, new FeedbackCallback() {
//            @Override
//            public void onSuccess(JSONObject feedbackJson) {
//                mainThreadHandler.post(() -> {
//                    if (view == null) return;
//                    view.hideLoading();
//                    try {
//                        // ... (phần xử lý feedback JSON như cũ) ...
//                        JSONObject taskResponse = feedbackJson.getJSONObject("taskResponse");
//                        JSONObject coherenceCohesion = feedbackJson.getJSONObject("coherenceCohesion");
//                        JSONObject grammarVocabulary = feedbackJson.getJSONObject("grammarVocabulary");
//                        JSONObject length = feedbackJson.getJSONObject("length");
//
//                        allCriteriaSuccess = taskResponse.getInt("iconType") == 1 &&
//                                coherenceCohesion.getInt("iconType") == 1 &&
//                                grammarVocabulary.getInt("iconType") == 1 &&
//                                length.getInt("iconType") == 1;
//                        Log.d(TAG, "All criteria success after Gemini: " + allCriteriaSuccess);
//
//                        view.displayStructuredAIFeedback(
//                                taskResponse.getString("feedback"), taskResponse.getInt("iconType"),
//                                coherenceCohesion.getString("feedback"), coherenceCohesion.getInt("iconType"),
//                                grammarVocabulary.getString("feedback"), grammarVocabulary.getInt("iconType"),
//                                length.getString("feedback"), length.getInt("iconType")
//                        );
//
//                        view.showToast("Feedback received!");
//                        currentButtonState = STATE_RETRY_WRITING;
//                        isUserEditingAfterFeedback = false;
//                        submittedTextForCurrentFeedback = textBeingSubmitted; // Giữ nguyên
//
//                        // ADDED: After submission, feedback panel should be visible.
//                        isFeedbackPanelVisible = true;
//                        view.setFeedbackPanelVisibility(true);
//                        view.setFeedbackTriggerVisibility(true);
//
//                        updateSubmitButtonBasedOnState();
//
//                        // Cập nhật feedback summary trong Firebase sau khi có kết quả
//                        String feedbackSummary = allCriteriaSuccess ? "All criteria success" : "Needs improvement";
//                        if (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null && currentWritingExercise != null) {
//                            updateUserWritingAnswerFeedback(
//                                    currentMicrosoftUser.getUserId(), // Hoặc Firebase UID
//                                    currentWritingExercise.getId(), // Hoặc exerciseTitle
//                                    feedbackSummary
//                            );
//                        } else if (mAuth.getCurrentUser() != null && currentWritingExercise != null) {
//                            updateUserWritingAnswerFeedback(
//                                    mAuth.getCurrentUser().getUid(),
//                                    currentWritingExercise.getId(),
//                                    feedbackSummary
//                            );
//                        }
//                    } catch (JSONException e) {
//                        Log.e(TAG, "Error parsing overall feedback JSON from Gemini", e);
//                        if (view != null) view.showFailToast("Error processing feedback: " + e.getMessage());
//                        isFeedbackPanelVisible = false; // ADDED: On parse error, ensure feedback is not shown.
//                        view.setFeedbackPanelVisibility(false); // Explicitly hide
//                        view.setFeedbackTriggerVisibility(false); // Explicitly hide
//                        updateSubmitButtonBasedOnState(); // Revert UI state if necessary
//                    }
//                });
//            }
//            @Override
//            public void onError(String error) {
//                mainThreadHandler.post(() -> {
//                    if (view == null) return;
//                    view.hideLoading();
//                    Log.e(TAG, "Error getting feedback from Gemini: " + error);
//                    view.showFailToast("Failed to get feedback: " + error);
//                    isFeedbackPanelVisible = false; // ADDED: On API error, ensure feedback is not shown.
//                    view.setFeedbackPanelVisibility(false); // Explicitly hide
//                    view.setFeedbackTriggerVisibility(false); // Explicitly hide
//                    updateSubmitButtonBasedOnState(); // Revert UI state if necessary
//                });
//            }
//        });
//    }
//    private void saveUserWritingAnswerToFirebase(String userId, String exerciseId, String level, String topic, String answer, String initialFeedbackSummary) {
//        if (userId == null || exerciseId == null) {
//            Log.w(TAG, "Cannot save user writing answer: userId or exerciseId is null.");
//            return;
//        }
//        DatabaseReference userAnswersRef = databaseReference
//                .child("Users")
//                .child("MicrosoftUsers") // <--- ADDED THIS NODE
//                .child(userId)
//                .child("Progress")
//                .child("WritingAnswers")
//                .child(exerciseId);
//        UserWritingAnswer userAnswer = new UserWritingAnswer(
//                exerciseId,
//                level,
//                topic,
//                answer,
//                System.currentTimeMillis(), // Hoặc dùng ServerValue.TIMESTAMP cho Firebase
//                initialFeedbackSummary
//        );
//
//        // Sử dụng toMap() để dễ dàng ghi object vào Firebase
//        Map<String, Object> answerValues = userAnswer.toMap();
//        userAnswersRef.setValue(answerValues)
//                .addOnSuccessListener(aVoid -> Log.i(TAG, "User writing answer saved successfully for user: " + userId + ", exercise: " + exerciseId))
//                .addOnFailureListener(e -> Log.e(TAG, "Failed to save user writing answer for user: " + userId + ", exercise: " + exerciseId, e));
//    }
//    private void updateUserWritingAnswerFeedback(String userId, String exerciseId, String feedbackSummary) {
//        if (userId == null || exerciseId == null) {
//            Log.w(TAG, "Cannot update feedback summary: userId or exerciseId is null.");
//            return;
//        }
//        DatabaseReference userAnswerRef = databaseReference
//                .child("Users")
//                .child("MicrosoftUsers")
//                .child(userId)
//                .child("Progress")
//                .child("WritingAnswers")
//                .child(exerciseId);
//
//        Map<String, Object> updates = new HashMap<>();
//        updates.put("feedbackSummary", feedbackSummary);
//        // Bạn cũng có thể cập nhật timestamp ở đây nếu muốn
//        // updates.put("lastUpdatedTimestamp", ServerValue.TIMESTAMP);
//
//        userAnswerRef.updateChildren(updates)
//                .addOnSuccessListener(aVoid -> Log.i(TAG, "Feedback summary updated successfully for user: " + userId + ", exercise: " + exerciseId))
//                .addOnFailureListener(e -> Log.e(TAG, "Failed to update feedback summary for user: " + userId + ", exercise: " + exerciseId, e));
//    }
//
//
//
//
//    interface FeedbackCallback {
//        void onSuccess(JSONObject feedbackJson); // Cho feedback tổng thể
//        void onError(String error);
//    }
//
//    private void getFeedbackFromGemini(String originalPrompt, String userAnswer, FeedbackCallback callback) {
//        // Prompt cho feedback tổng thể (như cũ)
//        String promptForGemini = "You are an English language learning assistant. " +
//                "Evaluate the following written response to the prompt. " +
//                "Provide constructive feedback for a language learner. " +
//                "Your response MUST be a JSON object with the following exact structure: " +
//                "{\"taskResponse\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>}, " +
//                "\"coherenceCohesion\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>}, " +
//                "\"grammarVocabulary\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>}, " +
//                "\"length\": {\"feedback\": \"<string>\", \"iconType\": <0_for_warning_1_for_success>}} " +
//                "Be concise in your feedback strings. iconType should be 1 if the user did well in that aspect (e.g., good length, clear task response), 0 otherwise (e.g., some errors, could be clearer).\n\n" +
//                "Original Prompt: \"" + originalPrompt + "\"\n\n" +
//                "User's Response: \"" + userAnswer + "\"\n\n" +
//                "Provide only the JSON object as your response.";
//
//        callGeminiAPI(promptForGemini, responseString -> {
//            try {
//                // Parser cho feedback tổng thể (JSON object)
//                callback.onSuccess(new JSONObject(responseString));
//            } catch (JSONException e) {
//                Log.e(TAG, "Error parsing overall feedback JSON from Gemini", e);
//                callback.onError("Invalid JSON format from AI for overall feedback.");
//            }
//        }, callback::onError);
//    }
//
//
//
//    // --- Phương thức gọi API Gemini chung ---
//    // Interface cho callback thành công của API chung
//    private interface GeminiApiSuccessListener {
//        void onResult(String responseString) throws JSONException;
//    }
//    private interface GeminiApiErrorListener {
//        void onError(String errorMessage);
//    }
//
//    private void callGeminiAPI(String promptText, GeminiApiSuccessListener successListener, GeminiApiErrorListener errorListener) {
//        if (GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY") || GEMINI_API_KEY.isEmpty() ) {
//            // KIỂM TRA LẠI API KEY NÀY, NẾU ĐÂY LÀ KEY THẬT THÌ BỎ ĐIỀU KIỆN SO SÁNH VỚI NÓ
//            Log.e(TAG, "Gemini API Key is not set, is a placeholder, or matches a known test key!");
//            String simulatedErrorJson = "AI Feedback service is temporarily unavailable (API Key issue).";
//            // Simulating placeholder key issue
//            if (promptText.contains("JSON array Response:")) { // Inline error analysis
//                // Trả về mảng rỗng để không báo lỗi UI, chỉ log
//                mainThreadHandler.postDelayed(() -> {
//                    Log.w(TAG, "Simulating empty array for inline errors due to API key issue.");
//                    try {
//                        successListener.onResult("[]");
//                    } catch (JSONException e) { errorListener.onError(e.getMessage());}
//                }, 500);
//
//            } else { // Overall feedback
//                mainThreadHandler.postDelayed(() -> {
//                    Log.w(TAG, "Simulating API error for overall feedback due to API key issue.");
//                    errorListener.onError(simulatedErrorJson);
//                }, 500);
//            }
//            return;
//        }
//
//        executorService.execute(() -> {
//            HttpURLConnection conn = null;
//            try {
//                URL url = new URL(GEMINI_API_URL);
//                conn = (HttpURLConnection) url.openConnection();
//                conn.setRequestMethod("POST");
//                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
//                conn.setDoOutput(true);
//                conn.setConnectTimeout(20000);
//                conn.setReadTimeout(20000);
//
//                JSONObject jsonBody = new JSONObject();
//                JSONArray contentsArray = new JSONArray();
//                JSONObject content = new JSONObject();
//                JSONArray partsArray = new JSONArray();
//                JSONObject part = new JSONObject();
//                part.put("text", promptText);
//                partsArray.put(part);
//                content.put("parts", partsArray);
//                contentsArray.put(content);
//                jsonBody.put("contents", contentsArray);
//
//                // Yêu cầu Gemini trả về JSON nếu có thể (cho prompt inline error)
//                if (promptText.contains("JSON Array Response:") || promptText.contains("Provide only the JSON object as your response.")) {
//                    JSONObject generationConfig = new JSONObject();
//                    generationConfig.put("response_mime_type", "application/json");
//                    jsonBody.put("generationConfig", generationConfig);
//                }
//
//                try (OutputStream os = conn.getOutputStream()) { os.write(jsonBody.toString().getBytes("utf-8")); }
//
//                int responseCode = conn.getResponseCode();
//                Log.d(TAG, "Gemini API Response Code: " + responseCode + " for prompt type: " + (promptText.contains("JSON Array Response:") ? "Inline" : "Overall"));
//
//                if (responseCode == HttpURLConnection.HTTP_OK) {
//                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
//                        StringBuilder response = new StringBuilder();
//                        String responseLine;
//                        while ((responseLine = br.readLine()) != null) response.append(responseLine);
//
//                        Log.d(TAG, "Raw Gemini Response: " + response.toString());
//                        JSONObject fullJsonResponse = new JSONObject(response.toString());
//                        JSONArray candidates = fullJsonResponse.getJSONArray("candidates");
//                        if (candidates.length() > 0) {
//                            JSONObject firstCandidate = candidates.getJSONObject(0);
//                            JSONObject candidateContent = firstCandidate.getJSONObject("content");
//                            JSONArray candidateParts = candidateContent.getJSONArray("parts");
//                            if (candidateParts.length() > 0) {
//                                String resultText = candidateParts.getJSONObject(0).getString("text");
//                                // Gemini có thể vẫn gói JSON trong markdown, loại bỏ nếu cần
//                                if (resultText.startsWith("```json")) {
//                                    resultText = resultText.substring(7); // Bỏ ```json\n
//                                    if (resultText.endsWith("```")) {
//                                        resultText = resultText.substring(0, resultText.length() - 3);
//                                    }
//                                } else if (resultText.startsWith("```")) {
//                                    resultText = resultText.substring(3);
//                                    if (resultText.endsWith("```")) {
//                                        resultText = resultText.substring(0, resultText.length() - 3);
//                                    }
//                                }
//                                successListener.onResult(resultText.trim());
//                            } else throw new JSONException("Parts array is empty in Gemini response");
//                        } else throw new JSONException("Candidates array is empty in Gemini response");
//                    }
//                } else {
//                    StringBuilder errorResponse = new StringBuilder();
//                    if (conn.getErrorStream() != null) {
//                        try (BufferedReader brError = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
//                            String line; while ((line = brError.readLine()) != null) errorResponse.append(line.trim());
//                        }
//                    } else { errorResponse.append("No error stream data."); }
//                    Log.e(TAG, "Gemini API Error Response: " + errorResponse.toString());
//                    errorListener.onError("Server error: " + responseCode + ". " + errorResponse);
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error calling/processing Gemini API", e);
//                errorListener.onError("Client-side error: " + e.getMessage());
//            } finally {
//                if (conn != null) conn.disconnect();
//            }
//        });
//    }
//
//
//    public void onPause() { Log.d(TAG, "onPause called by View.");
//        if (inlineAnalysisRunnable != null) { // Hủy bỏ debounce nếu activity pause
//            inlineAnalysisHandler.removeCallbacks(inlineAnalysisRunnable);
//        }
//    }
//
//    public void onResume() {
//        Log.d(TAG, "onResume. State: " + currentButtonState + ", AllSuccess: " + allCriteriaSuccess + ", EditingAfterFeedback: " + isUserEditingAfterFeedback);
//        if (view != null) {
//            updateSubmitButtonBasedOnState();
//        }
//    }
//
//    public void onDestroy() {
//        Log.d(TAG, "onDestroy called.");
//        if (inlineAnalysisRunnable != null) {
//            inlineAnalysisHandler.removeCallbacks(inlineAnalysisRunnable);
//        }
//        if (executorService != null && !executorService.isShutdown()) {
//            executorService.shutdownNow();
//        }
//        this.view = null;
//    }
//
//    // ADDED: New method to toggle the feedback panel visibility
//    public void onReviewFeedbackClicked() {
//        if (view == null) return;
//        Log.d(TAG, "Review Feedback clicked. Current isFeedbackPanelVisible: " + isFeedbackPanelVisible);
//        isFeedbackPanelVisible = !isFeedbackPanelVisible; // Toggle the flag
//        view.setFeedbackPanelVisibility(isFeedbackPanelVisible); // Apply the visibility
//        if (isFeedbackPanelVisible) {
//            view.focusOnFeedbackPanel(); // Scroll to feedback panel if it becomes visible
//        }
//        // No need to call updateSubmitButtonBasedOnState here, as button state/text doesn't change.
//    }
//
//    public boolean handleBackPressed() { return false; }
//    public int getCurrentButtonState() { return currentButtonState; }
//    public boolean areAllCriteriaSuccess() { return this.allCriteriaSuccess; }
//    public boolean isUserEditingAfterFeedback() { return this.isUserEditingAfterFeedback; }
//    public String getSubmittedTextForCurrentFeedback() { return this.submittedTextForCurrentFeedback; }
//}

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
    // API Key bạn cung cấp. THAY BẰNG API KEY THẬT CỦA BẠN.
    private static final String GEMINI_API_KEY = "AIzaSyAKitUIzcsW3Gd5SyeTLTrcGnJcPTDG09c"; // <<<< THAY KEY CỦA BẠN VÀO ĐÂY
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
    private boolean editButtonForcedByLoad = false; // Cờ mới

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
        void setSeeRevisedVersionButtonVisibility(boolean visible);
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
                    }
                } else {
                    Log.w(TAG, "User data node not found for UID: " + firebaseUid);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load Microsoft user data.", error.toException());
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
        editButtonForcedByLoad = false; // Khởi tạo cờ mới

        view.setUIElementsVisibility(false);
        view.setFeedbackPanelVisibility(false);
        view.setFeedbackTriggerVisibility(false);
        view.setSeeRevisedVersionButtonVisibility(false);
        view.setAnswerEditTextEnabled(true);

        loadExerciseDataFromFirebase();
        loadAllExerciseTitlesFromFirebase();
        loadSavedUserAnswerFromFirebase();
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
                    }
                    currentWritingExercise = null;
                } else {
                    String scriptPrompt = snapshot.child("script").getValue(String.class);
                    if (scriptPrompt != null && !scriptPrompt.isEmpty()) {
                        currentWritingExercise = new WritingExercise(snapshot.getKey(), exerciseTitle, scriptPrompt);
                        if (view != null) {
                            view.displayExerciseTitle(currentWritingExercise.getTitle());
                            view.displayQuestionPrompt(currentWritingExercise.getScript());
                        }
                    } else {
                        currentWritingExercise = new WritingExercise(snapshot.getKey(), exerciseTitle, "Writing prompt not found.");
                        if (view != null) {
                            view.displayExerciseTitle(exerciseTitle);
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
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null || levelName == null || topicTitle == null || exerciseTitle == null) {
            Log.w(TAG, "Cannot load saved answer: User not logged in or missing exercise identifiers.");
            hasLoadedSavedAnswer = false;
            checkIfAllDataLoadedAndReady();
            return;
        }

        final String userIdForPath;
        if (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null && !currentMicrosoftUser.getUserId().isEmpty()) {
            userIdForPath = currentMicrosoftUser.getUserId();
            Log.d(TAG, "Loading saved answer using Microsoft User ID: " + userIdForPath);
        } else {
            userIdForPath = firebaseUser.getUid();
            Log.d(TAG, "Loading saved answer using Firebase User ID (as key under MicrosoftUsers): " + userIdForPath);
        }

        DatabaseReference answerRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers")
                .child(userIdForPath)
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
                    currentButtonState = STATE_RETRY_WRITING;
                    isUserEditingAfterFeedback = false;
                    submittedTextForCurrentFeedback = savedAnswer;

                    if (feedbackSummary != null) {
                        allCriteriaSuccess = feedbackSummary.equalsIgnoreCase("All criteria success");
                    } else {
                        allCriteriaSuccess = false;
                    }
                    Log.d(TAG, "Saved answer loaded. Initial state: STATE_RETRY_WRITING, AllSuccess: " + allCriteriaSuccess);
                } else {
                    hasLoadedSavedAnswer = false;
                    Log.d(TAG, "No saved answer found for user " + userIdForPath + ", exercise " + exerciseTitle);
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
                ", titlesLoaded=" + titlesLoaded + ". Current hasLoadedSavedAnswer=" + hasLoadedSavedAnswer);

        if (exerciseDataLoaded && titlesLoaded) {
            Log.d(TAG, "All initial data (exercise, titles) confirmed loaded. Saved answer status known.");
            if (view == null) return;

            view.setUIElementsVisibility(true);
            view.setAnswerInputVisibility(true);

            if (currentWritingExercise == null || currentWritingExercise.getScript() == null ||
                    currentWritingExercise.getScript().contains("not available") || currentWritingExercise.getScript().contains("not found")) {
                // Content not available, updateSubmitButtonBasedOnState will handle disabling UI
            } else {
                if (hasLoadedSavedAnswer) {
                    currentButtonState = STATE_RETRY_WRITING;
                } else {
                    currentButtonState = STATE_SUBMIT_WRITING;
                }
            }
            updateSubmitButtonBasedOnState();
        } else {
            Log.d(TAG, "Still waiting for some initial data to load before full UI setup.");
        }
    }

    private void updateSubmitButtonBasedOnState() {
        if (view == null) return;
        Log.d(TAG, "updateSubmitButtonBasedOnState - CurrentState: " + currentButtonState +
                ", isEditingAfterFeedback: " + isUserEditingAfterFeedback +
                ", allSuccess: " + allCriteriaSuccess +
                ", isFeedbackPanelVisible (flag): " + isFeedbackPanelVisible +
                ", hasLoadedSavedAnswer (flag before consumption): " + hasLoadedSavedAnswer +
                ", editButtonForcedByLoad (flag): " + editButtonForcedByLoad);


        boolean contentNotAvailable = currentWritingExercise == null || currentWritingExercise.getScript() == null ||
                currentWritingExercise.getScript().equals("Writing prompt not available for this exercise.") ||
                currentWritingExercise.getScript().equals("Writing prompt not found.") ||
                currentWritingExercise.getScript().equals("Content not available for this exercise.");

        if (contentNotAvailable) {
            view.setSubmitButtonState("N/A", false);
            view.setFeedbackPanelVisibility(false);
            view.setFeedbackTriggerVisibility(false);
            view.setSeeRevisedVersionButtonVisibility(false);
            view.setAnswerEditTextEnabled(false);
            if (currentWritingExercise == null && exerciseDataLoaded) {
                view.displayQuestionPrompt("Content not available for this exercise.");
            }
            isFeedbackPanelVisible = false;
            editButtonForcedByLoad = false; // Reset if content not available
            return;
        }

        if (currentButtonState == STATE_SUBMIT_WRITING) {
            view.setAnswerEditTextEnabled(true);
            view.setFeedbackPanelVisibility(false);
            view.setFeedbackTriggerVisibility(false);
            view.setSeeRevisedVersionButtonVisibility(false);
            String currentText = view.getCurrentAnswerText();
            onAnswerTextChanged(currentText != null ? currentText : "");
            if (TextUtils.isEmpty(currentText == null ? "" : currentText.trim())) {
                view.setSubmitButtonState("Submit", false);
            } else {
                view.setSubmitButtonState("Submit", true);
            }
            isFeedbackPanelVisible = false;
            editButtonForcedByLoad = false; // Not forced when in submit writing state
        } else if (currentButtonState == STATE_RETRY_WRITING) {
            view.setAnswerEditTextEnabled(false);

            if (hasLoadedSavedAnswer) {
                Log.d(TAG, "STATE_RETRY_WRITING (Post-Load): Setting button to 'Edit', hiding feedback.");
                view.setSubmitButtonState("Edit", true);
                editButtonForcedByLoad = true; // Đặt cờ này
                view.setFeedbackPanelVisibility(false);
                view.setFeedbackTriggerVisibility(false);
                isFeedbackPanelVisible = false;
                view.setSeeRevisedVersionButtonVisibility(!allCriteriaSuccess);
            } else {
                // This is STATE_RETRY_WRITING after a user submission (not an initial load).
                editButtonForcedByLoad = false; // Đặt lại cờ này
                Log.d(TAG, "STATE_RETRY_WRITING (Post-Submit): Setting button based on allCriteriaSuccess.");
                if (allCriteriaSuccess) {
                    view.setSubmitButtonState("Done", true);
                } else {
                    view.setSubmitButtonState("Edit", true);
                }
                view.setFeedbackTriggerVisibility(true);
                view.setFeedbackPanelVisibility(isFeedbackPanelVisible);
                view.setSeeRevisedVersionButtonVisibility(!allCriteriaSuccess);
            }
        }

        if (hasLoadedSavedAnswer) {
            hasLoadedSavedAnswer = false;
            Log.d(TAG, "hasLoadedSavedAnswer flag consumed.");
        }
    }

    public void onAnswerTextChanged(String currentText) {
        if (view == null) return;

        if (currentButtonState == STATE_SUBMIT_WRITING) {
            if (isUserEditingAfterFeedback) {
                boolean hasChanged = !currentText.trim().equals(submittedTextForCurrentFeedback.trim());
                view.setSubmitButtonState("Submit", hasChanged && !TextUtils.isEmpty(currentText.trim()));
            } else {
                view.setSubmitButtonState("Submit", !TextUtils.isEmpty(currentText.trim()));
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
                // Nếu allCriteriaSuccess là true VÀ nút "Edit" KHÔNG phải do load lại dữ liệu ép buộc
                if (allCriteriaSuccess && !editButtonForcedByLoad) {
                    Log.i(TAG, "'Done' button clicked (allCriteriaSuccess=true, editButtonForcedByLoad=false). Finishing activity.");
                    view.finishActivity();
                } else {
                    // Đây là trường hợp nút là "Edit" thực sự (do allCriteriaSuccess=false HOẶC do editButtonForcedByLoad=true)
                    Log.i(TAG, "'Edit' button clicked (allCriteriaSuccess=" + allCriteriaSuccess + ", editButtonForcedByLoad=" + editButtonForcedByLoad + "). Enabling edit mode.");
                    currentButtonState = STATE_SUBMIT_WRITING;
                    isUserEditingAfterFeedback = true;
                    isFeedbackPanelVisible = false;
                    editButtonForcedByLoad = false; // Tiêu thụ/Reset cờ này

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

        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        String userIdToUseForSaving = null;

        if (firebaseUser != null) {
            if (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null && !currentMicrosoftUser.getUserId().isEmpty()) {
                userIdToUseForSaving = currentMicrosoftUser.getUserId();
                Log.d(TAG, "Saving/Updating answer for Microsoft User ID: " + userIdToUseForSaving);
            } else {
                userIdToUseForSaving = firebaseUser.getUid();
                Log.d(TAG, "Saving/Updating answer for Firebase User ID (as key under MicrosoftUsers): " + userIdToUseForSaving);
            }

            if (currentWritingExercise != null) {
                saveUserWritingAnswerToFirebase(
                        userIdToUseForSaving,
                        currentWritingExercise.getId(),
                        levelName,
                        topicTitle,
                        textBeingSubmitted,
                        "Feedback pending..."
                );
            } else {
                Log.w(TAG, "Cannot save user answer: currentWritingExercise is null.");
            }
        } else {
            Log.w(TAG, "Cannot save user answer: No Firebase user logged in.");
        }

        final String finalUserIdForFeedbackUpdate = userIdToUseForSaving;

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
                        submittedTextForCurrentFeedback = textBeingSubmitted;
                        isFeedbackPanelVisible = true;
                        editButtonForcedByLoad = false; // Reset after a successful submission and feedback cycle

                        updateSubmitButtonBasedOnState();
                        if (isFeedbackPanelVisible) view.focusOnFeedbackPanel();

                        if (finalUserIdForFeedbackUpdate != null && currentWritingExercise != null) {
                            String feedbackSummary = allCriteriaSuccess ? "All criteria success" : "Needs improvement";
                            updateUserWritingAnswerFeedback(
                                    finalUserIdForFeedbackUpdate,
                                    currentWritingExercise.getId(),
                                    feedbackSummary
                            );
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing overall feedback JSON from Gemini", e);
                        if (view != null) view.showFailToast("Error processing feedback: " + e.getMessage());
                        isFeedbackPanelVisible = false;
                        currentButtonState = STATE_SUBMIT_WRITING;
                        editButtonForcedByLoad = false;
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
                    editButtonForcedByLoad = false;
                    // currentButtonState remains STATE_SUBMIT_WRITING from before calling API if it was.
                    updateSubmitButtonBasedOnState();
                });
            }
        });
    }

    private void saveUserWritingAnswerToFirebase(String userId, String exerciseId, String level, String topic, String answer, String initialFeedbackSummary) {
        if (userId == null || exerciseId == null) {
            Log.w(TAG, "Cannot save user writing answer: userId or exerciseId is null.");
            return;
        }
        DatabaseReference userAnswersRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers")
                .child(userId)
                .child("Progress")
                .child("WritingAnswers")
                .child(exerciseId);

        UserWritingAnswer userAnswer = new UserWritingAnswer(
                exerciseId,
                level,
                topic,
                answer,
                System.currentTimeMillis(),
                initialFeedbackSummary
        );

        userAnswersRef.setValue(userAnswer.toMap())
                .addOnSuccessListener(aVoid -> Log.i(TAG, "User writing answer saved successfully for user: " + userId + ", exercise: " + exerciseId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save user writing answer for user: " + userId + ", exercise: " + exerciseId, e));
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
        updates.put("lastUpdatedTimestamp", System.currentTimeMillis());

        userAnswerRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.i(TAG, "Feedback summary updated successfully for user: " + userId + ", exercise: " + exerciseId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update feedback summary for user: " + userId + ", exercise: " + exerciseId, e));
    }

    interface FeedbackCallback {
        void onSuccess(JSONObject feedbackJson);
        void onError(String error);
    }

    private void getFeedbackFromGemini(String originalPrompt, String userAnswer, FeedbackCallback callback) {
        // Prompt mới, nhấn mạnh sự nghiêm ngặt cho Grammar & Vocabulary
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
                // Parser cho feedback tổng thể (JSON object)
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
        if (GEMINI_API_KEY.equals("GEMINI_API_KEY") || GEMINI_API_KEY.isEmpty() ) {
            Log.e(TAG, "Gemini API Key is a placeholder, empty, or a sample key. Please set a valid API key.");
            String simulatedError = "AI Feedback service is temporarily unavailable (API Key configuration issue).";
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
                                if (fullJsonResponse.getJSONObject("promptFeedback").has("blockReason")) {
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
        Log.d(TAG, "onResume. Re-evaluating UI state.");
        if (exerciseDataLoaded && titlesLoaded && view != null) {
            updateSubmitButtonBasedOnState();
            if (currentButtonState == STATE_SUBMIT_WRITING) {
                String currentText = view.getCurrentAnswerText();
                onAnswerTextChanged(currentText != null ? currentText : "");
            }
        } else {
            Log.d(TAG, "onResume: Core data not yet loaded, UI update will be handled by loading callbacks.");
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
}
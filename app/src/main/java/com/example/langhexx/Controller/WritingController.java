package com.example.langhexx.Controller;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.text.TextUtils;

import androidx.annotation.NonNull;

// Thêm import cho ErrorDetail
import com.example.langhexx.Model.ErrorDetail;
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
import java.util.List; // Thêm import List
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WritingController {

    private static final String TAG = "WritingController";
    // API Key bạn cung cấp
    private static final String GEMINI_API_KEY = "AIzaSyCf-9jplfin2aWdFAdxWcCdzox5wzIkBbQ";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    // Handler cho inline analysis debouncing
    private final Handler inlineAnalysisHandler = new Handler(Looper.getMainLooper());
    private Runnable inlineAnalysisRunnable;
    private static final long INLINE_ANALYSIS_DEBOUNCE_MS = 1500; // 1.5 giây

    //
    private MicrosoftUser currentMicrosoftUser; // Giả sử bạn có cách lấy thông tin này khi đăng nhập
    private FirebaseAuth mAuth;

    public interface ViewInterface {
        // ... các phương thức hiện có ...
        void displayExerciseTitle(String title);
        void displayQuestionPrompt(String prompt);
        void showToast(String message);
        void showFailToast(String message);
        void showConfirmationDialog(String title, String message, Runnable onConfirm);
        void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle);
        void finishActivity();
        void setSubmitButtonState(String text, boolean enabled);
        void setUIElementsVisibility(boolean visible);
        void showLoading(String message); // Để dùng cho inline analysis loading nếu cần
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

        // Phương thức mới cho gạch chân lỗi
        void applyInlineErrorHighlighting(List<ErrorDetail> errors);
        void clearInlineErrorHighlighting();
    }

    // ... các hằng số và biến thành viên hiện có ...
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
        this.mAuth = FirebaseAuth.getInstance(); // Khởi tạo FirebaseAuth

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
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null) {
            // Giả sử bạn đã lưu thông tin người dùng Microsoft trong Realtime Database
            // với key là UID của FirebaseUser
            loadMicrosoftUserData(firebaseUser.getUid());
        }
    }

    private void loadMicrosoftUserData(String firebaseUid) {
        DatabaseReference userRef = databaseReference.child("Users").child(firebaseUid);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Giả sử cấu trúc User trong Firebase của bạn có các trường tương ứng
                    // hoặc bạn có một node con "microsoftInfo"
                    String msUserId = snapshot.child("microsoftGraphId").getValue(String.class); // Lấy từ cấu trúc ảnh bạn gửi
                    String email = snapshot.child("email").getValue(String.class);
                    String displayName = snapshot.child("name").getValue(String.class); // Hoặc "displayName"

                    if (msUserId != null) { // Kiểm tra xem có phải là user Microsoft không dựa trên sự tồn tại của microsoftGraphId
                        currentMicrosoftUser = new MicrosoftUser(msUserId, email, displayName);
                        Log.d(TAG, "Microsoft user data loaded: " + displayName);
                    } else {
                        Log.d(TAG, "User " + firebaseUid + " is not a Microsoft Graph linked user or data is missing.");
                    }
                } else {
                    Log.w(TAG, "Microsoft user data not found for UID: " + firebaseUid);
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

        view.setUIElementsVisibility(false);
        view.setFeedbackPanelVisibility(false);
        view.setFeedbackTriggerVisibility(false);
        view.setSeeRevisedVersionButtonVisibility(false);
        view.setAnswerEditTextEnabled(true);

        loadExerciseDataFromFirebase();
        loadAllExerciseTitlesFromFirebase();
    }

    private void handleInitializationError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (view != null) { view.showFailToast(errorMessage); view.finishActivity(); }
    }

    // ... loadExerciseDataFromFirebase, loadAllExerciseTitlesFromFirebase, checkIfAllDataLoadedAndReady giữ nguyên ...
    private void loadExerciseDataFromFirebase() {
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
                        view.setAnswerEditTextEnabled(false);
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
                        view.setAnswerEditTextEnabled(false);
                    }
                }
                exerciseDataLoaded = true; checkIfAllDataLoadedAndReady();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase loading cancelled/failed: " + error.getMessage(), error.toException());
                if (view != null) {
                    view.showFailToast("Error loading exercise: " + error.getMessage());
                    view.setAnswerEditTextEnabled(false);
                }
                exerciseDataLoaded = true; checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void loadAllExerciseTitlesFromFirebase() {
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
            onAnswerTextChanged(view != null ? "" : "");
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
            view.setAnswerEditTextEnabled(false);
            view.displayQuestionPrompt("Content not available for this exercise.");
            return;
        }

        switch (currentButtonState) {
            case STATE_SUBMIT_WRITING:
                view.setSubmitButtonState("Submit", true);
                view.setAnswerEditTextEnabled(true);
                if (isUserEditingAfterFeedback) {
                    view.setFeedbackPanelVisibility(true);
                    view.setFeedbackTriggerVisibility(false);
                    view.setSeeRevisedVersionButtonVisibility(!allCriteriaSuccess);
                } else {
                    view.setFeedbackPanelVisibility(false);
                    view.setFeedbackTriggerVisibility(false);
                    view.setSeeRevisedVersionButtonVisibility(false);
                }
                break;
            case STATE_RETRY_WRITING:
                view.setAnswerEditTextEnabled(false);
                if (allCriteriaSuccess) {
                    view.setSubmitButtonState("Done", true);
                } else {
                    view.setSubmitButtonState("Edit", true);
                }
                view.setFeedbackTriggerVisibility(true);
                view.setFeedbackPanelVisibility(false);
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

    public void onAnswerTextChanged(String currentText) {
        if (view == null) return;

        // Xử lý cho nút Submit
        if (currentButtonState == STATE_SUBMIT_WRITING) {
            if (isUserEditingAfterFeedback) {
                boolean hasChanged = !currentText.trim().equals(submittedTextForCurrentFeedback.trim());
                view.setSubmitButtonState("Submit", hasChanged);
            } else {
                view.setSubmitButtonState("Submit", !TextUtils.isEmpty(currentText.trim()));
            }
        }

        // Xử lý cho phân tích lỗi inline (debounced)
        // Chỉ thực hiện nếu EditText đang được phép chỉnh sửa
        if (view != null && currentButtonState == STATE_SUBMIT_WRITING && viewIsEditTextEnabled()) {
            // Hủy bỏ runnable cũ nếu có
            if (inlineAnalysisRunnable != null) {
                inlineAnalysisHandler.removeCallbacks(inlineAnalysisRunnable);
            }
            // Xóa highlight cũ ngay lập tức để người dùng biết rằng văn bản đang được phân tích lại
            // view.clearInlineErrorHighlighting(); // Cân nhắc: có thể làm màn hình nháy

            inlineAnalysisRunnable = () -> {
                Log.d(TAG, "Debounced: Analyzing text for inline errors: " + currentText);
                if (!TextUtils.isEmpty(currentText.trim())) {
                    requestInlineErrorAnalysis(currentText);
                } else {
                    view.clearInlineErrorHighlighting(); // Xóa lỗi nếu text rỗng
                }
            };
            inlineAnalysisHandler.postDelayed(inlineAnalysisRunnable, INLINE_ANALYSIS_DEBOUNCE_MS);
        } else if (view != null) {
            // Nếu không trong trạng thái cho phép inline analysis, xóa lỗi cũ nếu có
            view.clearInlineErrorHighlighting();
        }
    }
    // Phương thức trợ giúp để kiểm tra xem view có cho phép edit không (để tránh gọi khi view bị disable)
    private boolean viewIsEditTextEnabled() {
        // Cần một cách để view báo cho controller biết trạng thái của EditText
        // Tạm thời giả định là true nếu controller nghĩ là nó nên enabled
        return currentButtonState == STATE_SUBMIT_WRITING;
    }


    public void onSubmitButtonClicked(String userAnswer) {
        if (view == null) return;
        Log.d(TAG, "Submit button clicked. State: " + currentButtonState + ", AllSuccess: " + allCriteriaSuccess + ", EditingAfterFeedback: " + isUserEditingAfterFeedback);

        // Hủy bỏ phân tích inline đang chờ (nếu có) khi submit
        if (inlineAnalysisRunnable != null) {
            inlineAnalysisHandler.removeCallbacks(inlineAnalysisRunnable);
        }
        view.clearInlineErrorHighlighting(); // Xóa gạch chân lỗi khi submit

        switch (currentButtonState) {
            case STATE_SUBMIT_WRITING:
                allCriteriaSuccess = false;
                if (userAnswer.trim().isEmpty()) {
                    view.showFailToast("Please write something before submitting."); return;
                }
                if (currentWritingExercise == null || currentWritingExercise.getScript() == null || currentWritingExercise.getScript().isEmpty() || currentWritingExercise.getScript().contains("not available")) {
                    view.showFailToast("Cannot submit without a valid writing prompt."); return;
                }
                view.showConfirmationDialog("Confirm Submission", "Are you sure you want to submit your writing for feedback?", () -> {
                    proceedWithWritingSubmission(userAnswer, currentWritingExercise.getScript());
                });
                break;
            case STATE_RETRY_WRITING:
                if (allCriteriaSuccess) {
                    Log.i(TAG, "'Done' button clicked. Finishing activity.");
                    isUserEditingAfterFeedback = false;
                    view.finishActivity();
                } else {
                    Log.i(TAG, "'Edit' button clicked. Enabling edit mode.");
                    currentButtonState = STATE_SUBMIT_WRITING;
                    isUserEditingAfterFeedback = true;
                    allCriteriaSuccess = false;
                    updateSubmitButtonBasedOnState();
                    view.setSubmitButtonState("Submit", false);
                    view.requestFocusOnAnswerInput();
                    // Kích hoạt lại onAnswerTextChanged để có thể bắt đầu inline analysis nếu người dùng sửa
                    // Giả sử view sẽ cung cấp text hiện tại của EditText
                    // Hoặc tốt hơn là view gọi onAnswerTextChanged từ TextWatcher
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

//    private void proceedWithWritingSubmission(String userAnswer, String originalPrompt) {
//        if (view == null) return;
//        Log.i(TAG, "Proceeding with writing submission for text: " + userAnswer);
//        view.showLoading("Getting feedback...");
//        final String textBeingSubmitted = userAnswer;
//
//
//        getFeedbackFromGemini(originalPrompt, userAnswer, new FeedbackCallback() {
//            @Override
//            public void onSuccess(JSONObject feedbackJson) {
//                mainThreadHandler.post(() -> {
//                    if (view == null) return;
//                    view.hideLoading();
//                    try {
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
//                        submittedTextForCurrentFeedback = textBeingSubmitted;
//
//                        view.setFeedbackPanelVisibility(false);
//                        view.setFeedbackTriggerVisibility(true);
//
//                        updateSubmitButtonBasedOnState();
//
//                    } catch (JSONException e) {
//                        Log.e(TAG, "Error parsing structured feedback JSON", e);
//                        view.showFailToast("Error displaying feedback. Invalid AI format.");
//                        allCriteriaSuccess = false;
//                        currentButtonState = STATE_SUBMIT_WRITING;
//                        isUserEditingAfterFeedback = false;
//                        updateSubmitButtonBasedOnState();
//                        view.setSeeRevisedVersionButtonVisibility(true);
//                        view.setFeedbackPanelVisibility(false);
//                        view.setFeedbackTriggerVisibility(false);
//                    }
//                });
//            }
//
//            @Override
//            public void onError(String error) {
//                mainThreadHandler.post(() -> {
//                    if (view == null) return;
//                    view.hideLoading();
//                    view.showFailToast("Failed to get AI feedback: " + error);
//                    allCriteriaSuccess = false;
//                    currentButtonState = STATE_SUBMIT_WRITING;
//                    isUserEditingAfterFeedback = false;
//                    updateSubmitButtonBasedOnState();
//                    view.setSeeRevisedVersionButtonVisibility(true);
//                    view.setFeedbackPanelVisibility(false);
//                    view.setFeedbackTriggerVisibility(false);
//                });
//            }
//        });
//    }

    private void proceedWithWritingSubmission(String userAnswer, String originalPrompt) {
        if (view == null) return;
        Log.i(TAG, "Proceeding with writing submission for text: " + userAnswer);
        view.showLoading("Getting feedback...");
        final String textBeingSubmitted = userAnswer;

        // Lưu câu trả lời vào Firebase NẾU là người dùng Microsoft
        if (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null && currentWritingExercise != null) {
            saveUserWritingAnswerToFirebase(
                    currentMicrosoftUser.getUserId(), // Sử dụng ID người dùng Microsoft (hoặc Firebase UID nếu bạn muốn key là Firebase UID)
                    currentWritingExercise.getId(), // Hoặc exerciseTitle nếu đó là ID duy nhất
                    levelName,
                    topicTitle,
                    textBeingSubmitted,
                    "Feedback pending..." // Trạng thái ban đầu
            );
        } else if (mAuth.getCurrentUser() != null && currentWritingExercise != null) {
            // Nếu không phải là Microsoft user nhưng vẫn đăng nhập Firebase (ví dụ: email/password)
            // và bạn vẫn muốn lưu, bạn có thể sử dụng mAuth.getCurrentUser().getUid()
            Log.d(TAG, "Current user is Firebase user but not identified as Microsoft Graph linked user, or Microsoft user data not loaded yet.");
            // Tùy chọn: lưu với Firebase UID nếu currentMicrosoftUser là null nhưng FirebaseUser tồn tại
            saveUserWritingAnswerToFirebase(
                    mAuth.getCurrentUser().getUid(), // Sử dụng Firebase UID làm key
                    currentWritingExercise.getId(),
                    levelName,
                    topicTitle,
                    textBeingSubmitted,
                    "Feedback pending..."
            );
        }


        getFeedbackFromGemini(originalPrompt, userAnswer, new FeedbackCallback() {
            @Override
            public void onSuccess(JSONObject feedbackJson) {
                mainThreadHandler.post(() -> {
                    if (view == null) return;
                    view.hideLoading();
                    try {
                        // ... (phần xử lý feedback JSON như cũ) ...
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
                        submittedTextForCurrentFeedback = textBeingSubmitted; // Giữ nguyên

                        view.setFeedbackPanelVisibility(false);
                        view.setFeedbackTriggerVisibility(true);

                        updateSubmitButtonBasedOnState();

                        // Cập nhật feedback summary trong Firebase sau khi có kết quả
                        String feedbackSummary = allCriteriaSuccess ? "All criteria success" : "Needs improvement";
                        if (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null && currentWritingExercise != null) {
                            updateUserWritingAnswerFeedback(
                                    currentMicrosoftUser.getUserId(), // Hoặc Firebase UID
                                    currentWritingExercise.getId(), // Hoặc exerciseTitle
                                    feedbackSummary
                            );
                        } else if (mAuth.getCurrentUser() != null && currentWritingExercise != null) {
                            updateUserWritingAnswerFeedback(
                                    mAuth.getCurrentUser().getUid(),
                                    currentWritingExercise.getId(),
                                    feedbackSummary
                            );
                        }
                    } catch (JSONException e) {
                        // ... (xử lý lỗi JSON như cũ) ...
                    }
                });
            }
            @Override
            public void onError(String error) {
                // ... (xử lý lỗi API như cũ) ...
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
                .child("MicrosoftUsers") // <--- ADDED THIS NODE
                .child(userId)          // This should be the Microsoft Graph ID
                .child("WritingAnswers")
                .child(exerciseId);
        UserWritingAnswer userAnswer = new UserWritingAnswer(
                exerciseId,
                level,
                topic,
                answer,
                System.currentTimeMillis(), // Hoặc dùng ServerValue.TIMESTAMP cho Firebase
                initialFeedbackSummary
        );

        // Sử dụng toMap() để dễ dàng ghi object vào Firebase
        Map<String, Object> answerValues = userAnswer.toMap();
        userAnswersRef.setValue(answerValues)
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
                .child("WritingAnswers")
                .child(exerciseId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("feedbackSummary", feedbackSummary);
        // Bạn cũng có thể cập nhật timestamp ở đây nếu muốn
        // updates.put("lastUpdatedTimestamp", ServerValue.TIMESTAMP);

        userAnswerRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.i(TAG, "Feedback summary updated successfully for user: " + userId + ", exercise: " + exerciseId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update feedback summary for user: " + userId + ", exercise: " + exerciseId, e));
    }




    interface FeedbackCallback {
        void onSuccess(JSONObject feedbackJson); // Cho feedback tổng thể
        void onError(String error);
    }

    // Callback mới cho inline errors
    interface InlineErrorCallback {
        void onSuccess(List<ErrorDetail> errors);
        void onError(String error);
    }


    private void getFeedbackFromGemini(String originalPrompt, String userAnswer, FeedbackCallback callback) {
        // Prompt cho feedback tổng thể (như cũ)
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

    private void requestInlineErrorAnalysis(String textToAnalyze) {
        if (view == null) return;
        Log.d(TAG, "Requesting inline error analysis for: " + textToAnalyze);
        // view.showLoading("Analyzing text..."); // Cân nhắc hiển thị loading nếu cần

        String promptForInlineErrors = "You are an English language proofreading assistant. " +
                "Analyze the following text for spelling, vocabulary, and grammar errors. " +
                "For each error, identify the exact erroneous text, its 0-based start index, and its 0-based exclusive end index in the original text. " +
                "Also specify the type of error ('spelling', 'grammar', 'vocabulary') and provide a brief suggestion if applicable. " +
                "Respond ONLY with a JSON array, where each element is an object like this: " +
                "{\"error_text\": \"<original_word_or_phrase>\", \"start_index\": <number>, \"end_index\": <number>, \"type\": \"<error_type>\", \"suggestion\": \"<suggested_correction>\"}. " +
                "If there are no errors, respond with an empty JSON array [].\n\n" +
                "Text to analyze: \"" + textToAnalyze + "\"\n\n" +
                "JSON Array Response:";

        callGeminiAPI(promptForInlineErrors, responseString -> {
            // mainThreadHandler.post(() -> { if (view != null) view.hideLoading(); });
            try {
                JSONArray errorsJsonArray = new JSONArray(responseString);
                List<ErrorDetail> errorDetails = new ArrayList<>();
                for (int i = 0; i < errorsJsonArray.length(); i++) {
                    JSONObject errorObj = errorsJsonArray.getJSONObject(i);
                    errorDetails.add(new ErrorDetail(
                            errorObj.getString("error_text"),
                            errorObj.getInt("start_index"),
                            errorObj.getInt("end_index"),
                            errorObj.getString("type"),
                            errorObj.optString("suggestion", "") // suggestion có thể không có
                    ));
                }
                if (view != null) {
                    view.clearInlineErrorHighlighting(); // Xóa lỗi cũ trước khi áp dụng lỗi mới
                    view.applyInlineErrorHighlighting(errorDetails);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing inline errors JSON from Gemini: " + responseString, e);
                if (view != null) {
                    // view.showFailToast("Could not parse inline errors.");
                    view.clearInlineErrorHighlighting(); // Xóa lỗi nếu parse lỗi
                }
            }
        }, error -> {
            // mainThreadHandler.post(() -> { if (view != null) view.hideLoading(); });
            Log.e(TAG, "Error getting inline error analysis: " + error);
            if (view != null) {
                // view.showFailToast("AI analysis error: " + error);
                view.clearInlineErrorHighlighting(); // Xóa lỗi nếu API lỗi
            }
        });
    }

    // --- Phương thức gọi API Gemini chung ---
    // Interface cho callback thành công của API chung
    private interface GeminiApiSuccessListener {
        void onResult(String responseString) throws JSONException;
    }
    private interface GeminiApiErrorListener {
        void onError(String errorMessage);
    }

    private void callGeminiAPI(String promptText, GeminiApiSuccessListener successListener, GeminiApiErrorListener errorListener) {
        if (GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY") || GEMINI_API_KEY.isEmpty() ) {
            // KIỂM TRA LẠI API KEY NÀY, NẾU ĐÂY LÀ KEY THẬT THÌ BỎ ĐIỀU KIỆN SO SÁNH VỚI NÓ
            Log.e(TAG, "Gemini API Key is not set, is a placeholder, or matches a known test key!");
            String simulatedErrorJson = "AI Feedback service is temporarily unavailable (API Key issue).";
            // Simulating placeholder key issue
            if (promptText.contains("JSON array Response:")) { // Inline error analysis
                // Trả về mảng rỗng để không báo lỗi UI, chỉ log
                mainThreadHandler.postDelayed(() -> {
                    Log.w(TAG, "Simulating empty array for inline errors due to API key issue.");
                    try {
                        successListener.onResult("[]");
                    } catch (JSONException e) { errorListener.onError(e.getMessage());}
                }, 500);

            } else { // Overall feedback
                mainThreadHandler.postDelayed(() -> {
                    Log.w(TAG, "Simulating API error for overall feedback due to API key issue.");
                    errorListener.onError(simulatedErrorJson);
                }, 500);
            }
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

                // Yêu cầu Gemini trả về JSON nếu có thể (cho prompt inline error)
                if (promptText.contains("JSON Array Response:") || promptText.contains("Provide only the JSON object as your response.")) {
                    JSONObject generationConfig = new JSONObject();
                    generationConfig.put("response_mime_type", "application/json");
                    jsonBody.put("generationConfig", generationConfig);
                }

                try (OutputStream os = conn.getOutputStream()) { os.write(jsonBody.toString().getBytes("utf-8")); }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Gemini API Response Code: " + responseCode + " for prompt type: " + (promptText.contains("JSON Array Response:") ? "Inline" : "Overall"));

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
                                String resultText = candidateParts.getJSONObject(0).getString("text");
                                // Gemini có thể vẫn gói JSON trong markdown, loại bỏ nếu cần
                                if (resultText.startsWith("```json")) {
                                    resultText = resultText.substring(7); // Bỏ ```json\n
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
                            } else throw new JSONException("Parts array is empty in Gemini response");
                        } else throw new JSONException("Candidates array is empty in Gemini response");
                    }
                } else {
                    StringBuilder errorResponse = new StringBuilder();
                    if (conn.getErrorStream() != null) {
                        try (BufferedReader brError = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                            String line; while ((line = brError.readLine()) != null) errorResponse.append(line.trim());
                        }
                    } else { errorResponse.append("No error stream data."); }
                    Log.e(TAG, "Gemini API Error Response: " + errorResponse.toString());
                    errorListener.onError("Server error: " + responseCode + ". " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calling/processing Gemini API", e);
                errorListener.onError("Client-side error: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }


    public void onPause() { Log.d(TAG, "onPause called by View.");
        if (inlineAnalysisRunnable != null) { // Hủy bỏ debounce nếu activity pause
            inlineAnalysisHandler.removeCallbacks(inlineAnalysisRunnable);
        }
    }

    public void onResume() {
        Log.d(TAG, "onResume. State: " + currentButtonState + ", AllSuccess: " + allCriteriaSuccess + ", EditingAfterFeedback: " + isUserEditingAfterFeedback);
        if (view != null) {
            updateSubmitButtonBasedOnState();
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

    public boolean handleBackPressed() { return false; }
    public int getCurrentButtonState() { return currentButtonState; }
    public boolean areAllCriteriaSuccess() { return this.allCriteriaSuccess; }
    public boolean isUserEditingAfterFeedback() { return this.isUserEditingAfterFeedback; }
    public String getSubmittedTextForCurrentFeedback() { return this.submittedTextForCurrentFeedback; }
}
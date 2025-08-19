
package vn.enghexteam.enghex.Controller;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import vn.enghexteam.enghex.BuildConfig;
import vn.enghexteam.enghex.Model.MicrosoftUser;
import vn.enghexteam.enghex.Model.WritingExercise;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WritingController {

    private static final String TAG = "WritingController";
    private static final String OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY;
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
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
        void finishActivity();
        void setSubmitButtonState(String text, boolean enabled);
        void setUIElementsVisibility(boolean visible);
        void showLoading(String message);
        void hideLoading();
        void clearAnswerInput();
        void setFeedbackPanelVisibility(boolean visible);
        void setFeedbackTriggerVisibility(boolean visible);
        void setAnswerInputVisibility(boolean visible);
        void setAnswerEditTextEnabled(boolean enabled);
        void requestFocusOnAnswerInput();
        void focusOnFeedbackPanel();
        void clearInlineErrorHighlighting();
        void displaySavedAnswer(String answer);
        String getCurrentAnswerText();
        void displayStructuredAIFeedback(
                String taskResponseFeedback, int taskResponseIconType, int taskScore,
                String coherenceCohesionFeedback, int coherenceCohesionIconType, int coherenceScore,
                String grammarVocabularyFeedback, int grammarVocabularyIconType, int grammarScore,
                String lengthFeedback, int lengthIconType, int lengthScore,
                int totalScore
        );

    }

    public static final int STATE_SUBMIT_WRITING = 0;
    public static final int STATE_RETRY_WRITING = 1;
    private int currentButtonState = STATE_SUBMIT_WRITING;

    private ViewInterface view;
    private String levelName;

    private String topicId;
    private String exerciseId;

    private String topicDisplayName;
    private String exerciseDisplayTitle;

    private WritingExercise currentWritingExercise;
    private ArrayList<WritingExercise> allExercisesInTopic;

    private boolean exerciseDataLoaded = false;
    private boolean allExercisesLoaded = false;
    private boolean isUserEditingAfterFeedback = false;
    private boolean allCriteriaSuccess = false;
    private String submittedTextForCurrentFeedback = "";

    private DatabaseReference databaseReference;

    public WritingController(ViewInterface view, Intent intent) {
        this.view = view;
        this.allExercisesInTopic = new ArrayList<>();
        this.databaseReference = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        this.mAuth = FirebaseAuth.getInstance();

        if (intent != null) {
            levelName = intent.getStringExtra("LEVEL_NAME");
            topicId = intent.getStringExtra("TOPIC_ID");
            topicDisplayName = intent.getStringExtra("TOPIC_DISPLAY_NAME");
            exerciseId = intent.getStringExtra("EXERCISE_ID");
            exerciseDisplayTitle = intent.getStringExtra("EXERCISE_DISPLAY_TITLE");

            Log.d(TAG, "Intent received: Level=" + levelName +
                    ", TopicID=" + topicId + ", TopicDisplay=" + topicDisplayName +
                    ", ExerciseID=" + exerciseId + ", ExerciseDisplay=" + exerciseDisplayTitle);
        } else {
            handleInitializationError("Error: Intent is null.");
            return;
        }

        if (levelName == null || topicId == null || exerciseId == null) {
            handleInitializationError("Error: Missing Level, Topic ID, or Exercise ID in Intent.");
            return;
        }

        if (topicDisplayName == null) Log.w(TAG, "Topic Display Name is null from Intent.");
        if (exerciseDisplayTitle == null) Log.w(TAG, "Exercise Display Title is null from Intent.");


        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null) {
            loadMicrosoftUserData(firebaseUser.getUid());
        } else {
            Log.w(TAG, "No Firebase user logged in. Loading public content only.");
            loadExerciseDataFromFirebase();
            loadAllExercisesInTopicFromFirebase();
            hasLoadedSavedAnswer = true;
            checkIfAllDataLoadedAndReady();
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
                        currentMicrosoftUser = new MicrosoftUser(firebaseUid, email, displayName);
                        Log.d(TAG, "User " + firebaseUid + " not MS Graph linked. Using Firebase UID: " + firebaseUid);
                    }
                } else {
                    Log.w(TAG, "User data node not found for UID: " + firebaseUid);
                    FirebaseUser fbUser = mAuth.getCurrentUser();
                    if (fbUser != null) {
                        currentMicrosoftUser = new MicrosoftUser(firebaseUid, fbUser.getEmail(), fbUser.getDisplayName());
                        Log.d(TAG, "Created shell MicrosoftUser for UID: " + firebaseUid);
                    } else {
                        Log.e(TAG, "Critical: FirebaseUser became null during loadMicrosoftUserData for missing node.");
                    }
                }
                loadExerciseDataFromFirebase();
                loadAllExercisesInTopicFromFirebase();
                if (currentMicrosoftUser != null) {
                    loadSavedUserAnswerFromFirebase();
                } else {
                    hasLoadedSavedAnswer = true;
                    checkIfAllDataLoadedAndReady();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load Microsoft user data.", error.toException());
                loadExerciseDataFromFirebase();
                loadAllExercisesInTopicFromFirebase();
                hasLoadedSavedAnswer = true;
                checkIfAllDataLoadedAndReady();
            }
        });
    }

    public void initialize() {
        Log.d(TAG, "Initializing Controller for: L-" + levelName + ", TopicID-" + topicId + ", ExerciseID-" + exerciseId);
        if (view == null) { Log.e(TAG, "View is null in initialize"); return; }

        currentButtonState = STATE_SUBMIT_WRITING;
        isUserEditingAfterFeedback = false;
        allCriteriaSuccess = false;
        submittedTextForCurrentFeedback = "";
        isFeedbackPanelVisible = false;
        editButtonForcedByLoad = false;

        view.setUIElementsVisibility(false);
        view.setFeedbackPanelVisibility(false);
        view.setFeedbackTriggerVisibility(false);
        view.setAnswerEditTextEnabled(true);
    }

    private void handleInitializationError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (view != null) { view.showFailToast(errorMessage); view.finishActivity(); }
    }

    private void loadExerciseDataFromFirebase() {
        if (levelName == null || topicId == null || exerciseId == null) {
            Log.e(TAG, "Cannot load exercise data: LevelName, TopicID, or ExerciseID is null.");
            if(view != null) { view.showFailToast("Error: Missing data to load exercise."); view.finishActivity(); }
            exerciseDataLoaded = true;
            checkIfAllDataLoadedAndReady();
            return;
        }
        DatabaseReference exerciseRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Writing").child("Topics").child(topicId)
                .child("Exercises").child(exerciseId);

        Log.d(TAG, "Attempting to load exercise data from: " + exerciseRef.toString());
        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "Writing exercise data NOT FOUND at path: " + exerciseRef);
                    if (view != null) {
                        view.displayQuestionPrompt("Content not available for this exercise.");
                        view.displayExerciseTitle(exerciseDisplayTitle != null ? exerciseDisplayTitle : exerciseId);
                    }
                    currentWritingExercise = null;
                } else {
                    Log.d(TAG, "Exercise data FOUND at path: " + exerciseRef);
                    String scriptPrompt = snapshot.child("script").getValue(String.class);
                    String titleFromDb = snapshot.child("title").getValue(String.class);

                    Log.d(TAG, "Fetched script: " + (scriptPrompt != null ? "Present, length " + scriptPrompt.length() : "NULL") +
                            ", Fetched title from DB: " + titleFromDb);

                    String finalDisplayTitle = titleFromDb;
                    if (finalDisplayTitle == null || finalDisplayTitle.trim().isEmpty()) {
                        finalDisplayTitle = exerciseDisplayTitle;
                    }
                    if (finalDisplayTitle == null || finalDisplayTitle.trim().isEmpty()) {
                        finalDisplayTitle = exerciseId;
                    }

                    if (scriptPrompt != null && !scriptPrompt.trim().isEmpty()) {
                        currentWritingExercise = new WritingExercise(exerciseId, finalDisplayTitle, scriptPrompt);
                        if (view != null) {
                            view.displayExerciseTitle(currentWritingExercise.getTitle());
                            view.displayQuestionPrompt(currentWritingExercise.getScript());
                        }
                        Log.d(TAG, "currentWritingExercise populated. Title: " + currentWritingExercise.getTitle() + ", Script loaded.");
                    } else {
                        Log.w(TAG, "Script is null or empty for exerciseId: " + exerciseId + " at path " + exerciseRef);
                        currentWritingExercise = new WritingExercise(exerciseId, finalDisplayTitle, "Writing prompt not available for this exercise.");
                        if (view != null) {
                            view.displayExerciseTitle(finalDisplayTitle);
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
                    view.displayExerciseTitle(exerciseDisplayTitle != null ? exerciseDisplayTitle : exerciseId);
                    view.displayQuestionPrompt("Content loading error.");
                }
                currentWritingExercise = null;
                exerciseDataLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void loadAllExercisesInTopicFromFirebase() {
        if (levelName == null || topicId == null) {
            Log.w(TAG, "Cannot load all exercises in topic: Level or Topic ID is null.");
            allExercisesLoaded = true;
            checkIfAllDataLoadedAndReady();
            return;
        }
        DatabaseReference exercisesNodeRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Writing").child("Topics").child(topicId)
                .child("Exercises");

        Log.d(TAG, "Loading all exercises from: " + exercisesNodeRef.toString());
        exercisesNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                allExercisesInTopic.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot exSnap : snapshot.getChildren()) {
                        String exId = exSnap.getKey();
                        String exDisplayTitleFromDb = exSnap.child("title").getValue(String.class);
                        if (exId != null && exDisplayTitleFromDb != null) {
                            allExercisesInTopic.add(new WritingExercise(exId, exDisplayTitleFromDb, null));
                        } else {
                            Log.w(TAG, "Exercise ID or display title is null for a child under " + exercisesNodeRef + ". Child key: " + exId);
                        }
                    }
                    Log.d(TAG, "Loaded " + allExercisesInTopic.size() + " exercises for topicId: " + topicId);
                } else {
                    Log.d(TAG, "No exercises found for topicId: " + topicId + " at path " + exercisesNodeRef);
                }
                allExercisesLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase loading exercises in topic cancelled/failed: " + error.getMessage());
                allExercisesLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void loadSavedUserAnswerFromFirebase() {
        if (currentMicrosoftUser == null || currentMicrosoftUser.getUserId() == null || currentMicrosoftUser.getUserId().isEmpty()) {
            Log.w(TAG, "Cannot load saved answer: MicrosoftUser or UserId is null/empty.");
            hasLoadedSavedAnswer = true;
            checkIfAllDataLoadedAndReady();
            return;
        }
        if (exerciseId == null) {
            Log.w(TAG, "Cannot load saved answer: exerciseId (key for user progress) is null.");
            hasLoadedSavedAnswer = true;
            checkIfAllDataLoadedAndReady();
            return;
        }

        final String userIdForPath = currentMicrosoftUser.getUserId();
        Log.d(TAG, "Loading saved answer for user: " + userIdForPath + ", exercise_firebase_key: " + exerciseId);
        DatabaseReference answerRef = databaseReference
                .child("Users").child("MicrosoftUsers").child(userIdForPath)
                .child("Progress").child("WritingAnswers").child(exerciseId); // Use actual exerciseId as key

        answerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String savedAnswer = snapshot.child("userAnswer").getValue(String.class);
                    String feedbackSummary = snapshot.child("feedbackSummary").getValue(String.class);
                    Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                    // These fields inside the saved node are for record-keeping/context
                    String recordExerciseId = snapshot.child("exerciseId").getValue(String.class); // Should match this.exerciseId
                    String recordTopicTitle = snapshot.child("topicTitle").getValue(String.class); // Should be topicDisplayName

                    currentMicrosoftUser.setWritingExerciseId(exerciseId); // Store the actual exerciseId
                    currentMicrosoftUser.setWritingLevelName(levelName);
                    currentMicrosoftUser.setWritingTopicTitle(topicDisplayName != null ? topicDisplayName : topicId);
                    currentMicrosoftUser.setWritingUserAnswer(savedAnswer != null ? savedAnswer : "");
                    currentMicrosoftUser.setWritingFeedbackSummary(feedbackSummary != null ? feedbackSummary : "");
                    currentMicrosoftUser.setWritingTimestamp(timestamp != null ? timestamp : 0L);

                    if (savedAnswer != null && !savedAnswer.trim().isEmpty()) {
                        if (view != null) view.displaySavedAnswer(savedAnswer);
                        Log.d(TAG, "Loaded saved answer for user " + userIdForPath + ", exercise_key " + exerciseId);
                    } else {
                        Log.d(TAG, "No saved answer TEXT found for user " + userIdForPath + ", exercise_key " + exerciseId);
                    }
                } else {
                    Log.d(TAG, "No saved answer NODE found for user " + userIdForPath + ", exercise_key " + exerciseId);
                }
                hasLoadedSavedAnswer = true;
                checkIfAllDataLoadedAndReady();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load saved user answer for exercise_key " + exerciseId, error.toException());
                hasLoadedSavedAnswer = true;
                checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void checkIfAllDataLoadedAndReady() {
        boolean userRelatedDataProcessed = (mAuth.getCurrentUser() != null && currentMicrosoftUser != null && hasLoadedSavedAnswer) ||
                (mAuth.getCurrentUser() == null && hasLoadedSavedAnswer);

        Log.d(TAG, "checkIfAllDataLoadedAndReady: exerciseDataLoaded=" + exerciseDataLoaded +
                ", allExercisesLoaded=" + allExercisesLoaded + ", userRelatedDataProcessed=" + userRelatedDataProcessed);

        if (exerciseDataLoaded && allExercisesLoaded && userRelatedDataProcessed) {
            Log.i(TAG, "All initial data dependencies met. Proceeding with UI setup.");
            if (view == null) { Log.e(TAG, "View is null in checkIfAllDataLoadedAndReady after data load."); return; }

            view.setUIElementsVisibility(true);
            view.setAnswerInputVisibility(true);

            boolean isContentEffectivelyUnavailable = currentWritingExercise == null ||
                    currentWritingExercise.getScript() == null ||
                    currentWritingExercise.getScript().trim().isEmpty() ||
                    currentWritingExercise.getScript().toLowerCase().contains("not available") ||
                    currentWritingExercise.getScript().toLowerCase().contains("not found") ||
                    currentWritingExercise.getScript().equals("Content loading error.");

            if (isContentEffectivelyUnavailable) {
                Log.w(TAG, "Content is effectively unavailable. Script: " + (currentWritingExercise != null ? currentWritingExercise.getScript() : "exercise is null"));
                currentButtonState = STATE_SUBMIT_WRITING;
                if (view != null) view.displayQuestionPrompt("Content not available for this exercise.");
            } else if (hasLoadedSavedAnswer && currentMicrosoftUser != null && !TextUtils.isEmpty(currentMicrosoftUser.getWritingUserAnswer())) {
                Log.d(TAG, "Content available AND saved answer loaded. Setting state to RETRY_WRITING.");
                currentButtonState = STATE_RETRY_WRITING;
                submittedTextForCurrentFeedback = currentMicrosoftUser.getWritingUserAnswer();
                allCriteriaSuccess = "All criteria success".equalsIgnoreCase(currentMicrosoftUser.getWritingFeedbackSummary());
                editButtonForcedByLoad = true;
            } else {
                Log.d(TAG, "Content available, NO saved answer. Setting state to SUBMIT_WRITING.");
                currentButtonState = STATE_SUBMIT_WRITING;
                isUserEditingAfterFeedback = false;
                allCriteriaSuccess = false;
                editButtonForcedByLoad = false;
                if(currentMicrosoftUser != null) {
                    currentMicrosoftUser.setWritingUserAnswer("");
                    currentMicrosoftUser.setWritingFeedbackSummary("");
                    currentMicrosoftUser.setWritingTimestamp(0L);
                    // For the MicrosoftUser object's internal record for this session:
                    currentMicrosoftUser.setWritingExerciseId(this.exerciseId); // Store actual ID
                    currentMicrosoftUser.setWritingLevelName(levelName);
                    currentMicrosoftUser.setWritingTopicTitle(topicDisplayName != null ? topicDisplayName : topicId); // Store display name or topic ID
                }
            }
            updateSubmitButtonBasedOnState();
        } else {
            Log.d(TAG, "Still waiting for some initial data to load.");
        }
    }

    private void updateSubmitButtonBasedOnState() {
        if (view == null) return;
        Log.d(TAG, "updateSubmitButtonBasedOnState - CurrentState: " + currentButtonState +
                ", editButtonForcedByLoad: " + editButtonForcedByLoad + ", allSuccess: " + allCriteriaSuccess);

        boolean contentNotAvailable = currentWritingExercise == null || currentWritingExercise.getScript() == null ||
                currentWritingExercise.getScript().trim().isEmpty() ||
                currentWritingExercise.getScript().toLowerCase().contains("not available") ||
                currentWritingExercise.getScript().toLowerCase().contains("not found") ||
                currentWritingExercise.getScript().equals("Content loading error.");

        if (contentNotAvailable) {
            view.setSubmitButtonState("N/A", false);
            view.setFeedbackPanelVisibility(false);
            view.setFeedbackTriggerVisibility(false);
            view.setAnswerEditTextEnabled(false);
            if (view != null && (currentWritingExercise == null || currentWritingExercise.getScript() == null || currentWritingExercise.getScript().trim().isEmpty())) {
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
                view.setSubmitButtonState("Edit", true);
                view.setFeedbackPanelVisibility(false);
                view.setFeedbackTriggerVisibility(false);
                isFeedbackPanelVisible = false;
            } else {
                view.setSubmitButtonState(allCriteriaSuccess ? "Done" : "Edit", true);
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
        if (view == null) {
            Log.e(TAG, "onSubmitButtonClicked: View is null!");
            return;
        }
        Log.d(TAG, "Submit button clicked. State: " + currentButtonState +
                ", AllSuccess: " + allCriteriaSuccess +
                ", EditingAfterFeedback: " + isUserEditingAfterFeedback +
                ", editButtonForcedByLoad: " + editButtonForcedByLoad);

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

                if (currentWritingExercise == null || currentWritingExercise.getScript() == null ||
                        currentWritingExercise.getScript().trim().isEmpty() ||
                        currentWritingExercise.getScript().toLowerCase().contains("not available") ||
                        currentWritingExercise.getScript().toLowerCase().contains("not found") ||
                        currentWritingExercise.getScript().equals("Content loading error.")) {
                    Log.e(TAG, "onSubmitButtonClicked: Cannot submit. Invalid/empty script. Script: " +
                            (currentWritingExercise != null ? currentWritingExercise.getScript() : "currentWritingExercise is null"));
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

                    if(editButtonForcedByLoad && currentMicrosoftUser != null && currentMicrosoftUser.getWritingUserAnswer() != null) {
                        submittedTextForCurrentFeedback = currentMicrosoftUser.getWritingUserAnswer().trim();
                    }

                    editButtonForcedByLoad = false;

                    updateSubmitButtonBasedOnState();
                    String currentAnswerInView = view.getCurrentAnswerText();
                    onAnswerTextChanged(currentAnswerInView != null ? currentAnswerInView : userAnswerFromView);
                    view.requestFocusOnAnswerInput();
                }
                break;
            default:
                Log.w(TAG, "Unknown button state clicked: " + currentButtonState + ". Resetting state.");
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
        final String textBeingSubmitted = userAnswer.trim();

        String userIdToUseForSaving = (currentMicrosoftUser != null && currentMicrosoftUser.getUserId() != null) ? currentMicrosoftUser.getUserId() : null;
        if (userIdToUseForSaving == null && mAuth.getCurrentUser() != null) {
            userIdToUseForSaving = mAuth.getCurrentUser().getUid();
            Log.w(TAG, "Falling back to FirebaseAuth UID for saving: " + userIdToUseForSaving);
        }

        // For saving user progress, use exerciseId (Firebase key)
        if (userIdToUseForSaving != null && this.exerciseId != null && (this.topicDisplayName != null || this.topicId != null) ) {
            if (currentMicrosoftUser == null && mAuth.getCurrentUser() != null) {
                FirebaseUser fbUser = mAuth.getCurrentUser();
                currentMicrosoftUser = new MicrosoftUser(fbUser.getUid(), fbUser.getEmail(), fbUser.getDisplayName());
                Log.d(TAG, "Created shell MicrosoftUser for saving progress.");
            }
            if (currentMicrosoftUser != null) {
                currentMicrosoftUser.setWritingExerciseId(this.exerciseId); // Use actual exerciseId for the record's key
                currentMicrosoftUser.setWritingLevelName(levelName);
                currentMicrosoftUser.setWritingTopicTitle(this.topicDisplayName != null ? this.topicDisplayName : this.topicId); // Context for User's Progress
                currentMicrosoftUser.setWritingUserAnswer(textBeingSubmitted);
                currentMicrosoftUser.setWritingTimestamp(System.currentTimeMillis());
                currentMicrosoftUser.setWritingFeedbackSummary("Feedback pending...");
                saveUserWritingAnswerToFirebase(userIdToUseForSaving, currentMicrosoftUser);
            } else {
                Log.w(TAG, "currentMicrosoftUser is null, cannot save user-specific progress.");
            }
        } else {
            Log.w(TAG, "Cannot save user answer: User ID, exerciseId, or topic context is missing.");
        }

        final String finalUserIdForFeedbackUpdate = userIdToUseForSaving;
        final String submittedUserAnswer = userAnswer;
        getFeedbackFromOpenAI(originalPrompt, textBeingSubmitted, new FeedbackCallback() {
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
                        int totalScore = feedbackJson.getInt("totalScore");

                        int taskScore = taskResponse.getInt("score");
                        int coherenceScore = coherenceCohesion.getInt("score");
                        int grammarScore = grammarVocabulary.getInt("score");
                        int lengthScore = length.getInt("score");

                        allCriteriaSuccess = taskResponse.getInt("iconType") == 1 &&
                                coherenceCohesion.getInt("iconType") == 1 &&
                                grammarVocabulary.getInt("iconType") == 1 &&
                                length.getInt("iconType") == 1;

                        if (currentMicrosoftUser != null) {
                            currentMicrosoftUser.setWritingFeedbackSummary(allCriteriaSuccess ? "All criteria success" : "Needs improvement");
                            // Nếu muốn lưu tổng điểm
                            // currentMicrosoftUser.setWritingTotalScore(totalScore);
                        }

                        view.displayStructuredAIFeedback(
                                taskResponse.getString("feedback"), taskResponse.getInt("iconType"), taskScore,
                                coherenceCohesion.getString("feedback"), coherenceCohesion.getInt("iconType"), coherenceScore,
                                grammarVocabulary.getString("feedback"), grammarVocabulary.getInt("iconType"), grammarScore,
                                length.getString("feedback"), length.getInt("iconType"), lengthScore,
                                totalScore
                        );

                        currentButtonState = STATE_RETRY_WRITING;
                        isUserEditingAfterFeedback = false;
                        submittedTextForCurrentFeedback = submittedUserAnswer;
                        isFeedbackPanelVisible = true;
                        editButtonForcedByLoad = false;
                        updateSubmitButtonBasedOnState();
                        if (isFeedbackPanelVisible) view.focusOnFeedbackPanel();
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
            Log.w(TAG, "Cannot save user writing answer: userId, userWithWritingData, or getWritingExerciseId() (actual exerciseId) is null.");
            return;
        }
        String exerciseKeyForUserProgress = userWithWritingData.getWritingExerciseId(); // This is the actual exerciseId

        DatabaseReference userAnswersRef = databaseReference
                .child("Users").child("MicrosoftUsers").child(userId)
                .child("Progress").child("WritingAnswers").child(exerciseKeyForUserProgress); // Keyed by actual exerciseId
        userWithWritingData.setWritingTimestamp(System.currentTimeMillis());
        userAnswersRef.setValue(userWithWritingData.toMapForWritingAnswer())
                .addOnSuccessListener(aVoid -> Log.i(TAG, "User writing answer saved successfully for user: " + userId + ", exerciseId_key: " + exerciseKeyForUserProgress))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save user writing answer for user: " + userId + ", exerciseId_key: " + exerciseKeyForUserProgress, e));
    }

    private void updateUserWritingAnswerFeedback(String userId, String exerciseIdKey, String feedbackSummary) {
        // exerciseIdKey is the actual Firebase key of the exercise
        if (userId == null || exerciseIdKey == null) {
            Log.w(TAG, "Cannot update feedback summary: userId or exerciseIdKey is null.");
            return;
        }
        DatabaseReference userAnswerRef = databaseReference
                .child("Users").child("MicrosoftUsers").child(userId)
                .child("Progress").child("WritingAnswers").child(exerciseIdKey); // Keyed by actual exerciseId

        Map<String, Object> updates = new HashMap<>();
        updates.put("feedbackSummary", feedbackSummary);
        updates.put("timestamp", System.currentTimeMillis());

        userAnswerRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.i(TAG, "Feedback summary updated successfully for user: " + userId + ", exerciseId_key: " + exerciseIdKey))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update feedback summary for user: " + userId + ", exerciseId_key: " + exerciseIdKey, e));
    }

    interface FeedbackCallback {
        void onSuccess(JSONObject feedbackJson);
        void onError(String error);
    }

    private void getFeedbackFromOpenAI(String originalPrompt, String userAnswer, FeedbackCallback callback) {
        String promptForGemini =
                "You are an English writing evaluator. Assess the user's writing in 4 rubric categories: taskResponse, coherenceCohesion, grammarVocabulary, length. " +
                        "For each, provide:" +
                        "\\n- feedback (short, practical comment)" +
                        "\\n- iconType: 1 if meets requirements, 0 if not" +
                        "\\n- score: integer from 0 (poor) to 10 (excellent)" +
                        "\\nAt the end, provide totalScore (average of 4 scores, round to nearest integer)." +
                        "\\nFormat output as JSON:" +
                        "\\n{" +
                        "\\n  \"taskResponse\": {\"feedback\": \"...\", \"iconType\": <0_or_1>, \"score\": <0-10>}," +
                        "\\n  \"coherenceCohesion\": {\"feedback\": \"...\", \"iconType\": <0_or_1>, \"score\": <0-10>}," +
                        "\\n  \"grammarVocabulary\": {\"feedback\": \"...\", \"iconType\": <0_or_1>, \"score\": <0-10>}," +
                        "\\n  \"length\": {\"feedback\": \"...\", \"iconType\": <0_or_1>, \"score\": <0-10>}," +
                        "\\n  \"totalScore\": <0-10>" +
                        "\\n}" +
                        "\\nOnly return JSON. Prompt: \"" + originalPrompt + "\"\\nUser's Response: \"" + userAnswer + "\"";
        callOpenAIAPI(promptForGemini, responseString -> {
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

    private void callOpenAIAPI(String promptText, GeminiApiSuccessListener successListener, GeminiApiErrorListener errorListener) {
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient();
            JSONArray messagesArr = new JSONArray();
            JSONObject userMsg = new JSONObject();
            try {
                userMsg.put("role", "user");
                userMsg.put("content", promptText);
                messagesArr.put(userMsg);
            } catch (JSONException e) {
                mainThreadHandler.post(() -> errorListener.onError("Error building OpenAI request: " + e.getMessage()));
                return;
            }

            JSONObject requestBody = new JSONObject();
            try {
                requestBody.put("model", "gpt-4.1");
                requestBody.put("messages", messagesArr);
                requestBody.put("response_format", new JSONObject().put("type", "json_object"));
            } catch (JSONException e) {
                mainThreadHandler.post(() -> errorListener.onError("Error building OpenAI request: " + e.getMessage()));
                return;
            }

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"), requestBody.toString());

            Request request = new Request.Builder()
                    .url(OPENAI_API_URL)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    mainThreadHandler.post(() -> errorListener.onError("OpenAI API connection error: " + e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseBodyString = response.body() != null ? response.body().string() : null;
                    if (response.body() != null) response.body().close();

                    if (response.isSuccessful() && responseBodyString != null) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBodyString);
                            JSONArray choices = jsonResponse.getJSONArray("choices");
                            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                            String aiReply = message.getString("content").trim();
                            String resultText = aiReply;
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
                            String finalText = resultText.trim();
                            mainThreadHandler.post(() -> {
                                try {
                                    successListener.onResult(finalText);
                                } catch (JSONException e) {
                                    errorListener.onError("Error processing OpenAI response: " + e.getMessage());
                                }
                            });
                        } catch (JSONException e) {
                            mainThreadHandler.post(() -> errorListener.onError("Error parsing OpenAI JSON response: " + e.getMessage()));
                        }
                    } else {
                        mainThreadHandler.post(() -> errorListener.onError("OpenAI API error: " + response.code() + ". " + responseBodyString));
                    }
                }
            });
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
        boolean userRelatedDataProcessed = (mAuth.getCurrentUser() != null && currentMicrosoftUser != null && hasLoadedSavedAnswer) ||
                (mAuth.getCurrentUser() == null && hasLoadedSavedAnswer);

        if (exerciseDataLoaded && allExercisesLoaded && userRelatedDataProcessed && view != null) {
            updateSubmitButtonBasedOnState();
            if (currentButtonState == STATE_SUBMIT_WRITING) {
                String currentText = view.getCurrentAnswerText();
                onAnswerTextChanged(currentText != null ? currentText : "");
            }
        } else {
            Log.d(TAG, "onResume: Core data not yet fully processed. UI update will be handled by loading callbacks or initialize.");
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
        isFeedbackPanelVisible = !isFeedbackPanelVisible;
        Log.d(TAG, "Review Feedback clicked. Feedback panel visible: " + isFeedbackPanelVisible);
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

package com.example.langhexx.Controller;

import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import com.example.langhexx.Model.Exercise;
import com.example.langhexx.Model.ReadingQuestion;
import com.example.langhexx.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReadingController {

    private static final String TAG = "ReadingController";

    public interface ViewInterface {
        void displayExerciseTitle(String title);
        void displayPassage(String text);
        void updateAdapterData(List<ReadingQuestion> newQuestions);
        void showResultsInAdapter(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctnessMap);
        void resetAdapterState();
        void setButtonState(int state, String text);
        Map<Integer, Integer> getAdapterSelectedAnswers();
        boolean areAdapterAnswersAllCorrect();
        void showToast(String message);
        void showFailToast(String message);
        void showConfirmationDialog(String title, String message, Runnable onConfirm);
        void navigateToNextExercise(String levelName, String topicId, String nextExerciseId, String topicDisplayTitle, String nextExerciseDisplayTitle);
        void finishActivity();
        void scrollToQuestion(int index);
        void setUIElementsVisibility(boolean visible);
        // Added for progress saving interaction
        void setAdapterAnswerListener();
        void applySavedAnswersToAdapter(Map<Integer, Integer> savedAnswers); // Might not be used if applying via model
    }

    public static final int STATE_SUBMIT = 0;
    public static final int STATE_RETRY = 1;
    public static final int STATE_NEXT = 2;
    public static final int STATE_FINISHED_NO_NEXT = -1;
    private int currentButtonState = STATE_SUBMIT;

    private ViewInterface view;
    private String levelName;
    private String topicId;
    private String exerciseId;
    private String topicDisplayTitle;
    private String exerciseDisplayTitle;
    private String currentUserId; // Added for user identification

    private String passageText;
    private List<ReadingQuestion> questionsList;
    private ArrayList<Exercise> allExercisesInTopic;
    private Map<Integer, Integer> savedUserAnswers = new HashMap<>(); // Added for saved answers

    private boolean dataLoaded = false;
    private boolean allExercisesLoaded = false;
    private boolean savedAnswersLoaded = false; // Added flag for saved answers

    private DatabaseReference databaseReference;

    public ReadingController(ViewInterface view, Intent intent) {
        this.view = view;
        this.questionsList = new ArrayList<>();
        this.allExercisesInTopic = new ArrayList<>();
        this.currentUserId = getCurrentUserId(); // Get user ID
        this.databaseReference = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        if (intent != null) {
            levelName = intent.getStringExtra("LEVEL_NAME");
            topicId = intent.getStringExtra("TOPIC_ID");
            exerciseId = intent.getStringExtra("EXERCISE_ID");
            topicDisplayTitle = intent.getStringExtra("TOPIC_TITLE");
            exerciseDisplayTitle = intent.getStringExtra("EXERCISE_TITLE");

            Log.d(TAG, "Intent data: Level=" + levelName + ", TopicID=" + topicId + ", ExerciseID=" + exerciseId +
                    ", TopicTitle=" + topicDisplayTitle + ", ExerciseTitle=" + exerciseDisplayTitle);
        } else {
            handleInitializationError("Error: Intent is null.");
            return;
        }

        if (currentUserId == null) {
            handleInitializationError("Error: Could not get user ID.");
            return;
        }

        if (levelName == null || topicId == null || exerciseId == null || topicDisplayTitle == null || exerciseDisplayTitle == null) {
            handleInitializationError("Error: Missing identifiers in Intent.");
            return;
        }
    }

    // !!! REPLACE WITH YOUR ACTUAL USER ID RETRIEVAL METHOD !!!
    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Log.d(TAG, "Current User ID: " + user.getUid());
            return user.getUid();
        } else {
            Log.e(TAG, "getCurrentUserId: User is not logged in!");
            return null;
        }
    }

    public void initialize() {
        Log.d(TAG, "Initializing Controller...");
        if (view == null || currentUserId == null) {
            Log.e(TAG, "Initialization failed: View or UserID is null.");
            handleInitializationError("Initialization Error.");
            return;
        }
        view.setUIElementsVisibility(false);
        view.displayExerciseTitle(exerciseDisplayTitle);
        loadExerciseDataFromFirebase();
        loadAllExercisesInTopicFromFirebase();
        loadSavedAnswersFromFirebase(); // Load saved answers
        view.setAdapterAnswerListener(); // Tell view to set adapter listener
    }

    private void handleInitializationError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (view != null) {
            view.showToast(errorMessage);
            view.finishActivity();
        }
    }

    private void loadExerciseDataFromFirebase() {
        if (levelName == null || topicId == null || exerciseId == null) {
            Log.e(TAG, "Cannot load exercise data: Identifiers are null.");
            dataLoaded = true; // Mark as loaded (even if failed) to allow checkIfAllDataLoaded to proceed
            checkIfAllDataLoaded();
            return;
        }
        DatabaseReference exerciseRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Reading").child("Topics").child(topicId)
                .child("Exercises").child(exerciseId);

        Log.i(TAG, "Loading exercise data from: " + exerciseRef.toString());
        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "Exercise data not found at path.");
                    view.showToast("Exercise data not found.");
                    dataLoaded = true; // Mark as loaded
                    checkIfAllDataLoaded(); // Check completion even if data is missing
                    return;
                }

                passageText = snapshot.child("script").getValue(String.class);
                if (passageText != null && !passageText.isEmpty()) {
                    view.displayPassage(passageText);
                    Log.d(TAG, "Passage loaded.");
                } else {
                    Log.w(TAG, "'script' field missing or empty.");
                    view.displayPassage(null);
                }

                DataSnapshot questionsSnapshot = snapshot.child("questions");
                List<ReadingQuestion> loadedQuestions = new ArrayList<>();
                if (questionsSnapshot.exists()) {
                    for (DataSnapshot questionSnap : questionsSnapshot.getChildren()) {
                        try {
                            ReadingQuestion question = parseReadingQuestionSnapshot(questionSnap);
                            if (question != null) loadedQuestions.add(question);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing question: " + questionSnap.getKey(), e);
                        }
                    }
                    Log.i(TAG, "Loaded " + loadedQuestions.size() + " questions.");
                } else {
                    Log.w(TAG, "No 'questions' node found.");
                }
                questionsList.clear();
                questionsList.addAll(loadedQuestions);
                // Don't update adapter yet, wait for saved answers

                dataLoaded = true;
                checkIfAllDataLoaded();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase loading cancelled/failed: " + error.getMessage(), error.toException());
                view.showToast("Error loading data: " + error.getMessage());
                dataLoaded = true; // Mark as loaded
                checkIfAllDataLoaded(); // Allow check completion
            }
        });
    }

    private void loadSavedAnswersFromFirebase() {
        if (view == null || currentUserId == null || exerciseId == null) {
            Log.e(TAG, "Cannot load saved answers: View, UserID or ExerciseID is null.");
            savedAnswersLoaded = true;
            checkIfAllDataLoaded();
            return;
        }
        DatabaseReference savedAnswersRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers") // Assuming same structure
                .child(currentUserId)
                .child("Progress")
                .child("ReadingAnswers") // Change to ReadingAnswers
                .child(exerciseId);

        Log.i(TAG, "Loading saved reading answers from: " + savedAnswersRef.toString());

        savedAnswersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                savedUserAnswers.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot answerSnap : snapshot.getChildren()) {
                        try {
                            String questionIndexStr = answerSnap.getKey();
                            Object value = answerSnap.getValue();
                            if (questionIndexStr != null && value instanceof Long) {
                                int questionIndex = Integer.parseInt(questionIndexStr);
                                int selectedOptionId = ((Long) value).intValue();
                                if (selectedOptionId != -1) {
                                    savedUserAnswers.put(questionIndex, selectedOptionId);
                                }
                            }
                        } catch (NumberFormatException | ClassCastException e) {
                            Log.w(TAG, "Error parsing saved reading answer snapshot: " + answerSnap.getKey(), e);
                        }
                    }
                    Log.i(TAG, "Loaded " + savedUserAnswers.size() + " saved reading answers.");
                } else {
                    Log.i(TAG, "No saved reading answers found for this exercise.");
                }
                savedAnswersLoaded = true;
                checkIfAllDataLoaded();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load saved reading answers: " + error.getMessage());
                savedAnswersLoaded = true;
                checkIfAllDataLoaded();
            }
        });
    }


    private ReadingQuestion parseReadingQuestionSnapshot(DataSnapshot questionSnap) {
        ReadingQuestion question = new ReadingQuestion();
        // Keep existing parsing logic, but ensure ReadingQuestion model is used
        String questionIdFromDB = questionSnap.child("id").getValue(String.class);
        question.setId(questionIdFromDB != null ? questionIdFromDB : questionSnap.getKey());
        String text = questionSnap.child("questionText").getValue(String.class);
        String answer = questionSnap.child("correctAnswer").getValue(String.class);
        Map<String, String> stringOptionsMap = new HashMap<>();
        DataSnapshot optionsSnapshot = questionSnap.child("options");
        if (optionsSnapshot.exists() && optionsSnapshot.getValue() instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> optionsMapObject = (Map<String, Object>) optionsSnapshot.getValue();
                if (optionsMapObject != null) {
                    for (Map.Entry<String, Object> entry : optionsMapObject.entrySet()) {
                        stringOptionsMap.put(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
            } catch (ClassCastException e) {
                Log.e(TAG, "Error casting options for question: " + questionSnap.getKey(), e);
                return null;
            }
        } else {
            Log.w(TAG, "'options' node missing or invalid format for question: " + questionSnap.getKey());
        }

        if (text != null && !text.isEmpty() && answer != null && !answer.isEmpty() && !stringOptionsMap.isEmpty()) {
            question.setQuestionText(text);
            question.setOptions(stringOptionsMap);
            question.setCorrectAnswer(answer);
            return question;
        } else {
            Log.w(TAG, "Skipping question due to missing essential data: Key=" + questionSnap.getKey());
            return null;
        }
    }

    private void loadAllExercisesInTopicFromFirebase() {
        if (levelName == null || topicId == null) {
            Log.e(TAG, "Cannot load all exercises: LevelName or TopicID null.");
            allExercisesLoaded = true; // Mark as loaded
            checkIfAllDataLoaded();
            return;
        }
        DatabaseReference exercisesPathRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Reading").child("Topics").child(topicId)
                .child("Exercises");

        Log.d(TAG, "Loading all exercises from: " + exercisesPathRef.toString());
        exercisesPathRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                allExercisesInTopic.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot exSnap : snapshot.getChildren()) {
                        String exId = exSnap.getKey();
                        String exTitle = exSnap.child("title").getValue(String.class);

                        if (exTitle == null || exTitle.isEmpty()) {
                            exTitle = exId;
                            Log.w(TAG, "Exercise ID " + exId + " in topic " + topicId + " is missing a title. Using ID as title.");
                        }
                        if (exId != null) {
                            allExercisesInTopic.add(new Exercise(exId, exTitle));
                        }
                    }
                    Log.i(TAG, "Loaded " + allExercisesInTopic.size() + " exercises for topic ID: " + topicId);
                } else {
                    Log.w(TAG, "No exercises found under topic ID: " + topicId);
                }
                allExercisesLoaded = true;
                checkIfAllDataLoaded();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load all exercise identifiers: " + error.getMessage());
                allExercisesLoaded = true;
                checkIfAllDataLoaded();
            }
        });
    }

    private void checkIfAllDataLoaded() {
        // Wait for exercise data, all exercise list, AND saved answers
        if (dataLoaded && allExercisesLoaded && savedAnswersLoaded && view != null) {
            Log.d(TAG, "All initial data loaded (exercise, titles, saved answers) for Reading.");

            // Apply saved answers to the model list BEFORE updating the adapter
            if (!questionsList.isEmpty() && !savedUserAnswers.isEmpty()) {
                Log.d(TAG, "Applying " + savedUserAnswers.size() + " saved answers to reading question list...");
                for (int i = 0; i < questionsList.size(); i++) {
                    ReadingQuestion q = questionsList.get(i);
                    if (savedUserAnswers.containsKey(i)) {
                        int savedOptionId = savedUserAnswers.get(i);
                        q.setInitialSelectedOptionId(savedOptionId);
                        Log.v(TAG, "Applying saved reading answer for Q" + i + ": OptionID=" + savedOptionId);
                    } else {
                        q.setInitialSelectedOptionId(-1); // Ensure reset if not saved
                    }
                }
            } else if (!questionsList.isEmpty()) {
                for (ReadingQuestion q : questionsList) { // Ensure reset if no answers saved at all
                    q.setInitialSelectedOptionId(-1);
                }
            }

            // Now update the adapter with the prepared questions list
            view.updateAdapterData(new ArrayList<>(questionsList)); // Pass copy

            boolean hasContentToShow = (passageText != null && !passageText.isEmpty()) || !questionsList.isEmpty();
            view.setUIElementsVisibility(hasContentToShow);

            // Reset button state logic (same as before, but after applying saved answers)
            if (questionsList.isEmpty()) {
                if (passageText == null || passageText.isEmpty()) {
                    view.showToast("No content available for this exercise.");
                } else {
                    view.showToast("No questions for this passage. Click Next if available.");
                }
                if (hasNextExercise()) {
                    setButtonState(STATE_NEXT, "Next Exercise");
                } else {
                    setButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
                }
            } else {
                resetSubmitButtonToSubmitState(); // Default to Submit if there are questions
            }
        } else {
            Log.d(TAG, "Still waiting for data: dataLoaded=" + dataLoaded
                    + ", allExercisesLoaded=" + allExercisesLoaded
                    + ", savedAnswersLoaded=" + savedAnswersLoaded);
        }
    }

    public void saveAnswerSelection(int questionIndex, int selectedOptionId) {
        if (currentUserId == null || exerciseId == null || questionIndex < 0) {
            Log.w(TAG, "Cannot save reading answer: Missing userId, exerciseId, or invalid index.");
            return;
        }
        if (view == null){
            Log.w(TAG, "Cannot save reading answer: View is null.");
            return;
        }

        DatabaseReference answerRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers")
                .child(currentUserId)
                .child("Progress")
                .child("ReadingAnswers") // Use ReadingAnswers path
                .child(exerciseId)
                .child(String.valueOf(questionIndex));

        Log.d(TAG, "Saving reading answer for Q" + questionIndex + " -> OptionID: " + selectedOptionId + " to path: " + answerRef.toString());

        answerRef.setValue(selectedOptionId)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Reading answer for Q" + questionIndex + " saved successfully."))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save reading answer for Q" + questionIndex, e));

        // Optionally save timestamp
        DatabaseReference timestampRef = answerRef.getParent().child("lastUpdated");
        timestampRef.setValue(ServerValue.TIMESTAMP);
    }


    public void onSubmitButtonClicked() {
        Log.d(TAG, "Submit button clicked. Current state: " + currentButtonState);

        if (questionsList.isEmpty()) {
            Log.w(TAG, "Submit button clicked, but no questions are loaded.");
            if (hasNextExercise()) {
                goToNextExercise();
            } else {
                view.showToast("No questions to submit and no next exercise.");
                finishExercise();
            }
            return;
        }

        switch (currentButtonState) {
            case STATE_SUBMIT:
                checkAnswersAndShowConfirmationIfNeeded();
                break;
            case STATE_RETRY:
                retryExercise();
                break;
            case STATE_NEXT:
                goToNextExercise();
                break;
            case STATE_FINISHED_NO_NEXT:
                Log.i(TAG,"'Finish ! Back Now' button clicked. Finishing activity.");
                finishExercise();
                break;
            default:
                Log.w(TAG, "Unknown button state clicked: " + currentButtonState);
                break;
        }
    }

    private void checkAnswersAndShowConfirmationIfNeeded() {
        Log.d(TAG, "Checking answers...");
        Map<Integer, Integer> userAnswers = view.getAdapterSelectedAnswers();
        int totalQuestions = questionsList.size();
        boolean allAnswered = true;
        int firstUnanswered = -1;

        if (totalQuestions == 0) {
            Log.w(TAG, "checkAnswers: No questions available.");
            updateButtonStateBasedOnResults(true);
            return;
        }

        for (int i = 0; i < totalQuestions; i++) {
            if (userAnswers.getOrDefault(i, -1) == -1) {
                allAnswered = false;
                firstUnanswered = i;
                break;
            }
        }

        if (allAnswered) {
            Log.d(TAG, "Check: All questions answered. Showing confirmation dialog.");
            view.showConfirmationDialog("Confirm", "Are you sure want to submit ?", this::proceedWithSubmission);
        } else {
            Log.d(TAG, "Check: Not all questions answered. First unanswered: " + firstUnanswered);
            view.showFailToast("Please answer all questions!");
            if (firstUnanswered != -1) {
                view.scrollToQuestion(firstUnanswered);
            }
        }
    }

    private void proceedWithSubmission() {
        Log.i(TAG, "Proceeding with submission...");
        Map<Integer, Integer> userAnswers = view.getAdapterSelectedAnswers();
        int totalQuestions = questionsList.size();

        if (totalQuestions == 0) {
            Log.w(TAG, "Proceeding with submission, but no questions exist.");
            updateButtonStateBasedOnResults(true);
            return;
        }

        Log.d(TAG, "Scoring " + totalQuestions + " questions.");
        int correctCount = 0;
        Map<Integer, Boolean> correctnessMap = new HashMap<>();

        for (int i = 0; i < totalQuestions; i++) {
            if (i >= questionsList.size()) {
                Log.w(TAG,"Index out of bounds during submission: " + i);
                continue;
            }
            ReadingQuestion question = questionsList.get(i);
            int selectedRadioButtonId = userAnswers.getOrDefault(i, -1);
            String selectedAnswerKey = mapRadioButtonIdToKey(selectedRadioButtonId);

            boolean isCorrect = question.getCorrectAnswer() != null &&
                    !selectedAnswerKey.isEmpty() &&
                    selectedAnswerKey.equalsIgnoreCase(question.getCorrectAnswer());

            if (isCorrect) {
                correctCount++;
            }
            correctnessMap.put(i, isCorrect);
            Log.v(TAG, "Q" + (i + 1) + ": SelectedID=" + selectedRadioButtonId + " (Key='" + selectedAnswerKey + "'), CorrectKey='" + question.getCorrectAnswer() + "', Result=" + isCorrect);
        }

        String resultMessage = String.format(Locale.getDefault(), "Result: %d / %d correct!", correctCount, totalQuestions);
        view.showToast(resultMessage);
        Log.i(TAG, "Final Score: " + correctCount + "/" + totalQuestions);
        view.showResultsInAdapter(userAnswers, correctnessMap);
        boolean allCorrect = (correctCount == totalQuestions);
        updateButtonStateBasedOnResults(allCorrect);
        Log.d(TAG, "Submission process finished. Button state: " + currentButtonState);
    }

    private String mapRadioButtonIdToKey(int selectedRadioButtonId) {
        if (selectedRadioButtonId == R.id.rbOptionA) return "A";
        if (selectedRadioButtonId == R.id.rbOptionB) return "B";
        if (selectedRadioButtonId == R.id.rbOptionC) return "C";
        if (selectedRadioButtonId == R.id.rbOptionD) return "D";
        return "";
    }

    private void updateButtonStateBasedOnResults(boolean allCorrect) {
        boolean hasNext = hasNextExercise();
        Log.d(TAG,"updateButtonStateBasedOnResults: AllCorrect=" + allCorrect + ", HasNext=" + hasNext);

        if (allCorrect) {
            if (hasNext) {
                Log.i(TAG,"All correct. Has next exercise. Setting state to NEXT.");
                setButtonState(STATE_NEXT, "Next Exercise");
            } else {
                Log.i(TAG,"All correct. Last exercise. Setting state to FINISHED_NO_NEXT.");
                setButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
            }
        } else {
            Log.i(TAG,"Some answers incorrect. Setting state to RETRY.");
            setButtonState(STATE_RETRY, "Retry");
        }
    }

    private void retryExercise() {
        Log.i(TAG, "Retry button clicked. Resetting state.");
        if (view == null) return;
        // Reset adapter UI state (should reload initial selections)
        view.resetAdapterState();
        // Reset button back to Submit
        resetSubmitButtonToSubmitState();
        // Scroll to top
        view.scrollToQuestion(0);
        Log.d(TAG,"Exercise state reset for retry. Button state: SUBMIT");
    }

    private void goToNextExercise() {
        Log.i(TAG, "Attempting to go to next exercise.");
        if (!allExercisesLoaded || allExercisesInTopic == null || allExercisesInTopic.isEmpty()) {
            Log.e(TAG, "Cannot go to next: All exercises list not loaded or empty.");
            if (view != null) view.showToast("Could not determine the next exercise.");
            setButtonState(STATE_FINISHED_NO_NEXT, "Error - Finish");
            return;
        }

        int currentIndex = -1;
        for (int i = 0; i < allExercisesInTopic.size(); i++) {
            if (allExercisesInTopic.get(i).getId().equals(exerciseId)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex >= 0 && currentIndex < allExercisesInTopic.size() - 1) {
            Exercise nextExercise = allExercisesInTopic.get(currentIndex + 1);
            Log.i(TAG, "Navigating to next exercise: ID=" + nextExercise.getId() + ", Title=" + nextExercise.getTitle());
            if (view != null) {
                view.navigateToNextExercise(levelName, topicId, nextExercise.getId(), topicDisplayTitle, nextExercise.getTitle());
            }
        } else {
            Log.w(TAG, "goToNext called, but no next exercise found. CurrentIndex=" + currentIndex + ", ListSize=" + allExercisesInTopic.size());
            if (view != null) view.showToast("You have completed all exercises in this topic!");
            setButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
        }
    }

    private void finishExercise() {
        Log.i(TAG, "finishExercise called.");
        if (view != null) {
            view.finishActivity();
        }
    }

    private boolean hasNextExercise() {
        if (allExercisesLoaded && allExercisesInTopic != null && !allExercisesInTopic.isEmpty() && exerciseId != null) {
            int currentIndex = -1;
            for (int i = 0; i < allExercisesInTopic.size(); i++) {
                if (allExercisesInTopic.get(i).getId().equals(exerciseId)) {
                    currentIndex = i;
                    break;
                }
            }
            boolean hasNext = currentIndex >= 0 && currentIndex < allExercisesInTopic.size() - 1;
            Log.d(TAG, "hasNextExercise Check: CurrentExerciseID=" + exerciseId + ", CurrentIndex=" + currentIndex + ", ListSize=" + allExercisesInTopic.size() + ", HasNext=" + hasNext);
            return hasNext;
        }
        Log.d(TAG, "hasNextExercise Check: AllExercisesLoaded=" + allExercisesLoaded + ", List empty or null=" + (allExercisesInTopic == null || allExercisesInTopic.isEmpty()));
        return false;
    }

    private void setButtonState(int state, String text) {
        Log.d(TAG, "Setting button state: " + state + " (" + text + ")");
        currentButtonState = state;
        if (view != null) {
            view.setButtonState(state, text);
        }
    }

    private void resetSubmitButtonToSubmitState() {
        // Only set to submit if there are questions
        if (questionsList != null && !questionsList.isEmpty()) {
            setButtonState(STATE_SUBMIT, "Submit");
        }
        // If no questions, the state is handled in checkIfAllDataLoaded
    }

    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        this.view = null;
    }
}
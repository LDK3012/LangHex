package com.example.langhexx.Controller;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.langhexx.Model.ReadingQuestion;
import com.example.langhexx.R; // Required for mapping RadioButton IDs

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReadingController {

    private static final String TAG = "ReadingController";

    // --- View Interface ---
    public interface ViewInterface {
        void displayExerciseTitle(String title);
        void displayPassage(String text);
        void updateAdapterData(List<ReadingQuestion> newQuestions);
        void showResultsInAdapter(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctnessMap);
        void resetAdapterState();
        void setButtonState(int state, String text);
        Map<Integer, Integer> getAdapterSelectedAnswers();
        boolean areAdapterAnswersAllCorrect(); // Ask adapter via View
        void showToast(String message);
        void showFailToast(String message);
        void showConfirmationDialog(String title, String message, Runnable onConfirm);
        void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle);
        void finishActivity();
        void scrollToQuestion(int index);
        void setUIElementsVisibility(boolean visible); // Control overall visibility
    }

    // --- State Constants ---
    public static final int STATE_SUBMIT = 0;
    public static final int STATE_RETRY = 1;
    public static final int STATE_NEXT = 2;
    public static final int STATE_FINISHED_HAS_NEXT = 3; // Internally might be same as NEXT but indicates completion
    public static final int STATE_FINISHED_NO_NEXT = -1; // Final state, no more exercises
    private int currentButtonState = STATE_SUBMIT; // Initial state

    // --- Data Members ---
    private ViewInterface view;
    private String levelName;
    private String topicTitle;
    private String exerciseTitle;
    private String passageText;
    private List<ReadingQuestion> questionsList; // Controller holds the canonical list
    private ArrayList<String> allExerciseTitles;
    private boolean dataLoaded = false;
    private boolean titlesLoaded = false;

    // --- Firebase ---
    private DatabaseReference databaseReference;

    public ReadingController(ViewInterface view, Intent intent) {
        this.view = view;
        this.questionsList = new ArrayList<>();
        this.allExerciseTitles = new ArrayList<>();
        this.databaseReference = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        // Extract data from Intent
        if (intent != null) {
            levelName = intent.getStringExtra("LEVEL_NAME");
            topicTitle = intent.getStringExtra("TOPIC_TITLE");
            exerciseTitle = intent.getStringExtra("EXERCISE_TITLE");
            Log.d(TAG, "Intent data: Level=" + levelName + ", Topic=" + topicTitle + ", Exercise=" + exerciseTitle);
        } else {
            handleInitializationError("Error: Intent is null.");
            return;
        }

        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            handleInitializationError("Error: Missing exercise identifiers in Intent.");
            return;
        }
    }

    // Called by View after setup
    public void initialize() {
        Log.d(TAG, "Initializing Controller...");
        view.setUIElementsVisibility(false); // Hide UI initially
        view.displayExerciseTitle(exerciseTitle); // Show title early
        loadExerciseDataFromFirebase();
        loadAllExerciseTitlesFromFirebase();
    }

    private void handleInitializationError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (view != null) {
            view.showToast(errorMessage);
            view.finishActivity();
        }
    }


    private void loadExerciseDataFromFirebase() {
        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            Log.e(TAG, "Cannot load exercise data: Identifiers are null.");
            return; // Already handled in constructor, but double-check
        }
        DatabaseReference exerciseRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Reading").child("Topics").child(topicTitle).child("Exercises").child(exerciseTitle);

        Log.i(TAG, "Loading exercise data from: " + exerciseRef.toString());

        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "Exercise data not found at path.");
                    view.showToast("Exercise data not found.");
                    view.finishActivity();
                    return;
                }

                // Load script (passage)
                passageText = snapshot.child("script").getValue(String.class);
                if (passageText != null && !passageText.isEmpty()) {
                    view.displayPassage(passageText);
                    Log.d(TAG, "Passage loaded.");
                } else {
                    Log.w(TAG, "'script' field missing or empty.");
                    view.displayPassage(null); // Explicitly tell view passage is missing
                    // Don't show a toast here, just let the view hide the passage area
                }

                // Load questions
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

                // Update the controller's list and then the view's adapter
                questionsList.clear();
                questionsList.addAll(loadedQuestions);
                view.updateAdapterData(questionsList); // Update adapter via View Interface

                dataLoaded = true;
                checkIfAllDataLoaded(); // Check if both data and titles are loaded
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase loading cancelled/failed: " + error.getMessage(), error.toException());
                view.showToast("Error loading data: " + error.getMessage());
                view.finishActivity(); // Finish if essential data fails to load
            }
        });
    }

    private ReadingQuestion parseReadingQuestionSnapshot(DataSnapshot questionSnap) {
        // (This logic is moved directly from the original Activity - unchanged)
        ReadingQuestion question = new ReadingQuestion();
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
                // Return null or empty question if options are critical and failed to parse
                return null;
            }
        } else {
            Log.w(TAG, "'options' node missing or invalid format for question: " + questionSnap.getKey());
            // Decide if a question without options is valid
            // return null; // If options are mandatory
        }

        // Validate essential fields before returning the question object
        if (text != null && !text.isEmpty() && answer != null && !answer.isEmpty() && !stringOptionsMap.isEmpty()) {
            question.setQuestionText(text);
            question.setOptions(stringOptionsMap);
            question.setCorrectAnswer(answer);
            return question;
        } else {
            Log.w(TAG, "Skipping question due to missing essential data: Key=" + questionSnap.getKey() +
                    ", Text=" + (text != null && !text.isEmpty()) +
                    ", Answer=" + (answer != null && !answer.isEmpty()) +
                    ", Options=" + !stringOptionsMap.isEmpty());
            return null; // Return null if essential data is missing
        }
    }


    private void loadAllExerciseTitlesFromFirebase() {
        if (levelName == null || topicTitle == null) {
            Log.e(TAG, "Cannot load titles: Level/Topic null.");
            return; // Should have been caught earlier
        }
        DatabaseReference exercisesRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Reading").child("Topics").child(topicTitle).child("Exercises");

        Log.d(TAG, "Loading all titles from: " + exercisesRef.toString());
        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                allExerciseTitles.clear(); // Clear previous titles
                if (snapshot.exists()) {
                    for (DataSnapshot exSnap : snapshot.getChildren()) {
                        String title = exSnap.getKey();
                        if (title != null && !title.isEmpty()) {
                            allExerciseTitles.add(title);
                        }
                    }
                    // Simple alphabetical sort, adjust if specific order needed
                    // Collections.sort(allExerciseTitles);
                    Log.i(TAG, "Loaded " + allExerciseTitles.size() + " titles for topic: " + topicTitle);
                } else {
                    Log.w(TAG, "No exercises found under topic path: " + topicTitle);
                }
                titlesLoaded = true;
                checkIfAllDataLoaded(); // Check if both data and titles are loaded
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load all exercise titles: " + error.getMessage());
                // Decide if this is critical. Maybe proceed without "Next" functionality?
                // For now, log the error and continue.
                titlesLoaded = true; // Mark as loaded (with error state) to allow UI to proceed
                checkIfAllDataLoaded();
            }
        });
    }

    private void checkIfAllDataLoaded() {
        if (dataLoaded && titlesLoaded) {
            Log.d(TAG, "All initial data (exercise + titles) loaded.");
            // Now that data is loaded, make the main UI visible
            // Visibility check should depend on whether there is content to show
            boolean hasContentToShow = (passageText != null && !passageText.isEmpty()) || !questionsList.isEmpty();
            view.setUIElementsVisibility(hasContentToShow);
            if(questionsList.isEmpty()){
                view.showToast("No questions available for this exercise.");
                // Optionally disable submit button if no questions
                setButtonState(STATE_SUBMIT, "Submit"); // Keep submit state, but it won't do anything if list is empty
            } else {
                // Set initial button state only if there are questions
                resetSubmitButtonToSubmitState();
            }
        }
    }


    // --- Button Actions ---

    public void onSubmitButtonClicked() {
        Log.d(TAG, "Submit button clicked. Current state: " + currentButtonState);
        // Prevent action if no questions loaded
        if (questionsList.isEmpty()) {
            Log.w(TAG, "Submit button clicked, but no questions are loaded.");
            view.showToast("No questions to submit.");
            // Optionally handle 'Next' if passage exists but no questions
            if (hasNextExercise()) {
                goToNextExercise(); // Or show a 'Continue' button?
            } else {
                finishExercise(); // Or show a 'Finish' button?
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
            case STATE_FINISHED_HAS_NEXT: // Treat same as NEXT for button action
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

        // Should not happen if button logic is correct, but check anyway
        if (totalQuestions == 0) {
            Log.w(TAG, "checkAnswers: No questions available.");
            // Decide what to do - maybe show Next/Finish?
            updateButtonStateBasedOnResults(true); // Treat as 'all correct' to move on
            return;
        }


        for (int i = 0; i < totalQuestions; i++) {
            // Check if the key exists and the value is not -1
            if (userAnswers.getOrDefault(i, -1) == -1) {
                allAnswered = false;
                firstUnanswered = i;
                break;
            }
        }

        if (allAnswered) {
            Log.d(TAG, "Check: All questions answered. Showing confirmation dialog.");
            // Use lambda for the confirmation action
            view.showConfirmationDialog("Xác nhận nộp bài", "Bạn có chắc chắn muốn nộp bài không?", this::proceedWithSubmission);
        } else {
            Log.d(TAG, "Check: Not all questions answered. First unanswered: " + firstUnanswered);
            view.showFailToast("Please answer all questions!");
            if (firstUnanswered != -1) {
                view.scrollToQuestion(firstUnanswered); // Ask view to scroll
            }
        }
    }


    private void proceedWithSubmission() {
        Log.i(TAG, "Proceeding with submission...");
        Map<Integer, Integer> userAnswers = view.getAdapterSelectedAnswers();
        int totalQuestions = questionsList.size();

        if (totalQuestions == 0) {
            Log.w(TAG, "Proceeding with submission, but no questions exist.");
            updateButtonStateBasedOnResults(true); // Treat as all correct if no questions
            return;
        }


        Log.d(TAG, "Scoring " + totalQuestions + " questions.");
        int correctCount = 0;
        Map<Integer, Boolean> correctnessMap = new HashMap<>();

        for (int i = 0; i < totalQuestions; i++) {
            if (i >= questionsList.size()) {
                Log.w(TAG,"Index out of bounds during submission: " + i);
                continue; // Should not happen if logic is sound
            }
            ReadingQuestion question = questionsList.get(i);
            int selectedRadioButtonId = userAnswers.getOrDefault(i, -1); // Get the ID or -1
            String selectedAnswerKey = mapRadioButtonIdToKey(selectedRadioButtonId); // Map ID to "A", "B", etc.

            boolean isCorrect = question.getCorrectAnswer() != null &&
                    !selectedAnswerKey.isEmpty() &&
                    selectedAnswerKey.equalsIgnoreCase(question.getCorrectAnswer());

            if (isCorrect) {
                correctCount++;
            }
            correctnessMap.put(i, isCorrect); // Store correctness for this question index
            Log.v(TAG, "Q" + (i + 1) + ": SelectedID=" + selectedRadioButtonId + " (Key='" + selectedAnswerKey + "'), CorrectKey='" + question.getCorrectAnswer() + "', Result=" + isCorrect);
        }

        String resultMessage = String.format(Locale.getDefault(), "Result: %d / %d correct!", correctCount, totalQuestions);
        view.showToast(resultMessage);
        Log.i(TAG, "Final Score: " + correctCount + "/" + totalQuestions);

        // Tell the View to update the adapter to show results
        view.showResultsInAdapter(userAnswers, correctnessMap);

        // Update button state based on results and whether there's a next exercise
        boolean allCorrect = (correctCount == totalQuestions);
        updateButtonStateBasedOnResults(allCorrect);

        Log.d(TAG, "Submission process finished. Button state: " + currentButtonState);
    }

    // Helper to map RadioButton Resource IDs to Answer Keys ("A", "B", "C", "D")
    // This belongs in the Controller as it relates IDs (known at compile time) to logical keys.
    private String mapRadioButtonIdToKey(int selectedRadioButtonId) {
        if (selectedRadioButtonId == R.id.rbOptionA) return "A";
        if (selectedRadioButtonId == R.id.rbOptionB) return "B";
        if (selectedRadioButtonId == R.id.rbOptionC) return "C";
        if (selectedRadioButtonId == R.id.rbOptionD) return "D";
        // Add more mappings if your item_question.xml has more options (e.g., rbOptionE)
        return ""; // Return empty string if ID is -1 or not recognized
    }


    // Logic to determine button state AFTER submission
    private void updateButtonStateBasedOnResults(boolean allCorrect) {
        boolean hasNext = hasNextExercise();
        Log.d(TAG,"updateButtonStateBasedOnResults: AllCorrect=" + allCorrect + ", HasNext=" + hasNext);

        if (allCorrect) {
            // All answers are correct
            if (hasNext) {
                // Correct and there's a next exercise -> NEXT
                Log.i(TAG,"All correct. Has next exercise. Setting state to NEXT.");
                setButtonState(STATE_NEXT, "Next");
            } else {
                // Correct and it's the last exercise -> FINISHED (no next)
                Log.i(TAG,"All correct. Last exercise. Setting state to FINISHED_NO_NEXT.");
                setButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
            }
        } else {
            // At least one answer is incorrect -> RETRY
            Log.i(TAG,"Some answers incorrect. Setting state to RETRY.");
            setButtonState(STATE_RETRY, "Retry");
        }
    }


    private void retryExercise() {
        Log.i(TAG, "Retry button clicked. Resetting state.");

        // 1. Ask the View to reset the adapter's state
        view.resetAdapterState();

        // 2. Reset the Controller's and View's button state back to SUBMIT
        resetSubmitButtonToSubmitState();

        // 3. Ask the View to scroll to the top question (optional)
        view.scrollToQuestion(0);

        Log.d(TAG,"Exercise state reset for retry. Button state: SUBMIT");
    }

    private void goToNextExercise() {
        Log.i(TAG, "Attempting to go to next exercise.");
        if (!titlesLoaded || allExerciseTitles == null || allExerciseTitles.isEmpty()) {
            Log.e(TAG, "Cannot go to next: Titles list not loaded or empty.");
            view.showToast("Could not determine the next exercise.");
            // Fallback: Maybe set to Finish? Or retry loading titles?
            setButtonState(STATE_FINISHED_NO_NEXT, "Error - Finish");
            return;
        }

        int currentIndex = allExerciseTitles.indexOf(exerciseTitle);
        if (currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1) {
            String nextExerciseTitle = allExerciseTitles.get(currentIndex + 1);
            Log.i(TAG, "Navigating to next exercise: " + nextExerciseTitle);
            // Tell the View to navigate
            view.navigateToNextExercise(levelName, topicTitle, nextExerciseTitle);
        } else {
            // This case should ideally be handled by setting STATE_FINISHED_NO_NEXT earlier,
            // but handle defensively here too.
            Log.w(TAG, "goToNext called, but no next exercise found. CurrentIndex=" + currentIndex + ", ListSize=" + allExerciseTitles.size());
            view.showToast("You have completed all exercises in this topic!");
            setButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
            // Optionally call finishActivity directly?
            // finishExercise();
        }
    }

    private void finishExercise() {
        Log.i(TAG, "finishExercise called.");
        view.finishActivity(); // Tell the view to close itself
    }

    // --- State and Helper Methods ---

    private boolean hasNextExercise() {
        if (titlesLoaded && allExerciseTitles != null && !allExerciseTitles.isEmpty() && exerciseTitle != null) {
            int currentIndex = allExerciseTitles.indexOf(exerciseTitle);
            boolean hasNext = currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1;
            Log.d(TAG, "hasNextExercise Check: CurrentIndex=" + currentIndex + ", ListSize=" + allExerciseTitles.size() + ", HasNext=" + hasNext);
            return hasNext;
        }
        Log.d(TAG, "hasNextExercise Check: Titles loaded=" + titlesLoaded + ", List empty=" + (allExerciseTitles == null || allExerciseTitles.isEmpty()));
        return false; // Cannot determine if titles aren't loaded or list is empty
    }

    // Sets the internal state and tells the View to update the button
    private void setButtonState(int state, String text) {
        Log.d(TAG, "Setting button state: " + state + " (" + text + ")");
        currentButtonState = state;
        if (view != null) {
            view.setButtonState(state, text);
        }
    }

    private void resetSubmitButtonToSubmitState() {
        setButtonState(STATE_SUBMIT, "Submit");
    }


    // --- Lifecycle ---
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        // Clean up resources if needed, e.g., remove Firebase listeners if they weren't single-value events
        this.view = null; // Remove reference to View to prevent leaks
    }
}
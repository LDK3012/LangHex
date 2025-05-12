package com.example.langhexx.Controller;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.langhexx.Model.Exercise;
import com.example.langhexx.Model.ReadingQuestion;
import com.example.langhexx.R;

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

    private String passageText;
    private List<ReadingQuestion> questionsList;
    private ArrayList<Exercise> allExercisesInTopic;
    private boolean dataLoaded = false;
    private boolean allExercisesLoaded = false;

    private DatabaseReference databaseReference;

    public ReadingController(ViewInterface view, Intent intent) {
        this.view = view;
        this.questionsList = new ArrayList<>();
        this.allExercisesInTopic = new ArrayList<>();
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

        if (levelName == null || topicId == null || exerciseId == null || topicDisplayTitle == null || exerciseDisplayTitle == null) {
            handleInitializationError("Error: Missing identifiers in Intent.");
            return;
        }
    }

    public void initialize() {
        Log.d(TAG, "Initializing Controller...");
        view.setUIElementsVisibility(false);
        view.displayExerciseTitle(exerciseDisplayTitle); // Hiển thị tên bài tập
        loadExerciseDataFromFirebase();
        loadAllExercisesInTopicFromFirebase();
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
            return;
        }
        DatabaseReference exerciseRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Reading").child("Topics").child(topicId) // Sử dụng topicId
                .child("Exercises").child(exerciseId);          // Sử dụng exerciseId

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
                view.updateAdapterData(questionsList);

                dataLoaded = true;
                checkIfAllDataLoaded();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase loading cancelled/failed: " + error.getMessage(), error.toException());
                view.showToast("Error loading data: " + error.getMessage());
                view.finishActivity();
            }
        });
    }


    private ReadingQuestion parseReadingQuestionSnapshot(DataSnapshot questionSnap) {
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
            return;
        }
        // Đường dẫn sử dụng topicId
        DatabaseReference exercisesPathRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Reading").child("Topics").child(topicId) // Sử dụng topicId
                .child("Exercises");

        Log.d(TAG, "Loading all exercises from: " + exercisesPathRef.toString());
        exercisesPathRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                allExercisesInTopic.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot exSnap : snapshot.getChildren()) {
                        String exId = exSnap.getKey(); // ID của exercise
                        String exTitle = exSnap.child("title").getValue(String.class); // Tên hiển thị của exercise

                        if (exTitle == null || exTitle.isEmpty()) {
                            exTitle = exId; // Fallback
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
        if (dataLoaded && allExercisesLoaded) {
            Log.d(TAG, "All initial data (exercise details + all exercises in topic) loaded.");
            boolean hasContentToShow = (passageText != null && !passageText.isEmpty()) || !questionsList.isEmpty();
            view.setUIElementsVisibility(hasContentToShow);
            if(questionsList.isEmpty() && (passageText == null || passageText.isEmpty())){
                view.showToast("No content available for this exercise.");
                // Dù không có content, vẫn có thể có bài tập tiếp theo
                if(hasNextExercise()){
                    setButtonState(STATE_NEXT, "Next Exercise");
                } else {
                    setButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
                }
            } else if (questionsList.isEmpty() && passageText != null && !passageText.isEmpty()) {
                view.showToast("No questions for this passage. Click Next if available.");
                if(hasNextExercise()){
                    setButtonState(STATE_NEXT, "Next Exercise");
                } else {
                    setButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
                }
            }
            else {
                resetSubmitButtonToSubmitState();
            }
        }
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
                // case STATE_FINISHED_HAS_NEXT: // Gộp vào STATE_NEXT
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
                setButtonState(STATE_NEXT, "Next Exercise"); // Thay đổi text
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
        view.resetAdapterState();
        resetSubmitButtonToSubmitState();
        view.scrollToQuestion(0);
        Log.d(TAG,"Exercise state reset for retry. Button state: SUBMIT");
    }

    private void goToNextExercise() {
        Log.i(TAG, "Attempting to go to next exercise.");
        if (!allExercisesLoaded || allExercisesInTopic == null || allExercisesInTopic.isEmpty()) {
            Log.e(TAG, "Cannot go to next: All exercises list not loaded or empty.");
            view.showToast("Could not determine the next exercise.");
            setButtonState(STATE_FINISHED_NO_NEXT, "Error - Finish");
            return;
        }

        int currentIndex = -1;
        for (int i = 0; i < allExercisesInTopic.size(); i++) {
            if (allExercisesInTopic.get(i).getId().equals(exerciseId)) { // So sánh bằng ID
                currentIndex = i;
                break;
            }
        }

        if (currentIndex >= 0 && currentIndex < allExercisesInTopic.size() - 1) {
            Exercise nextExercise = allExercisesInTopic.get(currentIndex + 1); // Lấy object Exercise
            Log.i(TAG, "Navigating to next exercise: ID=" + nextExercise.getId() + ", Title=" + nextExercise.getTitle());
            // Truyền ID và Title của exercise tiếp theo, cùng với topicId và topicDisplayTitle
            view.navigateToNextExercise(levelName, topicId, nextExercise.getId(), topicDisplayTitle, nextExercise.getTitle());
        } else {
            Log.w(TAG, "goToNext called, but no next exercise found. CurrentIndex=" + currentIndex + ", ListSize=" + allExercisesInTopic.size());
            view.showToast("You have completed all exercises in this topic!");
            setButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
        }
    }

    private void finishExercise() {
        Log.i(TAG, "finishExercise called.");
        view.finishActivity();
    }

    // --- State and Helper Methods ---

    private boolean hasNextExercise() {
        if (allExercisesLoaded && allExercisesInTopic != null && !allExercisesInTopic.isEmpty() && exerciseId != null) {
            int currentIndex = -1;
            for (int i = 0; i < allExercisesInTopic.size(); i++) {
                if (allExercisesInTopic.get(i).getId().equals(exerciseId)) { // So sánh bằng ID
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
        setButtonState(STATE_SUBMIT, "Submit");
    }

    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        this.view = null; // Prevent memory leaks
    }
}
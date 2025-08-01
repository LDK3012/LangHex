package com.example.langhexx.Controller;

import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;

import com.example.langhexx.Model.Exercise;
import com.example.langhexx.Model.ReadingQuestion;
import com.example.langhexx.R;
import com.example.langhexx.Util.AzureTTSHelper;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class ReadingController {

    private static final String TAG = "ReadingController";
    public interface TTSCallback {
        void onStart();
        void onDone(byte[] audioData);
        void onError(String message);
    }

    public void synthesizeTextToSpeech(String text, TTSCallback callback) {
        AzureTTSHelper.synthesize(text, new AzureTTSHelper.TTSCallback() {
            @Override
            public void onStart() {
                if (callback != null) callback.onStart();
            }
            @Override
            public void onDone(byte[] audioData) {
                if (callback != null) callback.onDone(audioData);
            }
            @Override
            public void onError(String message) {
                if (callback != null) callback.onError(message);
            }
        });
    }

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
        void setAdapterAnswerListener();
        void applySavedAnswersToAdapter(Map<Integer, Integer> savedAnswers);
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
    private List<ReadingQuestion> originalQuestionsList = new ArrayList<>();
    private List<ReadingQuestion> displayedQuestions = new ArrayList<>();

    private ArrayList<Exercise> allExercisesInTopic;
    private boolean dataLoaded = false;
    private boolean allExercisesLoaded = false;
    private DatabaseReference databaseReference;

    public ReadingController(ViewInterface view, Intent intent) {
        this.view = view;
        this.allExercisesInTopic = new ArrayList<>();
        this.databaseReference = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        if (intent != null) {
            levelName = intent.getStringExtra("LEVEL_NAME");
            topicId = intent.getStringExtra("TOPIC_ID");
            exerciseId = intent.getStringExtra("EXERCISE_ID");
            topicDisplayTitle = intent.getStringExtra("TOPIC_TITLE");
            exerciseDisplayTitle = intent.getStringExtra("EXERCISE_TITLE");
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
        if (view == null) {
            handleInitializationError("Initialization Error.");
            return;
        }
        view.setUIElementsVisibility(false);
        view.displayExerciseTitle(exerciseDisplayTitle);
        loadExerciseDataFromFirebase();
        loadAllExercisesInTopicFromFirebase();
        view.setAdapterAnswerListener();
    }

    private void handleInitializationError(String errorMessage) {
        if (view != null) {
            view.showToast(errorMessage);
            view.finishActivity();
        }
    }

    private void loadExerciseDataFromFirebase() {
        if (levelName == null || topicId == null || exerciseId == null) {
            dataLoaded = true;
            checkIfAllDataLoaded();
            return;
        }
        DatabaseReference exerciseRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Reading").child("Topics").child(topicId)
                .child("Exercises").child(exerciseId);

        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    view.showToast("Exercise data not found.");
                    dataLoaded = true;
                    checkIfAllDataLoaded();
                    return;
                }

                passageText = snapshot.child("script").getValue(String.class);
                view.displayPassage(passageText);

                DataSnapshot questionsSnapshot = snapshot.child("questions");
                List<ReadingQuestion> loadedQuestions = new ArrayList<>();
                if (questionsSnapshot.exists()) {
                    for (DataSnapshot questionSnap : questionsSnapshot.getChildren()) {
                        ReadingQuestion question = parseReadingQuestionSnapshot(questionSnap);
                        if (question != null) loadedQuestions.add(question);
                    }
                }
                // Lưu lại danh sách gốc
                originalQuestionsList.clear();
                originalQuestionsList.addAll(loadedQuestions);

                dataLoaded = true;
                checkIfAllDataLoaded();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                view.showToast("Error loading data: " + error.getMessage());
                dataLoaded = true;
                checkIfAllDataLoaded();
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
                return null;
            }
        }
        if (text != null && !text.isEmpty() && answer != null && !answer.isEmpty() && !stringOptionsMap.isEmpty()) {
            question.setQuestionText(text);
            question.setOptions(stringOptionsMap);
            question.setCorrectAnswer(answer); // Gán đúng đáp án cho từng object
            return question;
        } else {
            return null;
        }
    }

    private void loadAllExercisesInTopicFromFirebase() {
        if (levelName == null || topicId == null) {
            allExercisesLoaded = true;
            checkIfAllDataLoaded();
            return;
        }
        DatabaseReference exercisesPathRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Reading").child("Topics").child(topicId)
                .child("Exercises");

        exercisesPathRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                allExercisesInTopic.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot exSnap : snapshot.getChildren()) {
                        String exId = exSnap.getKey();
                        String exTitle = exSnap.child("title").getValue(String.class);
                        if (exTitle == null || exTitle.isEmpty()) exTitle = exId;
                        if (exId != null) {
                            allExercisesInTopic.add(new Exercise(exId, exTitle));
                        }
                    }
                }
                allExercisesLoaded = true;
                checkIfAllDataLoaded();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                allExercisesLoaded = true;
                checkIfAllDataLoaded();
            }
        });
    }

    private void checkIfAllDataLoaded() {
        if (dataLoaded && allExercisesLoaded && view != null) {
            // Shuffle mới từ list gốc, lưu lại list hiển thị
            displayedQuestions = new ArrayList<>(originalQuestionsList);
            Collections.shuffle(displayedQuestions);
            view.updateAdapterData(displayedQuestions);

            boolean hasContentToShow = (passageText != null && !passageText.isEmpty()) || !displayedQuestions.isEmpty();
            view.setUIElementsVisibility(hasContentToShow);
            if (displayedQuestions.isEmpty()) {
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
                resetSubmitButtonToSubmitState();
            }
        }
    }

    public void onSubmitButtonClicked() {
        if (displayedQuestions.isEmpty()) {
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
                finishExercise();
                break;
        }
    }

    private void checkAnswersAndShowConfirmationIfNeeded() {
        Map<Integer, Integer> userAnswers = view.getAdapterSelectedAnswers();
        int totalQuestions = displayedQuestions.size();
        boolean allAnswered = true;
        int firstUnanswered = -1;

        for (int i = 0; i < totalQuestions; i++) {
            if (userAnswers.getOrDefault(i, -1) == -1) {
                allAnswered = false;
                firstUnanswered = i;
                break;
            }
        }

        if (allAnswered) {
            view.showConfirmationDialog("Confirm", "Are you sure want to submit ?", this::proceedWithSubmission);
        } else {
            view.showFailToast("Please answer all questions!");
            if (firstUnanswered != -1) view.scrollToQuestion(firstUnanswered);
        }
    }

    private void proceedWithSubmission() {
        Map<Integer, Integer> userAnswers = view.getAdapterSelectedAnswers();
        int totalQuestions = displayedQuestions.size();

        int correctCount = 0;
        Map<Integer, Boolean> correctnessMap = new HashMap<>();

        for (int i = 0; i < totalQuestions; i++) {
            ReadingQuestion displayedQ = displayedQuestions.get(i);
            String displayedId = displayedQ.getId();
            ReadingQuestion originalQ = findQuestionById(originalQuestionsList, displayedId);
            String correct = (originalQ != null) ? originalQ.getCorrectAnswer() : null;

            int selectedRadioButtonId = userAnswers.getOrDefault(i, -1);
            String selectedAnswerKey = mapRadioButtonIdToKey(selectedRadioButtonId);

            boolean isCorrect = correct != null
                    && !selectedAnswerKey.isEmpty()
                    && selectedAnswerKey.equalsIgnoreCase(correct);

            if (isCorrect) correctCount++;
            correctnessMap.put(i, isCorrect);
        }
        float score10 = (totalQuestions == 0) ? 0 : ((float) correctCount / totalQuestions) * 10;
        String scoreStr = (score10 == (int) score10)
                ? String.format(Locale.getDefault(), "%d", (int) score10)
                : String.format(Locale.getDefault(), "%.1f", score10);
        String resultMessage = "Your score: " + scoreStr + "/10";
        view.showToast(resultMessage);

        view.showResultsInAdapter(userAnswers, correctnessMap);
        boolean allCorrect = (correctCount == totalQuestions);
        updateButtonStateBasedOnResults(allCorrect);
    }
    private ReadingQuestion findQuestionById(List<ReadingQuestion> list, String id) {
        for (ReadingQuestion q : list) {
            if (q.getId() != null && q.getId().equals(id)) return q;
        }
        return null;
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
        if (allCorrect) {
            if (hasNext) setButtonState(STATE_NEXT, "Next Exercise");
            else setButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
        } else {
            setButtonState(STATE_RETRY, "Retry");
        }
    }

    private void retryExercise() {
        if (view == null) return;
        displayedQuestions = new ArrayList<>(originalQuestionsList);
        Collections.shuffle(displayedQuestions);
        view.updateAdapterData(displayedQuestions);

        resetSubmitButtonToSubmitState();
        view.scrollToQuestion(0);
    }

    private void goToNextExercise() {
        if (!allExercisesLoaded || allExercisesInTopic == null || allExercisesInTopic.isEmpty()) {
            view.showToast("Could not determine the next exercise.");
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
            view.navigateToNextExercise(levelName, topicId, nextExercise.getId(), topicDisplayTitle, nextExercise.getTitle());
        } else {
            view.showToast("You have completed all exercises in this topic!");
            setButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
        }
    }

    private void finishExercise() {
        if (view != null) view.finishActivity();
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
            return currentIndex >= 0 && currentIndex < allExercisesInTopic.size() - 1;
        }
        return false;
    }

    private void setButtonState(int state, String text) {
        currentButtonState = state;
        if (view != null) view.setButtonState(state, text);
    }

    private void resetSubmitButtonToSubmitState() {
        if (displayedQuestions != null && !displayedQuestions.isEmpty()) {
            setButtonState(STATE_SUBMIT, "Submit");
        }
    }

    public void onDestroy() {
        this.view = null;
    }
}

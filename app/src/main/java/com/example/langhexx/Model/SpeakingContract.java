package com.example.langhexx.Model;

import android.content.Context;
import java.util.List;

public interface SpeakingContract {

    interface Model {
        void loadQuestions(String levelName, String topicTitle, QuestionListener listener);
        void evaluateAnswer(String question, String userAnswer, EvaluationListener listener);
        String getCurrentQuestion();
        int getCurrentQuestionIndex();
        int getQuestionCount();
        void advanceQuestionIndex();
        void cleanup();
    }

    interface View {
        void displayQuestion(String question);
        void displayUserAnswer(String userAnswer, boolean isCorrect);
        void displayEvaluationFeedback(String feedbackVi, String suggestionEn);
        void showFeedbackDialog(String message);
        void showCompletionMessage();
        void showError(String message);
        void showToast(String message);
        void showCustomToast(boolean success, String message);
        void setMicButtonEnabled(boolean enabled);
        void playSound(boolean isCorrect);
        void speakText(String text, String utteranceId);
        void showWarningIcon(boolean show, String feedbackMessage);
        void hideResponseElements();
        void updateUiForNewQuestion(String question);
        void scrollDown();
        Context getContext();
        void requestAudioPermission();
        void finishActivity();

        // --- Phương thức cho SpeechRecognizer nội bộ ---
        void startListening();
        void stopListening();
        void indicateListeningState(boolean isListening);

        // --- Phương thức mới cho Dialog Xác Nhận Giọng Nói ---
        void showSpeechConfirmationDialog(String partialText);
        void updateSpeechConfirmationDialog(String newPartialText);
        void dismissSpeechConfirmationDialog();
    }

    interface Controller {
        void viewDidLoad();
        void loadData();
        void onMicButtonClicked();
        void onCloseButtonClicked();
        void onQuestionSpeakerClicked(String question);
        void onResponseSpeakerClicked(String response);
        void onWarningIconClicked(String message);
        void onSpeechResult(String spokenText);
        void onSpeechError(String errorReason);
        void onPermissionResult(boolean granted);
        void onDestroy();
    }

    interface QuestionListener {
        void onQuestionsLoaded(List<String> questions);
        void onQuestionLoadError(String error);
    }

    interface EvaluationListener {
        void onEvaluationSuccess(String userAnswer, boolean isCorrect, String feedbackVi, String suggestionEn);
        void onEvaluationError(String userAnswer, String errorType);
    }
}
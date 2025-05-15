package com.example.langhexx.Model;

import android.content.Context;
import java.util.List;

public interface SpeakingContract {

    // --- MODEL Interface ---
    interface Model {
        void loadQuestions(String levelName, String identifier, QuestionListener listener);
        void evaluateAnswer(String question, String userAnswer, EvaluationListener listener);
        String getCurrentQuestion();
        int getCurrentQuestionIndex();
        int getQuestionCount();
        void advanceQuestionIndex();
        void previousQuestionIndex();
        void cleanup();
    }

    // --- BASE VIEW Interface (Các phương thức chung) ---
    interface BaseView {
        void showError(String message);
        void showToast(String message);
        void showCustomToast(boolean success, String message);
        void setMicButtonEnabled(boolean enabled);
        void playSound(boolean isCorrect);
        void speakText(String text, String utteranceId);
        Context getContext();
        void requestAudioPermission();
        void finishActivity();
    }

    // --- PRONUNCIATION VIEW Interface
    interface PronunciationView extends BaseView {
        void displayScreenTitle(String title);
        void displayScriptToRepeat(String scriptText);
        void updateScriptCounter(int current, int total);
        void setNavigationButtonsEnabled(boolean isPreviousEnabled, boolean isNextEnabled);
        void requestAudioPermission();
        void startRecordingUI();
        void stopRecordingUI();
        void updateRecordingUIState(boolean isRecording);
        void updatePlaybackUIState(boolean isPlaying, boolean hasRecording);
        void updateSeekBarProgress(int progress, int max);
        void updatePlayTime(String currentTime, String totalTime);
        String getRecordingFilePath();
        void setRecordingFilePath(String path);
    }

    // --- INTERACTIVE SPEAKING VIEW Interface
    interface InteractiveSpeakingView extends BaseView {
        void displayQuestionToAnswer(String question);
        void displayUserAnswer(String userAnswer, boolean isCorrect);
        void displayEvaluationFeedback(String feedbackVi, String suggestionEn);
        void showFeedbackDialog(String message);
        void showCompletionMessage();
        void showWarningIcon(boolean show, String feedbackMessage);
        void hideResponseElements();
        void updateUiForNewQuestion(String question);
        void scrollDown();

        // Phương thức cho SpeechRecognizer
        void startListening();
        void stopListening();
        void indicateListeningState(boolean isListening);

        // Phương thức cho Dialog Giọng Nói
        void showSpeechConfirmationDialog(String partialText);
        void updateSpeechConfirmationDialog(String newPartialText);
        void dismissSpeechConfirmationDialog();
    }


    // --- CONTROLLER Interface ---
    interface Controller {
        void viewDidLoad();
        void loadData();

        void onMicButtonClicked();
        void onCloseButtonClicked();
        void onQuestionSpeakerClicked(String questionText);
        void onHomeButtonClicked();

        void onSpeechResult(String spokenText);
        void onSpeechError(String errorReason);
        void onPermissionResult(boolean granted);

        // Dành riêng cho PronunciationView
        void onNextScriptClicked();
        void onPreviousScriptClicked();

        // Dành riêng cho InteractiveSpeakingView
        void onResponseSpeakerClicked(String responseText);
        void onWarningIconClicked(String message);

        void onDestroy();
        //
        void onAudioPermissionResult(boolean granted);
    }

    // --- LISTENER Interfaces ---
    interface QuestionListener {
        void onQuestionsLoaded(List<String> items);
        void onQuestionLoadError(String error);
    }

    interface EvaluationListener {
        void onEvaluationSuccess(String userAnswer, boolean isCorrect, String feedbackVi, String suggestionEn);
        void onEvaluationError(String userAnswer, String errorType);
    }
}
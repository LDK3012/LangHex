package com.example.langhexx.Controller;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.langhexx.Model.SpeakingContract;
import com.example.langhexx.Model.SpeakingGrammarModel;

import java.util.List;

public class SpeakingController implements SpeakingContract.Controller, SpeakingContract.QuestionListener, SpeakingContract.EvaluationListener {

    private static final String TAG = "SpeakingController";

    private SpeakingContract.Model model;
    private SpeakingContract.View view;
    private Handler handler = new Handler(Looper.getMainLooper());

    private String levelName;
    private String topicTitle;
    private boolean ttsReady = false;

    public SpeakingController(SpeakingContract.View view, Context context, String levelName, String topicTitle) {
        this.view = view;
        this.model = new SpeakingGrammarModel(context);
        this.levelName = levelName;
        this.topicTitle = topicTitle;

        if (levelName == null || topicTitle == null || levelName.isEmpty() || topicTitle.isEmpty()) {
            if (view != null) {
                view.showError("Missing Level or Topic information !");
                view.setMicButtonEnabled(false);
            } else {
                Log.e(TAG, "View is null in constructor when trying to show error for missing level/topic.");
            }
        }
    }

    @Override
    public void viewDidLoad() {
        Log.d(TAG, "Controller: View did load.");
        if (view != null) {
            view.setMicButtonEnabled(false);
        }
    }

    public void onTtsReady() {
        Log.d(TAG,"Controller: TTS is ready.");
        ttsReady = true;
        loadData();
    }

    @Override
    public void loadData() {
        if (view == null) {
            Log.e(TAG, "Controller: View is null, cannot load data.");
            return;
        }
        if (ttsReady && levelName != null && !levelName.isEmpty() && topicTitle != null && !topicTitle.isEmpty()) {
            Log.d(TAG, "Controller: Requesting model to load data for " + levelName + ", " + topicTitle);
            model.loadQuestions(levelName, topicTitle, this);
        } else if (!ttsReady) {
            Log.w(TAG,"Controller: Waiting for TTS to be ready before loading data.");
            // Optionally, show a message to the user via view.showToast or similar
        } else {
            Log.e(TAG,"Controller: Cannot load data due to missing level/topic information.");
            view.showError("Unable to load data due to missing Level or Topic information !");
            view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void onMicButtonClicked() {
        Log.d(TAG, "Controller: Mic button clicked.");
        if (view == null) {
            Log.e(TAG, "View is null in onMicButtonClicked");
            return;
        }
        Context context = view.getContext();
        if (context == null) {
            Log.e(TAG, "Context is null in onMicButtonClicked");
            view.showToast("Error: Unable to access Context!");
            return;
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            view.requestAudioPermission();
        } else {
            if (model != null && model.getCurrentQuestion() != null) {
                view.startListening();
            } else {
                view.showToast("No question to answer or the model is not ready !");
            }
        }
    }

    @Override
    public void onCloseButtonClicked() {
        Log.d(TAG, "Controller: Close button clicked.");
        if (view != null) {
            view.finishActivity();
        }
    }

    @Override
    public void onQuestionSpeakerClicked(String question) {
        Log.d(TAG, "Controller: Question speaker clicked.");
        if (view == null) return;
        if (ttsReady) {
            view.stopListening();
            view.speakText(question, "question_manual_" + (model != null ? model.getCurrentQuestionIndex() : "unknown"));
        } else {
            view.showToast("The speech function is not ready yet !");
        }
    }

    @Override
    public void onResponseSpeakerClicked(String response) {
        Log.d(TAG, "Controller: Response speaker clicked.");
        if (view == null) return;
        if (ttsReady) {
            view.stopListening();
            view.speakText(response, "response_manual_" + (model != null ? model.getCurrentQuestionIndex() : "unknown"));
        } else {
            view.showToast("The speech function is not ready yet !");
        }
    }

    @Override
    public void onWarningIconClicked(String message) {
        Log.d(TAG, "Controller: Warning icon clicked.");
        if (view != null) {
            view.showFeedbackDialog(message);
        }
    }

    @Override
    public void onSpeechResult(String spokenText) {
        Log.d(TAG, "Controller: Received speech result: " + spokenText);
        if (view == null || model == null) {
            Log.e(TAG, "View or Model is null in onSpeechResult");
            if (view != null) view.setMicButtonEnabled(true);
            return;
        }
        String currentQuestion = model.getCurrentQuestion();
        if (currentQuestion != null) {
            view.setMicButtonEnabled(false);
            model.evaluateAnswer(currentQuestion, spokenText, this);
        } else {
            Log.e(TAG,"Controller: Cannot evaluate, current question is null.");
            view.showToast("Error: Current question not found for evaluation !");
            view.setMicButtonEnabled(true);
        }
    }

    @Override
    public void onSpeechError(String errorReason) {
        Log.w(TAG, "Controller: Received speech error: " + errorReason);
        if (view == null) return;
        if (errorReason != null) {
            view.showToast(errorReason);
        }
        view.setMicButtonEnabled(true);
    }

    @Override
    public void onPermissionResult(boolean granted) {
        Log.i(TAG,"Controller: Received permission result. Granted: " + granted);
        if (view == null) return;
        if (granted) {
            view.showToast("Microphone permission granted !");
            view.setMicButtonEnabled(true);
        } else {
            view.showToast("Microphone permission is required for speech recognition !");
            view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Controller: onDestroy called.");
        if (model != null) {
            model.cleanup();
        }
        if(handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        view = null;
        model = null;
    }

    @Override
    public void onQuestionsLoaded(List<String> questions) {
        Log.i(TAG, "Controller: Questions loaded. Count: " + (questions != null ? questions.size() : "null"));
        if (view == null || model == null) return;

        String firstQuestion = model.getCurrentQuestion();
        if (firstQuestion != null) {
            view.displayQuestion(firstQuestion);
            view.setMicButtonEnabled(true);
        } else {
            Log.w(TAG, "Controller: No questions available or first question is null.");
            view.showError(questions != null && questions.isEmpty() ? "There are no questions in this exercise !" : "Unable to display the first question");
            view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void onQuestionLoadError(String error) {
        Log.e(TAG, "Controller: Question load error: " + error);
        if (view == null) return;
        view.showError(error);
        view.setMicButtonEnabled(false);
    }

    @Override
    public void onEvaluationSuccess(String userAnswer, boolean isCorrect, String feedbackVi, String suggestionEn) {
        Log.i(TAG, "Controller: Evaluation success. Correct: " + isCorrect);
        if (view == null || model == null) return;

        view.displayUserAnswer(userAnswer, isCorrect);
        view.playSound(isCorrect);

        if (isCorrect) {
            view.showWarningIcon(false, null);
            view.showCustomToast(true, "Chính xác!");

            model.advanceQuestionIndex();
            String nextQuestion = model.getCurrentQuestion();
            if (nextQuestion != null) {
                handler.postDelayed(() -> {
                    if(view != null) {
                        view.displayQuestion(nextQuestion);
                        view.setMicButtonEnabled(true);
                    }
                }, 2000);
            } else {
                handler.postDelayed(() -> {
                    if (view != null) {
                        view.showCompletionMessage();
                        view.setMicButtonEnabled(false);
                    }
                }, 1500);
            }
        } else {
            String combinedFeedback = feedbackVi != null ? feedbackVi : "The answer is incorrect !";
            if (suggestionEn != null && !suggestionEn.trim().isEmpty()) {
                combinedFeedback += "\n\n" + "Suggestion (English):\n" + suggestionEn;
            }
            view.showWarningIcon(true, combinedFeedback);
            view.showCustomToast(false, "Not correct. Check the hint and try again !");
            view.setMicButtonEnabled(true);
        }
    }

    @Override
    public void onEvaluationError(String userAnswer, String errorType) {
        Log.e(TAG, "Controller: Evaluation error: " + errorType + " for answer: " + userAnswer);
        if (view == null) return;

        view.displayUserAnswer(userAnswer, false);
        view.showWarningIcon(false, null);
        view.showToast("Evaluation error: " + errorType + ". Please try again");
        view.setMicButtonEnabled(true);
    }
}
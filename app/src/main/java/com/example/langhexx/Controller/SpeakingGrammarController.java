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

public class SpeakingGrammarController implements SpeakingContract.Controller, SpeakingContract.QuestionListener, SpeakingContract.EvaluationListener {

    private static final String TAG = "SpeakingGrammarCtrl";

    private SpeakingContract.Model model;
    private SpeakingContract.InteractiveSpeakingView view;
    private Handler handler = new Handler(Looper.getMainLooper());

    private String levelName;
    private String topicIdentifier;
    private boolean ttsReady = false;

    public SpeakingGrammarController(SpeakingContract.InteractiveSpeakingView view, Context context, String levelName, String topicIdentifier) {
        this.view = view;
        this.model = new SpeakingGrammarModel(context);
        this.levelName = levelName;
        this.topicIdentifier = topicIdentifier;

        if (levelName == null || topicIdentifier == null || levelName.isEmpty() || topicIdentifier.isEmpty()) {
            if (this.view != null) {
                this.view.showError("Error: Missing Level or Topic identifier information!");
                this.view.setMicButtonEnabled(false);
            } else {
                Log.e(TAG, "View is null in constructor for missing level/topic identifier.");
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
        if (!ttsReady) {
            Log.w(TAG,"Controller: Waiting for TTS to be ready before loading data.");
            return;
        }
        if (levelName != null && !levelName.isEmpty() && topicIdentifier != null && !topicIdentifier.isEmpty()) {
            Log.d(TAG, "Controller: Requesting model to load data for Level: " + levelName + ", Identifier: " + topicIdentifier);
            model.loadQuestions(levelName, topicIdentifier, this);
        } else {
            Log.e(TAG,"Controller: Cannot load data due to missing level/topic identifier.");
            view.showError("Error: Unable to load data due to missing Level or Topic information.");
            view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void onMicButtonClicked() {
        Log.d(TAG, "Controller: Mic button clicked.");
        if (view == null) { Log.e(TAG, "View is null in onMicButtonClicked"); return; }
        Context context = view.getContext();
        if (context == null) { Log.e(TAG, "Context is null"); view.showToast("Error: Application context not available."); return; }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            view.requestAudioPermission();
        } else {
            if (model != null && model.getCurrentQuestion() != null) {
                view.startListening();
            } else {
                view.showToast("No question to answer or model not ready.");
            }
        }
    }

    @Override
    public void onCloseButtonClicked() {
        Log.d(TAG, "Controller: Close button clicked.");
        if (view != null) view.finishActivity();
    }

    @Override
    public void onHomeButtonClicked() {
        Log.d(TAG, "Controller: Home button clicked.");
        if (view != null) {
            view.finishActivity();
        }
    }

    @Override
    public void onQuestionSpeakerClicked(String questionText) {
        Log.d(TAG, "Controller: Question speaker clicked for: " + questionText);
        if (view == null) return;
        if (ttsReady) {
            view.stopListening();
            view.speakText(questionText, "question_tts_" + (model != null ? model.getCurrentQuestionIndex() : "q_unknown"));
        } else {
            view.showToast("Speech synthesis is not ready yet.");
        }
    }

    @Override
    public void onResponseSpeakerClicked(String responseText) {
        Log.d(TAG, "Controller: Response speaker clicked for: " + responseText);
        if (view == null) return;
        if (ttsReady) {
            view.stopListening();
            view.speakText(responseText, "response_tts_" + (model != null ? model.getCurrentQuestionIndex() : "r_unknown"));
        } else {
            view.showToast("Speech synthesis is not ready yet.");
        }
    }

    @Override
    public void onWarningIconClicked(String message) {
        Log.d(TAG, "Controller: Warning icon clicked. Message: " + message);
        if (view != null) view.showFeedbackDialog(message);
    }

    @Override
    public void onSpeechResult(String spokenText) {
        Log.d(TAG, "Controller: Received speech result: \"" + spokenText + "\"");
        if (view == null || model == null) {
            Log.e(TAG, "View or Model is null in onSpeechResult");
            if (view != null) view.setMicButtonEnabled(true);
            return;
        }
        String currentQuestion = model.getCurrentQuestion();
        if (currentQuestion != null) {
            if (spokenText == null || spokenText.trim().isEmpty()) {
                view.showToast("No speech detected. Please try again.");
                view.setMicButtonEnabled(true);
                return;
            }
            view.setMicButtonEnabled(false);
            model.evaluateAnswer(currentQuestion, spokenText, this);
        } else {
            Log.e(TAG,"Controller: Cannot evaluate, current question is null.");
            view.showError("Error: Current question not found for evaluation.");
            view.setMicButtonEnabled(true);
        }
    }

    @Override
    public void onSpeechError(String errorReason) {
        Log.w(TAG, "Controller: Received speech error: " + errorReason);
        if (view == null) return;
        view.showToast(errorReason != null && !errorReason.isEmpty() ? "Speech error: " + errorReason : "An unknown speech error occurred.");
        view.setMicButtonEnabled(true);
    }

    @Override
    public void onPermissionResult(boolean granted) {
        Log.i(TAG,"Controller: Received permission result. Granted: " + granted);
        if (view == null) return;
        if (granted) {
            view.showToast("Microphone permission granted!");
            view.setMicButtonEnabled(true);
        } else {
            view.showToast("Microphone permission is required for speech input.");
            view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Controller: onDestroy called.");
        if (model != null) model.cleanup();
        if(handler != null) handler.removeCallbacksAndMessages(null);
        view = null;
        model = null;
    }

    @Override
    public void onAudioPermissionResult(boolean granted) {
        //
    }

    @Override
    public void onQuestionsLoaded(List<String> questions) {
        Log.i(TAG, "Controller: Questions loaded. Count: " + (questions != null ? questions.size() : "0"));
        if (view == null || model == null) { Log.e(TAG, "View or Model null in onQuestionsLoaded."); return; }

        String firstQuestion = model.getCurrentQuestion();
        if (firstQuestion != null) {
            view.updateUiForNewQuestion(firstQuestion);
            view.setMicButtonEnabled(true);
            if (ttsReady) {
                // view.speakText(firstQuestion, "question_auto_0");
            }
        } else {
            Log.w(TAG, "Controller: No questions available or first question is null.");
            view.showError(questions != null && questions.isEmpty() ? "No questions found for this topic." : "Error: Unable to display the first question.");
            view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void onQuestionLoadError(String error) {
        Log.e(TAG, "Controller: Question load error: " + error);
        if (view == null) return;
        view.showError("Failed to load questions: " + error);
        view.setMicButtonEnabled(false);
    }

    @Override
    public void onEvaluationSuccess(String userAnswer, boolean isCorrect, String feedbackEn, String suggestionEn) { // feedbackEn
        Log.i(TAG, "Controller: Evaluation success. Correct: " + isCorrect + ". FeedbackEN: " + feedbackEn + ". SuggestionEN: " + suggestionEn);
        if (view == null || model == null) { Log.e(TAG, "View or Model null in onEvaluationSuccess."); return; }

        view.displayUserAnswer(userAnswer, isCorrect);
        view.playSound(isCorrect);

        if (isCorrect) {
            view.showWarningIcon(false, null);
            String positiveFeedback = "Correct!";
            if (feedbackEn != null && !feedbackEn.trim().isEmpty()) {
            }
            view.showCustomToast(true, positiveFeedback);

            model.advanceQuestionIndex();
            String nextQuestion = model.getCurrentQuestion();
            if (nextQuestion != null) {
                handler.postDelayed(() -> {
                    if(view != null) {
                        view.updateUiForNewQuestion(nextQuestion);
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
            view.displayEvaluationFeedback(feedbackEn, suggestionEn);
            StringBuilder detailedMessageForIcon = new StringBuilder();
            boolean hasContentForIcon = false;
            if (feedbackEn != null && !feedbackEn.trim().isEmpty()) {
                detailedMessageForIcon.append("Explanation:\n").append(feedbackEn.trim());
                hasContentForIcon = true;
            }
            if (suggestionEn != null && !suggestionEn.trim().isEmpty()) {
                if (hasContentForIcon) detailedMessageForIcon.append("\n\n");
                detailedMessageForIcon.append("Suggested Answer:\n").append(suggestionEn.trim());
                hasContentForIcon = true;
            }
            String messageForWarningClick = hasContentForIcon ? detailedMessageForIcon.toString() : "The answer is incorrect. Please review and try again.";

            view.showWarningIcon(true, messageForWarningClick);
            view.showCustomToast(false, "Not quite right. Check the suggestion.");
            view.setMicButtonEnabled(true);
        }
    }

    @Override
    public void onEvaluationError(String userAnswer, String errorType) {
        Log.e(TAG, "Controller: Evaluation error: " + errorType + " for answer: \"" + userAnswer + "\"");
        if (view == null) return;
        view.displayUserAnswer(userAnswer, false); // Show user's answer, marked as incorrect due to error
        view.showWarningIcon(false, null);
        view.showError("Evaluation error: " + errorType + ". Please try again.");
        view.setMicButtonEnabled(true);
    }

    // --- Pronunciation specific methods from Controller interface (not used here) ---
    @Override public void onNextScriptClicked() { Log.w(TAG, "onNextScriptClicked called in Grammar controller - N/A"); }
    @Override public void onPreviousScriptClicked() { Log.w(TAG, "onPreviousScriptClicked called in Grammar controller - N/A"); }
}
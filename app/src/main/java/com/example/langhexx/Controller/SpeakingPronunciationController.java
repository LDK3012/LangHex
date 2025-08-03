package com.example.langhexx.Controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.langhexx.Model.SpeakingContract;
import com.example.langhexx.Model.SpeakingPronunciationModel;
import java.util.List;

public class SpeakingPronunciationController implements SpeakingContract.Controller, SpeakingContract.QuestionListener {

    private static final String TAG = "SpeakingVoiceCtrl";

    private SpeakingContract.Model model;
    private SpeakingContract.PronunciationView view;
    private Handler handler = new Handler(Looper.getMainLooper());

    private String levelName;
    private String topicId;
    private String topicDisplayTitle;

    private boolean hasAudioPermission = false;
    private boolean isViewRecording = false;

    public SpeakingPronunciationController(SpeakingContract.PronunciationView view, Context context, String levelName, String topicId, String topicDisplayTitle) {
        this.view = view;
        this.model = new SpeakingPronunciationModel(context);
        this.levelName = levelName;
        this.topicId = topicId;
        this.topicDisplayTitle = topicDisplayTitle;

        if (this.view == null) {
            Log.e(TAG, "View (PronunciationView) is null in constructor.");
            return;
        }

        if (levelName == null || topicId == null || levelName.isEmpty() || topicId.isEmpty()) {
            this.view.showError("Error: Missing Level or Topic ID information!");
            this.view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void viewDidLoad() {
        if (view == null) {
            Log.e(TAG, "View is null, cannot start loading data.");
            return;
        }
        if (topicDisplayTitle != null && !topicDisplayTitle.isEmpty()) {
            view.displayScreenTitle(topicDisplayTitle);
        } else {
            view.displayScreenTitle("Pronunciation Practice");
        }
        view.requestAudioPermission(); // Request permission early
        loadData();
    }

    @Override
    public void loadData() {
        if (levelName == null || topicId == null || levelName.isEmpty() || topicId.isEmpty()) {
            if (view != null) view.showError("Cannot load scripts: Missing Level or Topic ID.");
            return;
        }
        if (model == null) {
            if (view != null) view.showError("Cannot load scripts: Model not initialized.");
            return;
        }
        model.loadQuestions(levelName, topicId, this);
    }

    private void updateViewWithCurrentScriptData() {
        if (view == null || model == null) return;
        String currentScript = model.getCurrentQuestion();
        int currentIndex = model.getCurrentQuestionIndex();
        int totalCount = model.getQuestionCount();

        if (currentScript != null) {
            view.displayScriptToRepeat(currentScript);
        } else if (totalCount > 0) {
            view.showError("Error displaying current script.");
            view.displayScriptToRepeat("");
        } else {
            view.displayScriptToRepeat("No scripts available for this topic.");
        }
        view.updateScriptCounter(currentIndex + 1, totalCount);
        view.setNavigationButtonsEnabled(currentIndex > 0, currentIndex < totalCount - 1);
        view.setMicButtonEnabled(hasAudioPermission && totalCount > 0);
    }

    @Override
    public void onNextScriptClicked() {
        if (model != null && view != null) {
            if (model.getCurrentQuestionIndex() < model.getQuestionCount() - 1) {
                model.advanceQuestionIndex();
                updateViewWithCurrentScriptData();
            } else {
                if (view.getContext() != null) view.showToast("This is the last script.");
            }
        }
    }

    @Override
    public void onPreviousScriptClicked() {
        if (model != null && view != null) {
            if (model.getCurrentQuestionIndex() > 0) {
                model.previousQuestionIndex();
                updateViewWithCurrentScriptData();
            } else {
                if (view.getContext() != null) view.showToast("This is the first script.");
            }
        }
    }

    @Override
    public void onMicButtonClicked() {
        if (view == null) return;
        if (!hasAudioPermission) {
            view.showToast("Audio permission is required to record.");
            view.requestAudioPermission();
            return;
        }
        if (model == null || model.getQuestionCount() == 0) {
            view.showToast("No scripts loaded to record for.");
            return;
        }

        if (isViewRecording) {
            view.stopRecordingUI();
            isViewRecording = false;
        } else {
            view.startRecordingUI();
            isViewRecording = true;
        }
    }

    @Override
    public void onCloseButtonClicked() {
        Log.d(TAG, "Close button clicked.");
        if (view != null) view.finishActivity();
    }

    @Override
    public void onHomeButtonClicked() {
        Log.d(TAG, "Home button clicked.");
        if (view != null) view.finishActivity();
    }

    @Override
    public void onQuestionSpeakerClicked(String questionText) {
        if (view != null) view.speakText(questionText, "script_tts_" + model.getCurrentQuestionIndex());
    }

    @Override
    public void onAudioPermissionResult(boolean granted) {
        this.hasAudioPermission = granted;
        if (view != null) {
            if (granted) {
                if (model != null && model.getQuestionCount() > 0) {
                    view.setMicButtonEnabled(true);
                }
            } else {
                view.showToast("Audio permission denied.");
                view.setMicButtonEnabled(false);
            }
        }
    }

    @Override
    public void playTtsWithAzure(Context context, String text) {
        //
    }

    @Override
    public void onQuestionsLoaded(List<String> scripts) {
        Log.i(TAG, "Scripts loaded. Count: " + (scripts != null ? scripts.size() : "null"));
        if (view != null) {
            if (scripts != null && !scripts.isEmpty()) {
                updateViewWithCurrentScriptData();
            } else {
                view.showError("No scripts found for this topic.");
                view.displayScriptToRepeat("");
                view.updateScriptCounter(0, 0);
                view.setNavigationButtonsEnabled(false, false);
                view.setMicButtonEnabled(false); // No scripts, no recording
            }
        }
    }

    @Override
    public void onQuestionLoadError(String error) {
        Log.e(TAG, "Failed to load scripts: " + error);
        if (view != null) {
            view.showError("Error loading scripts: " + error);
            view.displayScriptToRepeat("");
            view.updateScriptCounter(0, 0);
            view.setNavigationButtonsEnabled(false, false);
            view.setMicButtonEnabled(false);
        }
    }

    // --- Stubs for unused methods from contract ---
    @Override public void onSpeechResult(String spokenText) { Log.d(TAG, "onSpeechResult not used here."); }
    @Override public void onSpeechError(String errorReason) { Log.w(TAG, "onSpeechError not used here."); }

    @Override
    public void onPermissionResult(boolean granted) {
        //
    }

    @Override public void onResponseSpeakerClicked(String responseText) { Log.d(TAG, "onResponseSpeakerClicked not used here."); }
    @Override public void onWarningIconClicked(String message) { Log.d(TAG, "onWarningIconClicked not used here."); }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Controller onDestroy called. Cleaning up.");
        if (model != null) model.cleanup();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (view != null && isViewRecording) {
            view.stopRecordingUI();
        }
        this.view = null;
        this.model = null;
    }
}
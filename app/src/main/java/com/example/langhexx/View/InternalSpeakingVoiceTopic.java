package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import com.example.langhexx.Controller.SpeakingVoiceController;
import com.example.langhexx.Model.SpeakingContract;
import com.example.langhexx.R;

import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentConfig;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentGradingSystem;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentGranularity;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentResult;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.WordLevelTimingResult;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamContainerFormat;
import com.microsoft.cognitiveservices.speech.audio.PullAudioInputStreamCallback;
import com.microsoft.cognitiveservices.speech.audio.PullAudioInputStream;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InternalSpeakingVoiceTopic extends AppCompatActivity implements SpeakingContract.PronunciationView {

    private static final String TAG_ACTIVITY = "InternalSpeakingVoiceTopic";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 201;

    private static final String AZURE_SPEECH_KEY = "75aMORlAm3JGJXfz0oOcHaX3hytrGyJ9MBRUfRGutW5qeZSuFjz3JQQJ99BEACYeBjFXJ3w3AAAYACOGDbeK";
    private static final String AZURE_SPEECH_REGION = "eastus";
    private static final String TARGET_LANGUAGE = "en-US";

    Button btnBackward, btnForward;
    ImageButton imgPlayAudio, imgDelete, imgRecord;
    ImageView imgClose, imgHome;
    TextView txtScreenTitle, txtScriptToRepeat, txtScriptCounter, txtTime;
    SeekBar sbrAudio;
    LinearLayout layoutPlaybackControls;
    ProgressBar pgbAzureProcessing;

    private SpeakingContract.Controller controller;
    private String levelName;
    private String topicId;
    private String topicDisplayTitleStr;

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private String currentRecordingFilePath = null;
    private boolean isCurrentlyRecording = false;
    private boolean isCurrentlyPlaying = false;
    private Handler seekBarHandler = new Handler();

    private Animation pulsatingAnimation;

    private boolean lastCanGoPreviousState = false;
    private boolean lastCanGoNextState = false;
    private String lastCleanScriptDisplayed = "";
    private int currentScriptModelIndex = -1;
    private File recordingsDir;

    private SpeechConfig azureSpeechConfig;

    private static final boolean USE_WAV_TRANSCODING_FOR_AZURE = true;
    private static final int AZURE_RECOGNITION_TIMEOUT_SECONDS = 60;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_learning);

        Intent intent = getIntent();
        levelName = intent.getStringExtra("levelName");
        topicId = intent.getStringExtra("topicId");
        topicDisplayTitleStr = intent.getStringExtra("topicTitle");

        if (AZURE_SPEECH_KEY.equals("YOUR_AZURE_SPEECH_KEY") || AZURE_SPEECH_REGION.equals("YOUR_AZURE_SPEECH_REGION")) {
            showError("CRITICAL ERROR: Please configure Azure Speech Key and Region in InternalSpeakingVoiceTopic.java.");
        }

        if (levelName == null || levelName.isEmpty() || topicId == null || topicId.isEmpty()) {
            Toast.makeText(this, "Error: Missing Level or Topic ID.", Toast.LENGTH_LONG).show();
            Log.e(TAG_ACTIVITY, "LevelName or TopicId is null or empty. Closing Activity.");
            finish();
            return;
        }

        addControls();

        this.recordingsDir = new File(getFilesDir(), "user_voice_recordings");
        if (!recordingsDir.exists()) {
            if (!recordingsDir.mkdirs()) {
                Log.e(TAG_ACTIVITY, "Cannot create recordings directory.");
                showError("Could not create storage for recordings.");
            }
        }

        try {
            azureSpeechConfig = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SPEECH_REGION);
            azureSpeechConfig.setSpeechRecognitionLanguage(TARGET_LANGUAGE);
        } catch (Exception e) {
            Log.e(TAG_ACTIVITY, "Error initializing Azure SpeechConfig: " + e.getMessage(), e);
            showError("Speech service configuration error. Check Azure Key/Region and network connection.");
            azureSpeechConfig = null;
        }

        controller = new SpeakingVoiceController(this, getApplicationContext(), levelName, topicId, topicDisplayTitleStr);
        addEvents();

        pgbAzureProcessing.setVisibility(View.GONE);
        imgRecord.setVisibility(View.VISIBLE);
        layoutPlaybackControls.setVisibility(View.GONE);
        updatePlaybackUIState(false, false);

        if (controller != null) {
            controller.viewDidLoad();
        } else {
            showError("Controller could not be initialized.");
            displayScriptToRepeat("Could not load script.");
            updateScriptCounter(0,0);
            setNavigationButtonsEnabled(false, false);
            setMicButtonEnabled(false);
        }
    }

    private void addControls() {
        btnBackward = findViewById(R.id.btnBackward);
        btnForward = findViewById(R.id.btnForward);
        imgPlayAudio = findViewById(R.id.imgButtonPlayAudio);
        imgDelete = findViewById(R.id.imgButtonDelete);
        imgRecord = findViewById(R.id.imgButtonRecord);
        imgClose = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);

        txtScreenTitle = findViewById(R.id.tvScreenTitle);
        txtScriptToRepeat = findViewById(R.id.txtScript);
        txtScriptCounter = findViewById(R.id.txtCounter);
        txtTime = findViewById(R.id.txtTime);
        sbrAudio = findViewById(R.id.sbrAudio);
        layoutPlaybackControls = findViewById(R.id.layout_playback_controls);
        pgbAzureProcessing = findViewById(R.id.pgbAzureProcessing);

        pulsatingAnimation = AnimationUtils.loadAnimation(this, R.anim.pulsating_effect);

        imgPlayAudio.setEnabled(false);
        imgDelete.setEnabled(false);
        sbrAudio.setEnabled(false);
        txtTime.setText("00:00 / 00:00");
    }

    private void addEvents() {
        imgClose.setOnClickListener(v -> {
            if (pgbAzureProcessing.getVisibility() == View.VISIBLE) {
                showToast("Processing, please wait...");
                return;
            }
            if (isCurrentlyRecording) {
                showToast("Please stop recording first.");
                return;
            }
            if (controller != null) controller.onCloseButtonClicked();
            else finishActivity();
        });

        imgHome.setOnClickListener(view -> {
            if (pgbAzureProcessing.getVisibility() == View.VISIBLE) {
                showToast("Processing, please wait...");
                return;
            }
            if (isCurrentlyRecording) {
                showToast("Please stop recording first.");
                return;
            }
            Intent intent = new Intent(InternalSpeakingVoiceTopic.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });


        btnBackward.setOnClickListener(v -> {
            if (pgbAzureProcessing.getVisibility() == View.VISIBLE) {
                showToast("Processing, please wait..."); return;
            }
            if (isCurrentlyRecording) {
                showToast("Please stop recording before switching scripts.");
                return;
            }
            if (controller != null && btnBackward.isEnabled()) {
                controller.onPreviousScriptClicked();
            }
        });

        btnForward.setOnClickListener(v -> {
            if (pgbAzureProcessing.getVisibility() == View.VISIBLE) {
                showToast("Processing, please wait..."); return;
            }
            if (isCurrentlyRecording) {
                showToast("Please stop recording before switching scripts.");
                return;
            }
            if (controller != null && btnForward.isEnabled()) {
                controller.onNextScriptClicked();
            }
        });

        imgRecord.setOnClickListener(v -> {
            if (pgbAzureProcessing.getVisibility() == View.VISIBLE) {
                showToast("Processing, please wait..."); return;
            }
            if (controller != null) controller.onMicButtonClicked();
        });

        imgPlayAudio.setOnClickListener(v -> {
            if (pgbAzureProcessing.getVisibility() == View.VISIBLE) {
                showToast("Processing, please wait..."); return;
            }
            if (currentRecordingFilePath == null || !new File(currentRecordingFilePath).exists()) {
                showToast("No recording to play.");
                checkAndLoadExistingRecording();
                return;
            }
            if (isCurrentlyPlaying) {
                pausePlayback();
            } else {
                startPlayback();
            }
        });

        imgDelete.setOnClickListener(v -> {
            if (pgbAzureProcessing.getVisibility() == View.VISIBLE) {
                showToast("Processing, please wait..."); return;
            }
            new AlertDialog.Builder(InternalSpeakingVoiceTopic.this)
                    .setTitle("Confirm Deletion")
                    .setMessage("Are you sure you want to delete this recording?")
                    .setPositiveButton("Yes", (dialog, which) -> deleteCurrentRecording())
                    .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                    .show();
        });


        sbrAudio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mediaPlayer != null && fromUser) {
                    try {
                        mediaPlayer.seekTo(progress);
                        updatePlayTimeWithMillis(progress, mediaPlayer.getDuration());
                    } catch (IllegalStateException e) {
                        Log.w(TAG_ACTIVITY, "SeekBar: MediaPlayer not ready for seekTo/getDuration: " + e.getMessage());
                    }
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    seekBarHandler.removeCallbacks(updateSeekBarRunnable);
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            seekBarHandler.post(updateSeekBarRunnable);
                        } else {
                            updatePlayTimeWithMillis(seekBar.getProgress(), mediaPlayer.getDuration());
                        }
                    } catch (IllegalStateException e) {
                        Log.w(TAG_ACTIVITY, "SeekBar StopTrack: MediaPlayer not ready: " + e.getMessage());
                    }
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (pgbAzureProcessing.getVisibility() == View.VISIBLE) {
            showToast("Processing, please wait...");
            return;
        }
        if (isCurrentlyRecording) {
            showToast("Please stop recording first.");
            return;
        }
        if (controller != null) controller.onCloseButtonClicked();
        else super.onBackPressed();
    }

    @Override
    public void displayScreenTitle(String title) {
        if (txtScreenTitle != null) txtScreenTitle.setText(title);
    }

    @Override
    public void displayScriptToRepeat(String scriptText) {
        if (txtScriptToRepeat != null) {
            if (scriptText != null) {
                this.lastCleanScriptDisplayed = scriptText;
                txtScriptToRepeat.setText(scriptText);
                txtScriptToRepeat.setTextColor(ContextCompat.getColor(this, android.R.color.black));
            } else {
                this.lastCleanScriptDisplayed = "";
                txtScriptToRepeat.setText("");
            }
        }
    }


    @Override
    public void updateScriptCounter(int currentDisplay, int total) {
        if (txtScriptCounter != null) txtScriptCounter.setText(total == 0 ? "0/0" : currentDisplay + "/" + total);
        this.currentScriptModelIndex = currentDisplay - 1;
        checkAndLoadExistingRecording();
    }

    @Override
    public void setNavigationButtonsEnabled(boolean isPreviousEnabled, boolean isNextEnabled) {
        this.lastCanGoPreviousState = isPreviousEnabled;
        this.lastCanGoNextState = isNextEnabled;
        if (!isCurrentlyRecording && (pgbAzureProcessing == null || pgbAzureProcessing.getVisibility() != View.VISIBLE)) {
            if(btnBackward != null) {
                btnBackward.setEnabled(isPreviousEnabled);
                btnBackward.setBackground(ContextCompat.getDrawable(this, isPreviousEnabled ? R.drawable.bg_button_next_rounded : R.drawable.bg_button_previous_rounded));
            }
            if(btnForward != null) {
                btnForward.setEnabled(isNextEnabled);
                btnForward.setBackground(ContextCompat.getDrawable(this, isNextEnabled ? R.drawable.bg_button_next_rounded : R.drawable.bg_button_previous_rounded));
            }
        }
    }

    @Override
    public void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG_ACTIVITY, "Error displayed to user: " + message);
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showCustomToast(boolean success, String message) {
        showToast((success ? "Success: " : "Failure: ") + message);
    }

    @Override
    public void setMicButtonEnabled(boolean enabled) {
        if (imgRecord != null) {
            boolean isAzureBusy = pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE;
            if (!isCurrentlyRecording && !isAzureBusy) {
                imgRecord.setEnabled(enabled);
                imgRecord.setAlpha(enabled ? 1.0f : 0.5f);
            } else {
                imgRecord.setEnabled(false);
                imgRecord.setAlpha(0.5f);
            }
        }
    }

    @Override
    public void playSound(boolean isCorrect) {  }

    @Override
    public void speakText(String text, String utteranceId) {
        Log.d(TAG_ACTIVITY, "TTS request (not fully implemented in View): " + text);
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void finishActivity() {
        Log.d(TAG_ACTIVITY, "finishActivity called.");
        cleanUpAudioResources();
        super.finish();
    }


    @Override
    public void requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION_CODE);
        } else {
            if (controller != null) controller.onAudioPermissionResult(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION_CODE) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (controller != null) {
                controller.onAudioPermissionResult(granted);
            }
            if (!granted) {
                showError("Recording permission denied. This feature cannot be used.");
                setMicButtonEnabled(false);
            }
        }
    }

    private String generatePersistentFilePath() {
        if (levelName == null || topicId == null || currentScriptModelIndex < 0 || recordingsDir == null ) {
            Log.e(TAG_ACTIVITY, "Cannot generate file path: missing info. Level: " + levelName + ", Topic: " + topicId + ", Index: " + currentScriptModelIndex);
            return null;
        }
        if (!recordingsDir.exists()) {
            if (!recordingsDir.mkdirs()) {
                Log.e(TAG_ACTIVITY, "Cannot create recordings directory during generatePersistentFilePath.");
                showError("Storage error: could not create storage directory.");
                return null;
            }
        }
        String safeLevelName = levelName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String safeTopicId = topicId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String filename = "rec_" + safeLevelName + "_" + safeTopicId + "_" + currentScriptModelIndex + ".3gp";
        return new File(recordingsDir, filename).getAbsolutePath();
    }

    private void checkAndLoadExistingRecording() {
        if (isCurrentlyRecording || (pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE) ) {
            Log.d(TAG_ACTIVITY, "checkAndLoadExistingRecording: currently recording or Azure processing, skipping.");
            return;
        }

        String persistentPath = generatePersistentFilePath();
        Log.d(TAG_ACTIVITY, "checkAndLoadExistingRecording: Checking for script index " + currentScriptModelIndex + ", path: " + persistentPath);

        pausePlayback();
        releaseMediaPlayer();

        this.currentRecordingFilePath = null;
        updatePlaybackUIState(false, false);

        if (persistentPath != null) {
            File existingRecording = new File(persistentPath);
            if (existingRecording.exists() && existingRecording.length() > 0) {
                Log.i(TAG_ACTIVITY, "Found existing recording for script index " + currentScriptModelIndex + ": " + persistentPath);
                this.currentRecordingFilePath = persistentPath;
                restoreOriginalScriptText();
                prepareMediaPlayerForPlayback();
            } else {
                Log.i(TAG_ACTIVITY, "No existing recording for script index " + currentScriptModelIndex +
                        (existingRecording.exists() ? " (empty file, will be deleted)" : " (file does not exist)"));
                if (existingRecording.exists() && existingRecording.length() == 0) {
                    existingRecording.delete();
                }
                this.currentRecordingFilePath = null;
                restoreOriginalScriptText();
            }
        } else {
            Log.w(TAG_ACTIVITY, "checkAndLoadExistingRecording: Could not generate persistentPath.");
            this.currentRecordingFilePath = null;
            restoreOriginalScriptText();
        }
    }


    @Override
    public void startRecordingUI() {
        if (isCurrentlyRecording) {
            showToast("Currently recording...");
            return;
        }
        if (pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE) {
            showToast("Analysis in progress, please wait.");
            return;
        }

        restoreOriginalScriptText();

        String newRecordingPath = generatePersistentFilePath();
        if (newRecordingPath == null) {
            showError("Could not determine recording path. Cannot start recording.");
            return;
        }

        pausePlayback();
        releaseMediaPlayer();
        updatePlaybackUIState(false, false);

        File oldFileForThisScript = new File(newRecordingPath);
        if (oldFileForThisScript.exists()) {
            if (oldFileForThisScript.delete()) {
                Log.i(TAG_ACTIVITY, "Deleted old recording to record new: " + newRecordingPath);
            } else {
                Log.w(TAG_ACTIVITY, "Could not delete old recording: " + newRecordingPath + ". Overwriting if possible.");
            }
        }
        this.currentRecordingFilePath = newRecordingPath;

        if (recordingsDir == null || !recordingsDir.exists()) {
            this.recordingsDir = new File(getFilesDir(), "user_voice_recordings");
            if (!recordingsDir.exists() && !recordingsDir.mkdirs()) {
                showError("Error creating storage directory. Cannot record.");
                this.currentRecordingFilePath = null;
                return;
            }
        }

        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);


            mediaRecorder.setOutputFile(this.currentRecordingFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isCurrentlyRecording = true;
            updateRecordingUIState(true);
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG_ACTIVITY, "MediaRecorder prepare() or start() failed for " + this.currentRecordingFilePath, e);
            showError("Recording failed: " + e.getMessage());
            File failedFile = new File(this.currentRecordingFilePath);
            if (failedFile.exists()) {
                failedFile.delete();
            }
            this.currentRecordingFilePath = null;
            isCurrentlyRecording = false;
            releaseMediaRecorder();
            updateRecordingUIState(false);
        }
    }


    @Override
    public void stopRecordingUI() {
        if (!isCurrentlyRecording || mediaRecorder == null) {
            Log.d(TAG_ACTIVITY, "Stop recording called but not currently recording or mediaRecorder is null.");
            String persistentPath = generatePersistentFilePath();
            if (persistentPath != null && new File(persistentPath).exists() && new File(persistentPath).length() > 0) {
                if(this.currentRecordingFilePath == null || !this.currentRecordingFilePath.equals(persistentPath) || mediaPlayer == null){
                    this.currentRecordingFilePath = persistentPath;
                    prepareMediaPlayerForPlayback();
                }
                updatePlaybackUIState(false, true);
            } else {
                this.currentRecordingFilePath = null;
                updatePlaybackUIState(false, false);
            }
            isCurrentlyRecording = false;
            updateRecordingUIState(false);
            return;
        }

        String savedFilePath = this.currentRecordingFilePath;

        try {
            mediaRecorder.stop();
            Log.d(TAG_ACTIVITY, "MediaRecorder stopped successfully. File: " + savedFilePath);
            File checkFile = new File(savedFilePath);
            if(!checkFile.exists() || checkFile.length() == 0){
                Log.e(TAG_ACTIVITY, "MediaRecorder.stop() successful but file does not exist or is empty: " + savedFilePath);
                throw new RuntimeException("File not saved correctly after stop for path: "+savedFilePath);
            }
        } catch (RuntimeException stopException) {
            Log.e(TAG_ACTIVITY, "MediaRecorder stop() failed: " + stopException.getMessage() + " for file " + savedFilePath, stopException);
            showError("Failed to save recording. Please try again.");
            if (savedFilePath != null) {
                File problematicFile = new File(savedFilePath);
                if (problematicFile.exists() && problematicFile.delete()) {
                    Log.d(TAG_ACTIVITY, "Deleted problematic recording file: " + savedFilePath);
                }
            }
            this.currentRecordingFilePath = null;
            savedFilePath = null;
        } finally {
            releaseMediaRecorder();
            isCurrentlyRecording = false;
            updateRecordingUIState(false);

            if (savedFilePath != null && new File(savedFilePath).exists() && new File(savedFilePath).length() > 0) {
                this.currentRecordingFilePath = savedFilePath;
                prepareMediaPlayerForPlayback();
                if (azureSpeechConfig != null) {
                    processRecordingWithAzure(this.currentRecordingFilePath);
                } else {
                    showError("Azure service is not ready. Cannot analyze.");
                    restoreOriginalScriptText();
                }
            } else {
                Log.w(TAG_ACTIVITY, "Invalid recording file after stop: " + savedFilePath + ". Cannot play or analyze.");
                this.currentRecordingFilePath = null;
                updatePlaybackUIState(false, false);
                restoreOriginalScriptText();
            }
        }
    }


    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG_ACTIVITY, "Error releasing MediaRecorder: " + e.getMessage());
            }
            mediaRecorder = null;
        }
    }

    private void prepareMediaPlayerForPlayback() {
        if (currentRecordingFilePath == null || !new File(currentRecordingFilePath).exists() || new File(currentRecordingFilePath).length() == 0) {
            Log.d(TAG_ACTIVITY, "prepareMediaPlayerForPlayback: No valid recording file: " + currentRecordingFilePath);
            if(this.currentRecordingFilePath != null) {
                File f = new File(this.currentRecordingFilePath);
                if (f.exists() && f.length() == 0) {
                    Log.w(TAG_ACTIVITY, "Deleting empty file during prepareMediaPlayer: " + this.currentRecordingFilePath);
                    f.delete();
                }
            }
            this.currentRecordingFilePath = null;
            updatePlaybackUIState(false, false);
            return;
        }

        releaseMediaPlayer();

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(currentRecordingFilePath);
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG_ACTIVITY, "MediaPlayer prepared. Duration: " + mp.getDuration() + " ms for " + currentRecordingFilePath);
                if (mp.getDuration() <= 0) {
                    Log.e(TAG_ACTIVITY, "MediaPlayer prepared with invalid duration: " + mp.getDuration() + ". File may be corrupted.");
                    showError("Cannot play recording, file may be corrupted.");
                    releaseMediaPlayer();
                    this.currentRecordingFilePath = null;
                    updatePlaybackUIState(false, false);
                    return;
                }
                if (sbrAudio != null) sbrAudio.setMax(mp.getDuration());
                updatePlayTimeWithMillis(0, mp.getDuration());
                updatePlaybackUIState(false, true);
                if (imgPlayAudio != null) imgPlayAudio.setEnabled(true);
                if (sbrAudio != null) sbrAudio.setEnabled(true);
                if (imgDelete != null) imgDelete.setEnabled(true);
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG_ACTIVITY, "MediaPlayer playback completed.");
                isCurrentlyPlaying = false;
                try {
                    if (mediaPlayer != null && mediaPlayer.getDuration() > 0) {
                        updatePlayTimeWithMillis(mediaPlayer.getDuration(), mediaPlayer.getDuration());
                        if (sbrAudio != null) sbrAudio.setProgress(sbrAudio.getMax());
                    } else {
                        updatePlayTimeWithMillis(0,0);
                        if (sbrAudio != null) sbrAudio.setProgress(0);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG_ACTIVITY, "MediaPlayer state error on completion: " + e.getMessage());
                    updatePlayTimeWithMillis(0,0);
                    if (sbrAudio != null) sbrAudio.setProgress(0);
                }
                updatePlaybackUIState(false, true);
                if(seekBarHandler != null) seekBarHandler.removeCallbacks(updateSeekBarRunnable);
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG_ACTIVITY, "MediaPlayer error: what=" + what + ", extra=" + extra + " for " + currentRecordingFilePath);
                showError("Error playing audio (what=" + what + "). File may be corrupted.");
                releaseMediaPlayer();
                boolean fileStillExists = currentRecordingFilePath != null && new File(currentRecordingFilePath).exists();
                updatePlaybackUIState(false, fileStillExists);
                return true;
            });
            mediaPlayer.prepareAsync();
            Log.d(TAG_ACTIVITY, "MediaPlayer.prepareAsync() called for: " + currentRecordingFilePath);
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            Log.e(TAG_ACTIVITY, "MediaPlayer setDataSource or prepareAsync failed for "+currentRecordingFilePath, e);
            showError("Could not load audio for playback: " + e.getMessage());
            releaseMediaPlayer();
            updatePlaybackUIState(false, false);
        }
    }


    private void startPlayback() {
        if (mediaPlayer == null || currentRecordingFilePath == null || !new File(currentRecordingFilePath).exists() || new File(currentRecordingFilePath).length() == 0) {
            if (currentRecordingFilePath != null && new File(currentRecordingFilePath).exists() && mediaPlayer == null) {
                Log.d(TAG_ACTIVITY, "startPlayback: MediaPlayer null but file exists, re-preparing...");
                prepareMediaPlayerForPlayback();
                showToast("Preparing audio...");
                return;
            } else {
                showToast("No valid recording to play.");
                if (currentRecordingFilePath != null && new File(currentRecordingFilePath).exists() && new File(currentRecordingFilePath).length() == 0) {
                    Log.w(TAG_ACTIVITY, "Attempted to play zero-length file, deleting: " + currentRecordingFilePath);
                    File f = new File(currentRecordingFilePath); if(f.exists()) f.delete();
                    this.currentRecordingFilePath = null;
                }
                updatePlaybackUIState(false, false);
                return;
            }
        }

        boolean isPrepared = false;
        try {
            mediaPlayer.getDuration();
            isPrepared = true;
        } catch (IllegalStateException e) {
            Log.w(TAG_ACTIVITY, "startPlayback: MediaPlayer not ready (IllegalStateException on getDuration). File: " + currentRecordingFilePath, e);
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                showToast("Audio not ready. Please try again in a moment.");
            } else if (mediaPlayer == null) {
                prepareMediaPlayerForPlayback();
            }
            return;
        }


        if (isPrepared && !mediaPlayer.isPlaying()) {
            try {
                int currentSeekBarPos = (sbrAudio != null) ? sbrAudio.getProgress() : 0;
                if (currentSeekBarPos >= mediaPlayer.getDuration()) currentSeekBarPos = 0;
                mediaPlayer.seekTo(currentSeekBarPos);

                mediaPlayer.start();
                isCurrentlyPlaying = true;
                updatePlaybackUIState(true, true);
                if (seekBarHandler != null) seekBarHandler.post(updateSeekBarRunnable);
                Log.d(TAG_ACTIVITY, "Started playback from position " + currentSeekBarPos);
            } catch (IllegalStateException e) {
                Log.e(TAG_ACTIVITY, "MediaPlayer.start() failed (IllegalStateException): " + e.getMessage() + ". File: " + currentRecordingFilePath, e);
                showError("Playback failed. Audio may be corrupted or still preparing.");
                releaseMediaPlayer();
                prepareMediaPlayerForPlayback();
            }
        } else if (!isPrepared) {
            showToast("Audio is still preparing, please wait...");
        } else if (mediaPlayer.isPlaying()) {
            Log.d(TAG_ACTIVITY, "Already playing.");
        }
    }


    private void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
                isCurrentlyPlaying = false;
                updatePlaybackUIState(false, true);
                if(seekBarHandler != null) seekBarHandler.removeCallbacks(updateSeekBarRunnable);
                Log.d(TAG_ACTIVITY, "Paused playback.");
            } catch (IllegalStateException e) {
                Log.e(TAG_ACTIVITY, "MediaPlayer.pause() failed: " + e.getMessage());
            }
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception e){
                Log.e(TAG_ACTIVITY, "Error releasing MediaPlayer: " + e.getMessage());
            }
            mediaPlayer = null;
        }
        isCurrentlyPlaying = false;
        if(seekBarHandler != null) seekBarHandler.removeCallbacksAndMessages(null);
    }


    private void deleteCurrentRecording() {
        pausePlayback();
        releaseMediaPlayer();

        String pathToDelete = this.currentRecordingFilePath;
        if (pathToDelete == null) {
            pathToDelete = generatePersistentFilePath();
        }

        this.currentRecordingFilePath = null;

        if (pathToDelete != null) {
            File file = new File(pathToDelete);
            if (file.exists()) {
                if (file.delete()) {
                    Log.i(TAG_ACTIVITY, "Recording file deleted: " + pathToDelete);
                    showToast("Recording deleted.");
                } else {
                    showError("Could not delete recording file: " + file.getName());
                    Log.e(TAG_ACTIVITY, "Could not delete recording file: " + pathToDelete);
                }
            } else {
                Log.d(TAG_ACTIVITY, "Recording file not found to delete: " + pathToDelete);
            }
        } else {
            Log.w(TAG_ACTIVITY, "deleteCurrentRecording: No valid file path to delete.");
        }

        isCurrentlyPlaying = false;
        updatePlaybackUIState(false, false);
        restoreOriginalScriptText();

        if (sbrAudio != null) {
            sbrAudio.setProgress(0);
            sbrAudio.setMax(100);
        }
        if (txtTime != null) txtTime.setText("00:00 / 00:00");
    }


    @Override
    public void updateRecordingUIState(boolean isRecordingActive) {
        this.isCurrentlyRecording = isRecordingActive;
        boolean isAzureProcessing = pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE;

        if (isRecordingActive) {
            imgRecord.setImageResource(R.drawable.voice_icon_pause_);
            if (pulsatingAnimation != null && (imgRecord.getAnimation() == null || !imgRecord.getAnimation().hasStarted() || imgRecord.getAnimation().hasEnded())) {
                imgRecord.startAnimation(pulsatingAnimation);
            }
            imgRecord.setVisibility(View.VISIBLE);
            layoutPlaybackControls.setVisibility(View.GONE);

            if(btnBackward != null) btnBackward.setEnabled(false);
            if(btnForward != null) btnForward.setEnabled(false);
            if(imgClose != null) imgClose.setEnabled(false);
            if(imgHome != null) imgHome.setEnabled(false);
            if(imgDelete != null) imgDelete.setEnabled(false);
            if(imgPlayAudio != null) imgPlayAudio.setEnabled(false);

        } else {
            imgRecord.setImageResource(R.drawable.micro);
            if (pulsatingAnimation != null && imgRecord.getAnimation() != null) {
                imgRecord.clearAnimation();
            }

            boolean hasFile = this.currentRecordingFilePath != null && new File(this.currentRecordingFilePath).exists() && new File(this.currentRecordingFilePath).length() > 0;
            updatePlaybackUIState(this.isCurrentlyPlaying, hasFile);

            if (!isAzureProcessing) {
                if (imgClose != null) imgClose.setEnabled(true);
                if (imgHome != null) imgHome.setEnabled(true);

                if (btnBackward != null) {
                    btnBackward.setEnabled(this.lastCanGoPreviousState);
                    btnBackward.setBackground(ContextCompat.getDrawable(this, this.lastCanGoPreviousState ? R.drawable.bg_button_next_rounded : R.drawable.bg_button_previous_rounded));
                }
                if (btnForward != null) {
                    btnForward.setEnabled(this.lastCanGoNextState);
                    btnForward.setBackground(ContextCompat.getDrawable(this, this.lastCanGoNextState ? R.drawable.bg_button_next_rounded : R.drawable.bg_button_previous_rounded));
                }
                if(imgRecord != null) imgRecord.setEnabled(true);
                imgRecord.setAlpha(1.0f);
            } else {
                if(imgRecord != null) imgRecord.setEnabled(false);
                imgRecord.setAlpha(0.5f);
                if (btnBackward != null) btnBackward.setEnabled(false);
                if (btnForward != null) btnForward.setEnabled(false);
                if (imgClose != null) imgClose.setEnabled(false);
                if (imgHome != null) imgHome.setEnabled(false);
            }
        }
    }


    @Override
    public void updatePlaybackUIState(boolean isPlaying, boolean hasRecording) {
        this.isCurrentlyPlaying = isPlaying;
        boolean isAzureProcessing = pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE;

        if (hasRecording && !isCurrentlyRecording) {
            if (imgRecord != null) {
                if (imgRecord.getAnimation() != null) imgRecord.clearAnimation();
                imgRecord.setVisibility(View.GONE);
            }
            if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.VISIBLE);

            boolean canInteractWithPlayback = !isAzureProcessing && mediaPlayer != null;

            if (imgPlayAudio != null) {
                imgPlayAudio.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.icon_play_audio);
                imgPlayAudio.setEnabled(canInteractWithPlayback);
                imgPlayAudio.setAlpha(canInteractWithPlayback ? 1.0f : 0.5f);
            }
            if (imgDelete != null) {
                imgDelete.setEnabled(!isAzureProcessing && hasRecording);
                imgDelete.setAlpha(isAzureProcessing ? 0.5f : 1.0f);
            }
            if (sbrAudio != null) {
                sbrAudio.setEnabled(canInteractWithPlayback);
            }
        } else {
            if (imgRecord != null) {
                if (isCurrentlyRecording) {
                    imgRecord.setImageResource(R.drawable.voice_icon_pause_);
                    if (pulsatingAnimation != null && (imgRecord.getAnimation() == null || !imgRecord.getAnimation().hasStarted() || imgRecord.getAnimation().hasEnded())) {
                        imgRecord.startAnimation(pulsatingAnimation);
                    }
                } else {
                    imgRecord.setImageResource(R.drawable.micro);
                    if (imgRecord.getAnimation() != null) imgRecord.clearAnimation();
                }
                imgRecord.setVisibility(View.VISIBLE);
                imgRecord.setEnabled(!isAzureProcessing);
                imgRecord.setAlpha(isAzureProcessing ? 0.5f : 1.0f);
            }
            if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.GONE);

            if (imgPlayAudio != null) {
                imgPlayAudio.setEnabled(false);
                imgPlayAudio.setImageResource(R.drawable.icon_play_audio);
            }
            if (imgDelete != null) imgDelete.setEnabled(false);
            if (sbrAudio != null) {
                sbrAudio.setEnabled(false);
                sbrAudio.setProgress(0);
                sbrAudio.setMax(100);
            }
            if (txtTime != null) txtTime.setText("00:00 / 00:00");
        }
    }


    @Override
    public void updateSeekBarProgress(int progress, int max) {
        if (sbrAudio != null) {
            sbrAudio.setMax(max > 0 ? max : 100);
            int validatedProgress = Math.max(0, Math.min(progress, sbrAudio.getMax()));
            sbrAudio.setProgress(validatedProgress);
        }
    }

    @Override
    public void updatePlayTime(String currentTime, String totalTime) {
        if (txtTime != null) {
            txtTime.setText(String.format(Locale.getDefault(), "%s / %s", currentTime, totalTime));
        }
    }

    private void updatePlayTimeWithMillis(long currentMillis, long totalMillis) {
        long validTotalMillis = Math.max(0, totalMillis);
        long validCurrentMillis = Math.max(0, Math.min(currentMillis, validTotalMillis));

        String currentTimeStr = String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(validCurrentMillis),
                TimeUnit.MILLISECONDS.toSeconds(validCurrentMillis) % 60
        );
        String totalTimeStr = String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(validTotalMillis),
                TimeUnit.MILLISECONDS.toSeconds(validTotalMillis) % 60
        );
        updatePlayTime(currentTimeStr, totalTimeStr);

        if (sbrAudio != null) {
            if (mediaPlayer != null && validTotalMillis > 0) {
                try {
                    if (!sbrAudio.isPressed() || !mediaPlayer.isPlaying()) {
                        if(sbrAudio.getMax() != (int)validTotalMillis) sbrAudio.setMax((int)validTotalMillis);
                        sbrAudio.setProgress((int) validCurrentMillis);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG_ACTIVITY, "updatePlayTimeWithMillis: MediaPlayer not ready when updating SeekBar.");
                    sbrAudio.setMax(100); sbrAudio.setProgress(0);
                }
            } else if (validTotalMillis <= 0) {
                sbrAudio.setMax(100); sbrAudio.setProgress(0);
            }
        }
    }


    private Runnable updateSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isCurrentlyPlaying) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        int duration = mediaPlayer.getDuration();

                        if (duration <= 0) {
                            Log.e(TAG_ACTIVITY, "Error: duration <= 0 while playing in updateSeekBarRunnable. Stopping playback.");
                            pausePlayback();
                            updatePlayTimeWithMillis(0,0);
                            return;
                        }
                        if (sbrAudio != null && !sbrAudio.isPressed()) {
                            if(sbrAudio.getMax() != duration) sbrAudio.setMax(duration);
                            sbrAudio.setProgress(currentPosition);
                        }
                        updatePlayTimeWithMillis(currentPosition, duration);
                        if (seekBarHandler != null) seekBarHandler.postDelayed(this, 250);
                    } else if (isCurrentlyPlaying) {
                        Log.w(TAG_ACTIVITY, "updateSeekBarRunnable: isCurrentlyPlaying=true but mediaPlayer not playing. Rescheduling check.");
                        if (seekBarHandler != null) seekBarHandler.postDelayed(this, 500);
                    } else {
                        if (seekBarHandler != null) seekBarHandler.removeCallbacks(this);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG_ACTIVITY, "MediaPlayer in invalid state while updating seekbar: " + e.getMessage());
                    if(isCurrentlyPlaying && seekBarHandler != null) {
                        seekBarHandler.postDelayed(this, 1000);
                    } else if (seekBarHandler != null) {
                        seekBarHandler.removeCallbacks(this);
                    }
                } catch (Exception e) {
                    Log.e(TAG_ACTIVITY, "Exception in updateSeekBarRunnable: " + e.getMessage(), e);
                    if (seekBarHandler != null) seekBarHandler.removeCallbacks(this);
                }
            } else {
                if (seekBarHandler != null) seekBarHandler.removeCallbacks(this);
            }
        }
    };


    private void cleanUpAudioResources() {
        Log.d(TAG_ACTIVITY, "Cleaning up audio resources...");
        if (seekBarHandler != null) {
            seekBarHandler.removeCallbacksAndMessages(null);
        }
        releaseMediaPlayer();
        releaseMediaRecorder();
        if (imgRecord != null && pulsatingAnimation != null && imgRecord.getAnimation() != null) {
            if (imgRecord.getAnimation().hasStarted() && !imgRecord.getAnimation().hasEnded()){
                imgRecord.clearAnimation();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG_ACTIVITY, "onStop called.");
        if (isCurrentlyRecording && mediaRecorder != null) {
            Log.w(TAG_ACTIVITY, "Currently recording onStop. Auto-stopping and saving.");
            stopRecordingUI();
        }
        if (isCurrentlyPlaying && mediaPlayer != null) {
            Log.d(TAG_ACTIVITY, "Currently playing onStop. Pausing playback.");
            pausePlayback();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG_ACTIVITY, "onDestroy Activity called.");
        cleanUpAudioResources();

        if (azureSpeechConfig != null) {
            try {
                azureSpeechConfig.close();
                Log.d(TAG_ACTIVITY, "Azure SpeechConfig closed.");
            } catch (Exception e) {
                Log.e(TAG_ACTIVITY, "Error closing Azure SpeechConfig: " + e.getMessage());
            }
            azureSpeechConfig = null;
        }
        if (controller != null) {
            controller.onDestroy();
            controller = null;
        }
        if (pulsatingAnimation != null) {
            pulsatingAnimation.cancel();
            pulsatingAnimation = null;
        }
    }


    @Override
    public String getRecordingFilePath() {
        return currentRecordingFilePath;
    }

    @Override
    public void setRecordingFilePath(String path) {
        Log.d(TAG_ACTIVITY, "setRecordingFilePath called with path: " + path);
        String oldPath = this.currentRecordingFilePath;
        this.currentRecordingFilePath = path;

        if (path != null && new File(path).exists() && new File(path).length() > 0) {
            if (!path.equals(oldPath) || mediaPlayer == null) {
                pausePlayback();
                releaseMediaPlayer();
                prepareMediaPlayerForPlayback();
            }
        } else {
            Log.w(TAG_ACTIVITY, "setRecordingFilePath: path null or file does not exist/is empty. Releasing player.");
            if (path == null && oldPath != null) {
                Log.d(TAG_ACTIVITY, "setRecordingFilePath: new path is null, old path was: " + oldPath);
            }
            pausePlayback();
            releaseMediaPlayer();
            updatePlaybackUIState(false, false);
            restoreOriginalScriptText();
        }
    }

    private void setUiInteraction(boolean allowInteraction) {
        boolean isAzureProcessing = pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE;
        boolean effectiveAllowInteraction = allowInteraction && !isAzureProcessing;

        if(imgRecord != null) {
            imgRecord.setEnabled(effectiveAllowInteraction && !isCurrentlyRecording);
            imgRecord.setAlpha((effectiveAllowInteraction && !isCurrentlyRecording) ? 1.0f : 0.5f);
        }

        boolean hasFileForPlayback = currentRecordingFilePath != null && new File(currentRecordingFilePath).exists() && new File(currentRecordingFilePath).length() > 0;
        boolean canPlayback = effectiveAllowInteraction && hasFileForPlayback && mediaPlayer != null;

        if(imgPlayAudio != null) {
            imgPlayAudio.setEnabled(canPlayback);
            imgPlayAudio.setAlpha(canPlayback ? 1.0f : 0.5f);
        }
        if(imgDelete != null) {
            imgDelete.setEnabled(effectiveAllowInteraction && hasFileForPlayback);
            imgDelete.setAlpha((effectiveAllowInteraction && hasFileForPlayback) ? 1.0f : 0.5f);
        }
        if(sbrAudio != null) {
            sbrAudio.setEnabled(canPlayback);
        }

        if(btnBackward != null) {
            btnBackward.setEnabled(effectiveAllowInteraction && lastCanGoPreviousState);
        }
        if(btnForward != null) {
            btnForward.setEnabled(effectiveAllowInteraction && lastCanGoNextState);
        }

        if(imgClose != null) imgClose.setEnabled(effectiveAllowInteraction);
        if(imgHome != null) imgHome.setEnabled(effectiveAllowInteraction);
    }

    private void restoreOriginalScriptText() {
        if (txtScriptToRepeat != null && this.lastCleanScriptDisplayed != null && !this.lastCleanScriptDisplayed.isEmpty()) {
            txtScriptToRepeat.setText(this.lastCleanScriptDisplayed);
            txtScriptToRepeat.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }
    }

    private void processRecordingWithAzure(String audioFilePath) {
        if (azureSpeechConfig == null) {
            showError("Azure speech service is not configured. Cannot analyze.");
            restoreOriginalScriptText();
            return;
        }
        if (audioFilePath == null || !new File(audioFilePath).exists() || new File(audioFilePath).length() == 0) {
            showError("Invalid or empty recording file. Cannot analyze.");
            restoreOriginalScriptText();
            return;
        }

        final String referenceTextCleaned = this.lastCleanScriptDisplayed
                .replaceAll("[\\p{Punct}&&[^'-]]", "")
                .toLowerCase(Locale.ROOT)
                .trim();

        if (referenceTextCleaned.isEmpty()) {
            showError("Reference text is empty. Cannot perform pronunciation assessment.");
            restoreOriginalScriptText();
            return;
        }

        runOnUiThread(() -> {
            Log.d(TAG_ACTIVITY, "processRecordingWithAzure: Showing ProgressBar and disabling UI.");
            if (pgbAzureProcessing != null) pgbAzureProcessing.setVisibility(View.VISIBLE);
            setUiInteraction(false);
            showToast("Preparing and analyzing pronunciation with Azure...");
            restoreOriginalScriptText();
        });

        CompletableFuture.runAsync(() -> {
            MyPullAudioInputStreamCallback pullStreamCallback = null;
            PullAudioInputStream pullAudioInputStream = null;
            AudioConfig audioConfig = null;
            SpeechRecognizer recognizer = null;
            SpeechRecognitionResult speechResult = null;

            String recognizedText = "";
            PronunciationAssessmentResult pronunciationAssessmentResult = null;
            String errorForUi = null;
            String azureDetailedError = "";

            String effectiveAudioFilePath = audioFilePath;
            String tempWavFilePath = null;
            int actualSampleRateOfWav = 8000;

            if (USE_WAV_TRANSCODING_FOR_AZURE) {
                Log.i(TAG_ACTIVITY, "Starting transcoding to WAV for file: " + audioFilePath);
                tempWavFilePath = transcodeToWav(audioFilePath);

                if (tempWavFilePath != null && new File(tempWavFilePath).exists()) {
                    effectiveAudioFilePath = tempWavFilePath;
                    Log.i(TAG_ACTIVITY, "Successfully transcoded to WAV: " + effectiveAudioFilePath);
                    actualSampleRateOfWav = 8000;
                    Log.w(TAG_ACTIVITY, "processRecordingWithAzure: Assuming sample rate of WAV file is " + actualSampleRateOfWav + "Hz. " +
                            "If the original .3gp file is AMR-NB (8kHz) and transcodeToWav doesn't resample, this is correct. " +
                            "For optimal Azure performance, WAV should be 16kHz mono.");
                } else {
                    Log.w(TAG_ACTIVITY, "Transcoding to WAV failed. Using original file: " + audioFilePath + ". Analysis quality may be affected.");
                }
            }

            try {
                File audioFile = new File(effectiveAudioFilePath);
                if (!audioFile.exists() || audioFile.length() == 0) {
                    throw new FileNotFoundException("Audio file does not exist or is empty after check/transcode: " + effectiveAudioFilePath);
                }
                Log.i(TAG_ACTIVITY, "Azure Processing: Starting to process file: " + effectiveAudioFilePath + ", Size: " + audioFile.length() + " bytes");

                pullStreamCallback = new MyPullAudioInputStreamCallback(effectiveAudioFilePath);
                AudioStreamFormat streamFormat;

                if (USE_WAV_TRANSCODING_FOR_AZURE && tempWavFilePath != null && effectiveAudioFilePath.equals(tempWavFilePath)) {
                    short channelsForWav = 1;
                    short bitDepthForWav = 16;
                    Log.i(TAG_ACTIVITY, "Azure Processing: Using WAV PCM format. SampleRate (for SDK): " + actualSampleRateOfWav);
                    streamFormat = AudioStreamFormat.getWaveFormatPCM(actualSampleRateOfWav, bitDepthForWav, channelsForWav);
                } else {
                    streamFormat = AudioStreamFormat.getCompressedFormat(AudioStreamContainerFormat.ANY);
                    Log.i(TAG_ACTIVITY, "Azure Processing: Using COMPRESSED AudioStreamFormat (ANY) for " + effectiveAudioFilePath);
                }

                pullAudioInputStream = PullAudioInputStream.create(pullStreamCallback, streamFormat);
                audioConfig = AudioConfig.fromStreamInput(pullAudioInputStream);

                if (audioConfig == null) {
                    throw new IllegalStateException("AudioConfig is null after creation. Issue with stream or format for: " + effectiveAudioFilePath);
                }

                recognizer = new SpeechRecognizer(azureSpeechConfig, TARGET_LANGUAGE, audioConfig);

                Log.i(TAG_ACTIVITY, "Azure Processing: Reference text for assessment: '" + referenceTextCleaned + "'");
                PronunciationAssessmentConfig pronunciationConfig =
                        new PronunciationAssessmentConfig(referenceTextCleaned,
                                PronunciationAssessmentGradingSystem.HundredMark,
                                PronunciationAssessmentGranularity.Word, true);
                pronunciationConfig.applyTo(recognizer);

                Log.i(TAG_ACTIVITY, "Azure Processing: Starting single-shot recognition (recognizeOnceAsync)...");
                Future<SpeechRecognitionResult> future = recognizer.recognizeOnceAsync();
                speechResult = future.get(AZURE_RECOGNITION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (speechResult == null) {
                    errorForUi = "Did not receive result from Azure (timeout or critical error).";
                    Log.e(TAG_ACTIVITY, "Azure STT: speechResult is null.");
                } else if (speechResult.getReason() == ResultReason.RecognizedSpeech) {
                    recognizedText = speechResult.getText();
                    pronunciationAssessmentResult = PronunciationAssessmentResult.fromResult(speechResult);
                    Log.i(TAG_ACTIVITY, "Azure STT: Recognition result: \"" + recognizedText + "\"");
                    if (pronunciationAssessmentResult != null) {
                        Log.i(TAG_ACTIVITY, "Azure Pronunciation Score: Accuracy=" + pronunciationAssessmentResult.getAccuracyScore() +
                                ", Fluency=" + pronunciationAssessmentResult.getFluencyScore() +
                                ", PronScore=" + pronunciationAssessmentResult.getPronunciationScore() +
                                ", Completeness=" + pronunciationAssessmentResult.getCompletenessScore());
                        if(pronunciationAssessmentResult.getAccuracyScore() == 0 && pronunciationAssessmentResult.getPronunciationScore() == 0 && !recognizedText.isEmpty() && recognizedText.length() > referenceTextCleaned.length()/3){
                            Log.w(TAG_ACTIVITY, "Azure: Scores are 0 despite recognized text. Possible cause: suboptimal audio quality/sample rate.");
                        }
                    } else {
                        Log.w(TAG_ACTIVITY, "PronunciationAssessmentResult is NULL. Recognized text: \"" + recognizedText + "\". Reference: \"" + referenceTextCleaned + "\"");
                        if (recognizedText.isEmpty()) {
                            errorForUi = "Speech recognized but no content.";
                        } else {
                            errorForUi = "Could not get pronunciation assessment details. Spoken text might be too different from the sample or audio quality issues.";
                        }
                    }
                } else if (speechResult.getReason() == ResultReason.NoMatch) {
                    errorForUi = "Could not recognize speech (NoMatch). Please check microphone.";
                    Log.w(TAG_ACTIVITY, "Azure STT: NOMATCH. SpeechResult details: " + speechResult.toString());
                } else if (speechResult.getReason() == ResultReason.Canceled) {
                    CancellationDetails cancellation = CancellationDetails.fromResult(speechResult);
                    errorForUi = "Analysis request canceled: " + cancellation.getReason();
                    azureDetailedError = "ErrorCode: " + cancellation.getErrorCode() + ", Details: " + cancellation.getErrorDetails();
                    Log.e(TAG_ACTIVITY, "Azure STT: CANCELED. Reason: " + cancellation.getReason() + ". " + azureDetailedError);

                    if (cancellation.getReason() == CancellationReason.Error) {
                        String errDetailsOriginal = cancellation.getErrorDetails() != null ? cancellation.getErrorDetails() : "";
                        String errDetailsLower = errDetailsOriginal.toLowerCase();
                        com.microsoft.cognitiveservices.speech.CancellationErrorCode errorCode = cancellation.getErrorCode();

                        String gstreamerErrorHex = "0x29";
                        String gstreamerErrorName = "spxerr_gstreamer_not_found_error";
                        String gstreamerKeyword = "gstreamer";

                        if (errorCode == com.microsoft.cognitiveservices.speech.CancellationErrorCode.ConnectionFailure ||
                                errorCode == com.microsoft.cognitiveservices.speech.CancellationErrorCode.ServiceUnavailable) {
                            errorForUi = "Connection error or Azure service unavailable. Check network.";
                        } else if (errorCode == com.microsoft.cognitiveservices.speech.CancellationErrorCode.AuthenticationFailure) {
                            errorForUi = "Azure authentication error. Check Speech Key and Region.";
                        } else if (errDetailsLower.contains("audio format") ||
                                errDetailsLower.contains("unsupported") ||
                                errDetailsLower.contains(gstreamerKeyword) ||
                                errDetailsOriginal.contains(gstreamerErrorHex) ||
                                errDetailsLower.contains(gstreamerErrorName) ) {
                            errorForUi = "Audio processing error (" + audioFile.getName() +
                                    "). Error code: " + errorCode + ". Details: " + errDetailsOriginal +
                                    ". Consider using resampled 16kHz mono WAV.";
                            Log.e(TAG_ACTIVITY, "Detected GStreamer or audio format related error. Details: " + errDetailsOriginal);
                        } else if (errDetailsLower.contains("initialsilencetimeout")) {
                            errorForUi = "No speech detected. Speak louder and clearer.";
                        } else if (errDetailsLower.contains("babbletimeout")) {
                            errorForUi = "Too much noise or unclear speech detected.";
                        } else if (errDetailsLower.contains("phrase differ") || errDetailsLower.contains("reference text too short") || errDetailsLower.contains("reference text too long")) {
                            errorForUi = "Reference text unsuitable for assessment ("+ errDetailsOriginal +").";
                        }
                        else {
                            errorForUi = "Error from Azure [" + errorCode + "]: " + errDetailsOriginal;
                        }
                    }
                }

            } catch (FileNotFoundException fnfEx) {
                errorForUi = "Recording file to process not found: " + fnfEx.getMessage();
                Log.e(TAG_ACTIVITY, "Azure Processing: FileNotFoundException ", fnfEx);
            } catch (IllegalArgumentException iae) {
                errorForUi = "Invalid configuration or parameters for Azure: " + iae.getMessage();
                Log.e(TAG_ACTIVITY, "Azure Processing: IllegalArgumentException ", iae);
            } catch (InterruptedException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
                errorForUi = "Error waiting for Azure result (timeout or canceled): " + ex.getMessage();
                Log.e(TAG_ACTIVITY, "Azure Processing: Exception while waiting for future.get() ", ex);
                if (ex.getCause() != null) { Log.e(TAG_ACTIVITY, "Azure Processing: Caused by: ", ex.getCause()); }
            } catch (Exception e) {
                errorForUi = "Unknown error occurred during speech analysis: " + e.getMessage();
                Log.e(TAG_ACTIVITY, "Azure Processing: Generic Exception (Unknown error)", e);
                if (e.getCause() != null) {
                    Log.e(TAG_ACTIVITY, "Azure Processing: Cause of unknown error: ", e.getCause());
                    azureDetailedError += " | Cause: " + e.getCause().getMessage();
                }
            } finally {
                if (speechResult != null) { try { speechResult.close(); } catch (Exception e) { Log.e(TAG_ACTIVITY, "Error closing speechResult", e); } }
                if (recognizer != null) { try { recognizer.close(); } catch (Exception e) { Log.e(TAG_ACTIVITY, "Error closing recognizer", e); } }
                if (audioConfig != null) { try { audioConfig.close(); } catch (Exception e) { Log.e(TAG_ACTIVITY, "Error closing audioConfig", e); } }
                if (pullAudioInputStream != null) { try { pullAudioInputStream.close(); } catch (Exception e) { Log.e(TAG_ACTIVITY, "Error closing pullAudioInputStream", e); } }

                if (tempWavFilePath != null) {
                    File wavFile = new File(tempWavFilePath);
                    if (wavFile.exists()) {
                        if (wavFile.delete()) Log.i(TAG_ACTIVITY, "Deleted temporary WAV file: " + tempWavFilePath);
                        else Log.w(TAG_ACTIVITY, "Could not delete temporary WAV file: " + tempWavFilePath);
                    }
                }

                final String finalRecognizedText = recognizedText;
                final PronunciationAssessmentResult finalPronunciationResult = pronunciationAssessmentResult;
                final String finalErrorForUi = errorForUi;
                final String finalAzureDetailedError = azureDetailedError;

                runOnUiThread(() -> {
                    Log.d(TAG_ACTIVITY, "Azure Finally UI: Preparing to hide ProgressBar and update UI.");
                    if (pgbAzureProcessing != null) {
                        Log.d(TAG_ACTIVITY, "Azure Finally UI: ProgressBar is currently " + (pgbAzureProcessing.getVisibility() == View.VISIBLE ? "VISIBLE" : "GONE/INVISIBLE"));
                        pgbAzureProcessing.setVisibility(View.GONE);
                        Log.d(TAG_ACTIVITY, "Azure Finally UI: ProgressBar set to GONE. New Visibility: " + (pgbAzureProcessing.getVisibility() == View.VISIBLE ? "VISIBLE" : "GONE/INVISIBLE"));
                    } else {
                        Log.e(TAG_ACTIVITY, "Azure Finally UI: pgbAzureProcessing is NULL!");
                    }
                    setUiInteraction(true);

                    if (finalErrorForUi != null) {
                        showError(finalErrorForUi);
                        Log.e(TAG_ACTIVITY, "Azure UI Error: " + finalErrorForUi + (finalAzureDetailedError.isEmpty() ? "" : " | Azure detailed error: " + finalAzureDetailedError));
                        restoreOriginalScriptText();
                    } else if (finalPronunciationResult != null) {
                        showToast("Pronunciation analysis complete! Overall score: " + String.format(Locale.US, "%.0f", finalPronunciationResult.getPronunciationScore()) + "%");
                        highlightDifferencesAzure(this.lastCleanScriptDisplayed, finalPronunciationResult, finalRecognizedText);
                    } else if (finalRecognizedText != null && !finalRecognizedText.isEmpty()) {
                        showToast("Speech recognized successfully (no detailed pronunciation assessment).");
                        highlightDifferencesSimple(this.lastCleanScriptDisplayed, finalRecognizedText);
                    } else {
                        showError("Could not recognize speech or an unknown error occurred during processing.");
                        restoreOriginalScriptText();
                    }
                    Log.d(TAG_ACTIVITY, "Azure Finally UI: UI update complete.");
                });
            }
        }).exceptionally(ex -> {
            Log.e(TAG_ACTIVITY, "Azure Exceptionally: Critical error in background task ", ex);
            runOnUiThread(() -> {
                Log.d(TAG_ACTIVITY, "Azure Exceptionally UI: Preparing to hide ProgressBar due to error.");
                if (pgbAzureProcessing != null) {
                    pgbAzureProcessing.setVisibility(View.GONE);
                    Log.d(TAG_ACTIVITY, "Azure Exceptionally UI: ProgressBar set to GONE.");
                } else {
                    Log.e(TAG_ACTIVITY, "Azure Exceptionally UI: pgbAzureProcessing is NULL!");
                }
                setUiInteraction(true);
                showError("Critical error in Azure background task: " + ex.getMessage());
                restoreOriginalScriptText();
            });
            return null;
        });
    }

    private String transcodeToWav(String inputPath) {
        if (inputPath == null) {
            Log.e(TAG_ACTIVITY, "transcodeToWav: Input file path is null.");
            return null;
        }
        File inputFile = new File(inputPath);
        if (!inputFile.exists() || inputFile.length() == 0) {
            Log.e(TAG_ACTIVITY, "transcodeToWav: Input file does not exist or is empty: " + inputPath);
            return null;
        }

        File outputFile = new File(getCacheDir(), "temp_audio_" + System.currentTimeMillis() + ".wav");
        Log.i(TAG_ACTIVITY, "transcodeToWav: Starting transcoding " + inputPath + " to " + outputFile.getAbsolutePath());

        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        FileOutputStream fos = null;
        RandomAccessFile randomAccessFile = null;

        final int TIMEOUT_US = 10000;
        final int TARGET_SAMPLE_RATE_IDEAL = 16000;
        int actualOutputSampleRate = 8000;
        final int TARGET_CHANNELS = 1;
        final int TARGET_BIT_DEPTH = 16;

        try {
            fos = new FileOutputStream(outputFile);
            byte[] wavHeaderPlaceholder = new byte[44];
            fos.write(wavHeaderPlaceholder);

            extractor = new MediaExtractor();
            extractor.setDataSource(inputPath);

            int audioTrackIndex = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    inputFormat = format;
                    Log.d(TAG_ACTIVITY, "transcodeToWav: Found audio track: " + mime + ", Format: " + inputFormat);
                    break;
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                Log.e(TAG_ACTIVITY, "transcodeToWav: No audio track found in " + inputPath);
                return null;
            }
            extractor.selectTrack(audioTrackIndex);

            String inputMime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (inputMime == null) {
                Log.e(TAG_ACTIVITY, "transcodeToWav: Could not get MIME type from inputFormat.");
                return null;
            }
            decoder = MediaCodec.createDecoderByType(inputMime);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean inputEos = false;
            boolean outputEos = false;
            long totalPcmBytesWritten = 0;

            while (!outputEos) {
                Thread.yield();
                if (!inputEos) {
                    int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inputBufIndex >= 0) {
                        ByteBuffer inputBuffer = inputBuffers[inputBufIndex];
                        inputBuffer.clear();
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            Log.d(TAG_ACTIVITY, "transcodeToWav: End of input data (Input EOS).");
                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEos = true;
                        } else {
                            decoder.queueInputBuffer(inputBufIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputBufIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                if (outputBufIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufIndex];
                    if (bufferInfo.size > 0) {
                        byte[] pcmChunk = new byte[bufferInfo.size];
                        outputBuffer.get(pcmChunk);
                        outputBuffer.clear();
                        fos.write(pcmChunk);
                        totalPcmBytesWritten += pcmChunk.length;
                    }
                    decoder.releaseOutputBuffer(outputBufIndex, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG_ACTIVITY, "transcodeToWav: Received Output EOS flag.");
                        outputEos = true;
                    }
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = decoder.getOutputBuffers();
                    Log.d(TAG_ACTIVITY, "transcodeToWav: Output buffers changed.");
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    actualOutputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int actualOutputChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    Log.d(TAG_ACTIVITY, "transcodeToWav: Output format changed to: " + newFormat +
                            " (SampleRate: " + actualOutputSampleRate + ", Channels: " + actualOutputChannels + ")");
                    if (actualOutputSampleRate != TARGET_SAMPLE_RATE_IDEAL || actualOutputChannels != TARGET_CHANNELS) {
                        Log.w(TAG_ACTIVITY, "transcodeToWav: WARNING: Output PCM format is " + actualOutputSampleRate + "Hz/" + actualOutputChannels + "ch. " +
                                "Ideal for Azure is " + TARGET_SAMPLE_RATE_IDEAL + "Hz/" + TARGET_CHANNELS + "ch. " +
                                "RESAMPLE/REMIX NEEDED for optimization.");
                    }
                }
            }

            fos.flush();
            fos.close();
            fos = null;

            randomAccessFile = new RandomAccessFile(outputFile, "rw");
            writeWavHeader(randomAccessFile, totalPcmBytesWritten,
                    actualOutputSampleRate, TARGET_CHANNELS, TARGET_BIT_DEPTH);
            randomAccessFile.close();
            randomAccessFile = null;

            Log.i(TAG_ACTIVITY, "transcodeToWav: Transcoding successful: " + outputFile.getAbsolutePath() + ", PCM Size: " + totalPcmBytesWritten);
            return outputFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG_ACTIVITY, "transcodeToWav: Error during transcoding ", e);
            if (outputFile.exists()) {
                outputFile.delete();
            }
            return null;
        } finally {
            try {
                if (fos != null) fos.close();
                if (randomAccessFile != null) randomAccessFile.close();
                if (extractor != null) extractor.release();
                if (decoder != null) {
                    decoder.stop();
                    decoder.release();
                }
            } catch (Exception e) {
                Log.e(TAG_ACTIVITY, "transcodeToWav: Error releasing resources ", e);
            }
        }
    }

    private void writeWavHeader(RandomAccessFile raf, long pcmDataSize,
                                int sampleRate, int numChannels, int bitsPerSample) throws IOException {
        long totalDataLen = pcmDataSize + 36;
        long byteRate = (long)sampleRate * numChannels * bitsPerSample / 8;
        int blockAlign = numChannels * bitsPerSample / 8;

        raf.seek(0);

        raf.writeBytes("RIFF");
        raf.write(longToByteArray(totalDataLen, 4, ByteOrder.LITTLE_ENDIAN));
        raf.writeBytes("WAVE");
        raf.writeBytes("fmt ");
        raf.write(longToByteArray(16, 4, ByteOrder.LITTLE_ENDIAN));
        raf.write(longToByteArray(1, 2, ByteOrder.LITTLE_ENDIAN));
        raf.write(longToByteArray(numChannels, 2, ByteOrder.LITTLE_ENDIAN));
        raf.write(longToByteArray(sampleRate, 4, ByteOrder.LITTLE_ENDIAN));
        raf.write(longToByteArray(byteRate, 4, ByteOrder.LITTLE_ENDIAN));
        raf.write(longToByteArray(blockAlign, 2, ByteOrder.LITTLE_ENDIAN));
        raf.write(longToByteArray(bitsPerSample, 2, ByteOrder.LITTLE_ENDIAN));
        raf.writeBytes("data");
        raf.write(longToByteArray(pcmDataSize, 4, ByteOrder.LITTLE_ENDIAN));

        Log.d(TAG_ACTIVITY, "writeWavHeader: WAV header written. pcmDataSize=" + pcmDataSize +
                ", sampleRate=" + sampleRate + ", numChannels=" + numChannels + ", bitsPerSample=" + bitsPerSample);
    }

    private byte[] longToByteArray(long value, int numBytes, ByteOrder byteOrder) {
        ByteBuffer buffer = ByteBuffer.allocate(numBytes);
        buffer.order(byteOrder);
        if (numBytes == 2) {
            buffer.putShort((short) value);
        } else if (numBytes == 4) {
            buffer.putInt((int) value);
        } else {
            buffer.putLong(value);
        }
        return buffer.array();
    }

    private void highlightDifferencesSimple(String originalText, String recognizedText) {
        if (txtScriptToRepeat == null || originalText == null || recognizedText == null) {
            Log.w(TAG_ACTIVITY, "highlightDifferencesSimple: Invalid input.");
            restoreOriginalScriptText();
            return;
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder(originalText);
        if (originalText.trim().isEmpty()) {
            txtScriptToRepeat.setText(spannable);
            return;
        }

        String[] originalWords = originalText.trim().split("\\s+");
        String[] recognizedWords = recognizedText.trim().isEmpty() ? new String[0] : recognizedText.trim().split("\\s+");

        int currentSearchStartOffset = 0;
        int recIdx = 0;

        for (String origWordWithPunct : originalWords) {
            if (origWordWithPunct.isEmpty()) continue;
            int start = originalText.indexOf(origWordWithPunct, currentSearchStartOffset);
            if (start == -1) {
                Log.w(TAG_ACTIVITY, "SimpleHighlight: Could not find word '" + origWordWithPunct + "' from offset " + currentSearchStartOffset + ". Retrying from start.");
                start = originalText.indexOf(origWordWithPunct);
                if (start == -1) {
                    Log.e(TAG_ACTIVITY, "SimpleHighlight: Critical error, could not find word '" + origWordWithPunct + "' in original script. Skipping this word.");
                    currentSearchStartOffset += origWordWithPunct.length() + 1;
                    continue;
                }
            }
            int end = start + origWordWithPunct.length();

            boolean match = false;
            if (recIdx < recognizedWords.length) {
                String cleanedOrigWord = origWordWithPunct.replaceAll("[\\p{Punct}&&[^'-]]", "").toLowerCase(Locale.ROOT);
                String cleanedRecWord = recognizedWords[recIdx].replaceAll("[\\p{Punct}&&[^'-]]", "").toLowerCase(Locale.ROOT);
                if (cleanedOrigWord.equals(cleanedRecWord) && !cleanedOrigWord.isEmpty()) {
                    match = true;
                }
            }

            if (!match) {
                spannable.setSpan(new ForegroundColorSpan(Color.RED), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (match) {
                recIdx++;
            }
            currentSearchStartOffset = end;
        }
        txtScriptToRepeat.setText(spannable);
    }


    private void highlightDifferencesAzure(String originalText, PronunciationAssessmentResult assessmentResult, String recognizedTranscriptOverall) {
        if (txtScriptToRepeat == null || originalText == null || assessmentResult == null) {
            Log.e(TAG_ACTIVITY, "highlightDifferencesAzure: Invalid input (null).");
            restoreOriginalScriptText();
            return;
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder(originalText);
        List<WordLevelTimingResult> assessedWords = assessmentResult.getWords();

        if (assessedWords == null || assessedWords.isEmpty()) {
            Log.w(TAG_ACTIVITY, "No word data from PronunciationAssessmentResult for Azure highlighting.");
            if (recognizedTranscriptOverall != null && !recognizedTranscriptOverall.isEmpty()) {
                highlightDifferencesSimple(originalText, recognizedTranscriptOverall);
            } else {
                restoreOriginalScriptText();
            }
            return;
        }

        Log.i(TAG_ACTIVITY, "HighlightAzure: Starting highlighting. Number of Azure assessed words: " + assessedWords.size());
        Log.d(TAG_ACTIVITY, "HighlightAzure: Original text: \"" + originalText + "\"");

        int searchStartIndexInOriginal = 0;

        for (WordLevelTimingResult assessedWordData : assessedWords) {
            String wordFromAzureCleaned = assessedWordData.getWord();
            String errorType = assessedWordData.getErrorType();
            double accuracyScore = assessedWordData.getAccuracyScore();

            Log.d(TAG_ACTIVITY, "HighlightAzure: Processing Azure word: '" + wordFromAzureCleaned +
                    "', Error: " + errorType + ", Score: " + accuracyScore);

            boolean foundThisAzureWord = false;
            Pattern wordPattern = Pattern.compile("\\b[a-zA-Z0-9]+(['-][a-zA-Z0-9]+)*\\b");
            Matcher matcher = wordPattern.matcher(originalText);

            while (matcher.find(searchStartIndexInOriginal)) {
                String originalWordSegment = matcher.group(0);
                int startInOriginal = matcher.start();
                int endInOriginal = matcher.end();
                String cleanedOriginalWordSegment = originalWordSegment.toLowerCase(Locale.ROOT);

                Log.v(TAG_ACTIVITY, "HighlightAzure:   Comparing Azure '" + wordFromAzureCleaned +
                        "' with original (cleaned) '" + cleanedOriginalWordSegment +
                        "' (from original '" + originalWordSegment + "' at " + startInOriginal + "-" + endInOriginal + ")");

                if (cleanedOriginalWordSegment.equals(wordFromAzureCleaned)) {
                    Log.d(TAG_ACTIVITY, "HighlightAzure:   MATCH! Original: '" + originalWordSegment + "' with Azure: '" + wordFromAzureCleaned + "'");
                    boolean needsHighlightRed = false;
                    if ("Mispronunciation".equalsIgnoreCase(errorType)) {
                        needsHighlightRed = true;
                    } else if ("Omission".equalsIgnoreCase(errorType)) {
                        needsHighlightRed = true;
                    } else if (accuracyScore < 60.0 && !"None".equalsIgnoreCase(errorType) && !"Insertion".equalsIgnoreCase(errorType)) {
                        needsHighlightRed = true;
                    }

                    if (needsHighlightRed) {
                        spannable.setSpan(new ForegroundColorSpan(Color.RED), startInOriginal, endInOriginal, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        Log.i(TAG_ACTIVITY, "HighlightAzure:     HIGHLIGHTING RED: '" + originalWordSegment + "'");
                    } else {
                        Log.d(TAG_ACTIVITY, "HighlightAzure:     NOT HIGHLIGHTING RED (OK): '" + originalWordSegment + "'");
                    }

                    searchStartIndexInOriginal = endInOriginal;
                    foundThisAzureWord = true;
                    break;
                }
            }

            if (!foundThisAzureWord) {
                Log.w(TAG_ACTIVITY, "HighlightAzure:   COULD NOT FIND Azure word '" + wordFromAzureCleaned +
                        "' in the remainder of originalText (from position: " + searchStartIndexInOriginal + "). ");
            }
        }
        txtScriptToRepeat.setText(spannable);
        Log.i(TAG_ACTIVITY, "HighlightAzure: Highlighting complete and TextView updated.");
    }


    private static class MyPullAudioInputStreamCallback extends PullAudioInputStreamCallback {
        private FileInputStream fileStream;
        private static final String TAG_CALLBACK = "MyPullAudioStreamCB";

        MyPullAudioInputStreamCallback(String filePath) throws FileNotFoundException {
            File audioFile = new File(filePath);
            if (!audioFile.exists()) {
                Log.e(TAG_CALLBACK, "File does not exist: " + filePath);
                throw new FileNotFoundException("File does not exist: " + filePath);
            }
            if (audioFile.length() == 0) {
                Log.w(TAG_CALLBACK, "File is empty (0 bytes): " + filePath + ". Azure may report an error.");
            }
            this.fileStream = new FileInputStream(audioFile);
            Log.d(TAG_CALLBACK, "FileInputStream opened for: " + filePath + ", size: " + audioFile.length());
        }

        @Override
        public int read(byte[] dataBuffer) {
            try {
                int bytesRead = this.fileStream.read(dataBuffer, 0, dataBuffer.length);
                if (bytesRead == -1) {
                    return 0;
                }
                return bytesRead;
            } catch (IOException e) {
                Log.e(TAG_CALLBACK, "Error reading from file stream: " + e.getMessage(), e);
                return 0;
            }
        }

        @Override
        public void close() {
            try {
                if (this.fileStream != null) {
                    this.fileStream.close();
                    Log.d(TAG_CALLBACK, "FileInputStream closed.");
                    this.fileStream = null;
                }
            } catch (IOException e) {
                Log.e(TAG_CALLBACK, "Error closing file stream: " + e.getMessage(), e);
            }
        }
    }
}
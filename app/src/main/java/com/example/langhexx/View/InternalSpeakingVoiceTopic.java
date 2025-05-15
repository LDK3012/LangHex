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

    // IMPORTANT: Replace with your actual Azure Speech Key and Region
    private static final String AZURE_SPEECH_KEY = "75aMORlAm3JGJXfz0oOcHaX3hytrGyJ9MBRUfRGutW5qeZSuFjz3JQQJ99BEACYeBjFXJ3w3AAAYACOGDbeK"; // Thay thế bằng Key của bạn
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
                            int duration = 0;
                            try { duration = mediaPlayer.getDuration(); } catch (IllegalStateException e) { /* ignore */ }
                            updatePlayTimeWithMillis(seekBar.getProgress(), duration);
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
            } else if (isCurrentlyRecording) {
                imgRecord.setEnabled(true);
                imgRecord.setAlpha(1.0f);
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
                    if(existingRecording.delete()){
                        Log.d(TAG_ACTIVITY, "Deleted empty recording file: " + persistentPath);
                    }
                }
                this.currentRecordingFilePath = null;
                restoreOriginalScriptText();
                updatePlaybackUIState(false, false);
            }
        } else {
            Log.w(TAG_ACTIVITY, "checkAndLoadExistingRecording: Could not generate persistentPath.");
            this.currentRecordingFilePath = null;
            restoreOriginalScriptText();
            updatePlaybackUIState(false, false);
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
            boolean hasFile = persistentPath != null && new File(persistentPath).exists() && new File(persistentPath).length() > 0;

            if (hasFile) {
                if(this.currentRecordingFilePath == null || !this.currentRecordingFilePath.equals(persistentPath) || mediaPlayer == null){
                    this.currentRecordingFilePath = persistentPath;
                    if (new File(persistentPath).length() > 0) {
                        prepareMediaPlayerForPlayback();
                    } else {
                        updatePlaybackUIState(false, false);
                    }
                } else {
                    updatePlaybackUIState(mediaPlayer != null && mediaPlayer.isPlaying(), true);
                }
            } else {
                updatePlaybackUIState(false, false);
            }
            isCurrentlyRecording = false;
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

            if (savedFilePath != null && new File(savedFilePath).exists() && new File(savedFilePath).length() > 0) {
                this.currentRecordingFilePath = savedFilePath;
                if (azureSpeechConfig != null) {
                    processRecordingWithAzure(this.currentRecordingFilePath);
                } else {
                    showError("Azure service is not ready. Cannot analyze.");
                    restoreOriginalScriptText();
                    prepareMediaPlayerForPlayback();
                }
            } else {
                Log.w(TAG_ACTIVITY, "Invalid recording file after stop: " + (savedFilePath != null ? savedFilePath : "null") + ". Cannot play or analyze.");
                this.currentRecordingFilePath = null;
                restoreOriginalScriptText();
                updatePlaybackUIState(false, false);
            }
        }
    }


    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
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
                    if (f.delete()) {
                        Log.d(TAG_ACTIVITY, "Empty file deleted successfully.");
                    }
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
                    Log.e(TAG_ACTIVITY, "MediaPlayer prepared with invalid duration: " + mp.getDuration() + ". File may be corrupted: " + currentRecordingFilePath);
                    showError("Cannot play recording, file may be corrupted.");
                    releaseMediaPlayer();
                    this.currentRecordingFilePath = null;
                    updatePlaybackUIState(false, false);
                    return;
                }
                if (sbrAudio != null) sbrAudio.setMax(mp.getDuration());
                updatePlayTimeWithMillis(0, mp.getDuration());
                updatePlaybackUIState(false, true);
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
                    File f = new File(currentRecordingFilePath); if(f.exists() && f.delete()){ /* log */ }
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
                if (mediaPlayer.getDuration() > 0 && currentSeekBarPos >= mediaPlayer.getDuration()) {
                    currentSeekBarPos = 0;
                }
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
                } else {
                    showError("Could not delete recording file: " + file.getName());
                    Log.e(TAG_ACTIVITY, "Could not delete recording file: " + pathToDelete);
                }
            } else {
                Log.d(TAG_ACTIVITY, "Recording file not found to delete (already deleted or never existed): " + pathToDelete);
            }
        } else {
            Log.w(TAG_ACTIVITY, "deleteCurrentRecording: No valid file path to delete for current script.");
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
    public void updateRecordingUIState(boolean isRecordingActiveState) {
        boolean isAzureProcessing = pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE;
        if (isAzureProcessing) {
            return;
        }

        if (this.isCurrentlyRecording) {
            if (imgRecord != null) {
                imgRecord.setImageResource(R.drawable.voice_icon_pause_);
                if (pulsatingAnimation != null && (imgRecord.getAnimation() == null || !imgRecord.getAnimation().hasStarted() || imgRecord.getAnimation().hasEnded())) {
                    imgRecord.startAnimation(pulsatingAnimation);
                }
                imgRecord.setVisibility(View.VISIBLE);
                imgRecord.setEnabled(true);
            }
            if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.GONE);

            if(btnBackward != null) btnBackward.setEnabled(false);
            if(btnForward != null) btnForward.setEnabled(false);
            if(imgClose != null) {imgClose.setEnabled(false); imgClose.setAlpha(0.5f);}
            if(imgHome != null) {imgHome.setEnabled(false); imgHome.setAlpha(0.5f);}

        } else {
            if (imgRecord != null) {
                imgRecord.setImageResource(R.drawable.micro);
                if (pulsatingAnimation != null && imgRecord.getAnimation() != null) {
                    imgRecord.clearAnimation();
                }
            }
            boolean hasFile = this.currentRecordingFilePath != null && new File(this.currentRecordingFilePath).exists() && new File(this.currentRecordingFilePath).length() > 0;
            updatePlaybackUIState(this.isCurrentlyPlaying, hasFile);
        }
    }


    @Override
    public void updatePlaybackUIState(boolean isMediaPlayerPlaying, boolean hasAudioFile) {
        this.isCurrentlyPlaying = isMediaPlayerPlaying;
        boolean isAzureProcessing = pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE;

        if (isAzureProcessing) {
            if (imgRecord != null) { // Đảm bảo imgRecord cũng bị ẩn nếu đang xử lý
                if (imgRecord.getAnimation() != null) {
                    imgRecord.clearAnimation();
                }
                imgRecord.setVisibility(View.GONE);
            }
            if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.GONE);
            return;
        }

        if (this.isCurrentlyRecording) {
            if (imgRecord != null) {
                imgRecord.setImageResource(R.drawable.voice_icon_pause_);
                if (pulsatingAnimation != null && (imgRecord.getAnimation() == null || !imgRecord.getAnimation().hasStarted() || imgRecord.getAnimation().hasEnded())) {
                    imgRecord.startAnimation(pulsatingAnimation);
                }
                imgRecord.setVisibility(View.VISIBLE);
                imgRecord.setEnabled(true);
            }
            if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.GONE);

            if(btnBackward != null) btnBackward.setEnabled(false);
            if(btnForward != null) btnForward.setEnabled(false);
            if(imgClose != null) {imgClose.setEnabled(false); imgClose.setAlpha(0.5f);}
            if(imgHome != null) {imgHome.setEnabled(false); imgHome.setAlpha(0.5f);}

        } else {
            if (imgRecord != null && imgRecord.getAnimation() != null) {
                imgRecord.clearAnimation();
            }

            if (hasAudioFile) {
                if (imgRecord != null) imgRecord.setVisibility(View.GONE);
                if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.VISIBLE);

                boolean canInteractWithPlayback = (mediaPlayer != null);
                try { if(mediaPlayer != null) mediaPlayer.getDuration(); else canInteractWithPlayback = false; }
                catch (IllegalStateException e) { canInteractWithPlayback = false;}


                if (imgPlayAudio != null) {
                    imgPlayAudio.setImageResource(isMediaPlayerPlaying ? R.drawable.ic_pause : R.drawable.icon_play_audio);
                    imgPlayAudio.setEnabled(canInteractWithPlayback);
                    imgPlayAudio.setAlpha(canInteractWithPlayback ? 1.0f : 0.5f);
                }
                if (imgDelete != null) {
                    imgDelete.setEnabled(true);
                    imgDelete.setAlpha(1.0f);
                }
                if (sbrAudio != null) {
                    sbrAudio.setEnabled(canInteractWithPlayback);
                }
            } else {
                if (imgRecord != null) {
                    imgRecord.setImageResource(R.drawable.micro);
                    imgRecord.setVisibility(View.VISIBLE);
                    imgRecord.setEnabled(true);
                    imgRecord.setAlpha(1.0f);
                }
                if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.GONE);

                if (sbrAudio != null) {
                    sbrAudio.setEnabled(false);
                    sbrAudio.setProgress(0);
                    sbrAudio.setMax(100);
                }
                if (txtTime != null) txtTime.setText("00:00 / 00:00");
            }

            if (imgClose != null) {imgClose.setEnabled(true); imgClose.setAlpha(1.0f);}
            if (imgHome != null) {imgHome.setEnabled(true); imgHome.setAlpha(1.0f);}
            if (btnBackward != null) {
                btnBackward.setEnabled(this.lastCanGoPreviousState);
                btnBackward.setBackground(ContextCompat.getDrawable(this, this.lastCanGoPreviousState ? R.drawable.bg_button_next_rounded : R.drawable.bg_button_previous_rounded));
            }
            if (btnForward != null) {
                btnForward.setEnabled(this.lastCanGoNextState);
                btnForward.setBackground(ContextCompat.getDrawable(this, this.lastCanGoNextState ? R.drawable.bg_button_next_rounded : R.drawable.bg_button_previous_rounded));
            }
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
            boolean canUpdateSeekBar = false;
            try {
                if (mediaPlayer != null && validTotalMillis > 0) {
                    canUpdateSeekBar = true;
                }
            } catch (IllegalStateException e) {
                Log.w(TAG_ACTIVITY, "updatePlayTimeWithMillis: MediaPlayer not ready for sbrAudio update.");
            }

            if (canUpdateSeekBar) {
                if (!sbrAudio.isPressed()) {
                    if(sbrAudio.getMax() != (int)validTotalMillis) sbrAudio.setMax((int)validTotalMillis);
                    sbrAudio.setProgress((int) validCurrentMillis);
                }
            } else if (validTotalMillis <= 0) {
                sbrAudio.setMax(100);
                sbrAudio.setProgress(0);
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
                        updatePlayTimeWithMillis(currentPosition, duration);
                        if (seekBarHandler != null) seekBarHandler.postDelayed(this, 250);
                    } else if (isCurrentlyPlaying) {
                        Log.w(TAG_ACTIVITY, "updateSeekBarRunnable: isCurrentlyPlaying=true but mediaPlayer not playing. Will stop rescheduling if it persists.");
                    } else {
                        if (seekBarHandler != null) seekBarHandler.removeCallbacks(this);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG_ACTIVITY, "MediaPlayer in invalid state while updating seekbar: " + e.getMessage());
                    if(isCurrentlyPlaying && seekBarHandler != null) {
                        isCurrentlyPlaying = false;
                        updatePlaybackUIState(false, currentRecordingFilePath != null && new File(currentRecordingFilePath).exists());
                        if (seekBarHandler != null) seekBarHandler.removeCallbacks(this);

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
            Log.w(TAG_ACTIVITY, "Currently recording onStop. Auto-stopping recording.");
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
            Log.w(TAG_ACTIVITY, "setRecordingFilePath: path null or file does not exist/is empty. Path: " + path);
            if (path == null && oldPath != null) {
                Log.d(TAG_ACTIVITY, "setRecordingFilePath: new path is null, old path was: " + oldPath + ". This implies deletion or reset.");
            }
            pausePlayback();
            releaseMediaPlayer();
            updatePlaybackUIState(false, false);
            restoreOriginalScriptText();
        }
    }

    private void setUiInteraction(boolean allowInteraction) {
        boolean isAzureProcessing = pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE;
        boolean effectiveAllowInteraction = allowInteraction && !isAzureProcessing && !isCurrentlyRecording;

        if(imgRecord != null) {
            boolean shouldMicBeVisible = (layoutPlaybackControls == null || layoutPlaybackControls.getVisibility() == View.GONE);
            imgRecord.setEnabled(effectiveAllowInteraction && shouldMicBeVisible);
            imgRecord.setAlpha((effectiveAllowInteraction && shouldMicBeVisible) ? 1.0f : 0.5f);
        }

        boolean hasFileForPlayback = currentRecordingFilePath != null && new File(currentRecordingFilePath).exists() && new File(currentRecordingFilePath).length() > 0;
        boolean canPlaybackControlsBeActive = effectiveAllowInteraction && hasFileForPlayback;

        if(imgPlayAudio != null) {
            imgPlayAudio.setEnabled(canPlaybackControlsBeActive && mediaPlayer != null);
            imgPlayAudio.setAlpha((canPlaybackControlsBeActive && mediaPlayer != null) ? 1.0f : 0.5f);
        }
        if(imgDelete != null) {
            imgDelete.setEnabled(canPlaybackControlsBeActive);
            imgDelete.setAlpha(canPlaybackControlsBeActive ? 1.0f : 0.5f);
        }
        if(sbrAudio != null) {
            sbrAudio.setEnabled(canPlaybackControlsBeActive && mediaPlayer != null);
        }

        if(btnBackward != null) {
            btnBackward.setEnabled(effectiveAllowInteraction && lastCanGoPreviousState);
        }
        if(btnForward != null) {
            btnForward.setEnabled(effectiveAllowInteraction && lastCanGoNextState);
        }

        if(imgClose != null) imgClose.setEnabled(allowInteraction && !isAzureProcessing && !isCurrentlyRecording);
        if(imgHome != null) imgHome.setEnabled(allowInteraction && !isAzureProcessing && !isCurrentlyRecording);
    }

    private void restoreOriginalScriptText() {
        if (txtScriptToRepeat != null && this.lastCleanScriptDisplayed != null && !this.lastCleanScriptDisplayed.isEmpty()) {
            txtScriptToRepeat.setText(this.lastCleanScriptDisplayed);
            txtScriptToRepeat.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        } else if (txtScriptToRepeat != null) {
            txtScriptToRepeat.setText("");
        }
    }

    private void processRecordingWithAzure(String audioFilePath) {
        if (azureSpeechConfig == null) {
            showError("Azure speech service is not configured. Cannot analyze.");
            restoreOriginalScriptText();
            updatePlaybackUIState(false, new File(audioFilePath).exists() && new File(audioFilePath).length() > 0);
            return;
        }
        if (audioFilePath == null || !new File(audioFilePath).exists() || new File(audioFilePath).length() == 0) {
            showError("Invalid or empty recording file. Cannot analyze.");
            restoreOriginalScriptText();
            updatePlaybackUIState(false, false);
            return;
        }

        final String referenceTextCleaned = this.lastCleanScriptDisplayed
                .replaceAll("[\\p{Punct}&&[^'-]]", "")
                .toLowerCase(Locale.ROOT)
                .trim();

        if (referenceTextCleaned.isEmpty()) {
            showError("Reference text is empty. Cannot perform pronunciation assessment.");
            restoreOriginalScriptText();
            if (new File(audioFilePath).exists() && new File(audioFilePath).length() > 0) {
                prepareMediaPlayerForPlayback();
            } else {
                updatePlaybackUIState(false, false);
            }
            return;
        }

        runOnUiThread(() -> {
            Log.d(TAG_ACTIVITY, "processRecordingWithAzure: Starting. Setting UI to processing state.");
            if (pgbAzureProcessing != null) pgbAzureProcessing.setVisibility(View.VISIBLE);

            if (imgRecord != null) {
                // Dừng animation nếu có và ẩn nút ghi âm
                if (imgRecord.getAnimation() != null) {
                    imgRecord.clearAnimation();
                }
                imgRecord.setVisibility(View.GONE);
            }
            if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.GONE);

            if(btnBackward != null) btnBackward.setEnabled(false);
            if(btnForward != null) btnForward.setEnabled(false);
            if(imgClose != null) { imgClose.setEnabled(false); imgClose.setAlpha(0.5f); }
            if(imgHome != null) { imgHome.setEnabled(false); imgHome.setAlpha(0.5f); }
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
            int actualSampleRateForSdk = 8000;


            if (USE_WAV_TRANSCODING_FOR_AZURE) {
                Log.i(TAG_ACTIVITY, "Starting transcoding to WAV for file: " + audioFilePath);
                tempWavFilePath = transcodeToWav(audioFilePath);

                if (tempWavFilePath != null && new File(tempWavFilePath).exists()) {
                    effectiveAudioFilePath = tempWavFilePath;
                    Log.i(TAG_ACTIVITY, "Successfully transcoded to WAV: " + effectiveAudioFilePath);
                    actualSampleRateForSdk = 8000; // Cần cập nhật giá trị này từ transcodeToWav nếu có resampling
                    Log.w(TAG_ACTIVITY, "processRecordingWithAzure: Using WAV. Assuming sample rate " + actualSampleRateForSdk + "Hz. " +
                            "Ensure transcodeToWav produces 16kHz mono for optimal Azure results.");
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
                    Log.i(TAG_ACTIVITY, "Azure Processing: Using WAV PCM format. SampleRate (for SDK): " + actualSampleRateForSdk);
                    streamFormat = AudioStreamFormat.getWaveFormatPCM(actualSampleRateForSdk, bitDepthForWav, channelsForWav);
                } else {
                    Log.i(TAG_ACTIVITY, "Azure Processing: Using COMPRESSED AudioStreamFormat (ANY) for " + effectiveAudioFilePath);
                    streamFormat = AudioStreamFormat.getCompressedFormat(AudioStreamContainerFormat.ANY);
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
                            Log.w(TAG_ACTIVITY, "Azure: Scores are 0 despite recognized text. Possible cause: suboptimal audio quality/sample rate for assessment (e.g. not 16kHz mono). Input sample rate for SDK: " + actualSampleRateForSdk);
                        }
                    } else {
                        Log.w(TAG_ACTIVITY, "PronunciationAssessmentResult is NULL. Recognized text: \"" + recognizedText + "\". Reference: \"" + referenceTextCleaned + "\"");
                        if (recognizedText.isEmpty()) {
                            errorForUi = "Speech recognized but no content.";
                        } else {
                            errorForUi = "Could not get pronunciation details. Spoken text might be too different, or audio quality/format issue (e.g. ensure 16kHz mono for assessment).";
                        }
                    }
                } else if (speechResult.getReason() == ResultReason.NoMatch) {
                    errorForUi = "Could not recognize speech (NoMatch). Please check microphone and speak clearly.";
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
                            errorForUi = "Audio processing error by Azure SDK. File: " + audioFile.getName() +
                                    ". ErrorCode: " + errorCode + ". Details: " + errDetailsOriginal +
                                    ". SDK might require GStreamer for this format, or the format is unsupported. Consider using 16kHz mono WAV.";
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
                final String finalAzureDetailedErrorLog = azureDetailedError;

                runOnUiThread(() -> {
                    Log.d(TAG_ACTIVITY, "Azure Finally UI: Hiding ProgressBar, restoring UI interactions.");
                    if (pgbAzureProcessing != null) {
                        pgbAzureProcessing.setVisibility(View.GONE);
                    }

                    if(btnBackward != null) btnBackward.setEnabled(this.lastCanGoPreviousState);
                    if(btnForward != null) btnForward.setEnabled(this.lastCanGoNextState);
                    if(imgClose != null) { imgClose.setEnabled(true); imgClose.setAlpha(1.0f); }
                    if(imgHome != null) { imgHome.setEnabled(true); imgHome.setAlpha(1.0f); }

                    boolean hasValidRecordingAfterAzure = (this.currentRecordingFilePath != null &&
                            new File(this.currentRecordingFilePath).exists() &&
                            new File(this.currentRecordingFilePath).length() > 0);

                    if (finalErrorForUi != null) {
                        showError(finalErrorForUi);
                        Log.e(TAG_ACTIVITY, "Azure UI Error: " + finalErrorForUi + (finalAzureDetailedErrorLog.isEmpty() ? "" : " | Azure detailed error: " + finalAzureDetailedErrorLog));
                        restoreOriginalScriptText();
                        updatePlaybackUIState(false, false);
                    } else if (finalPronunciationResult != null) {
                        // showToast("Pronunciation analysis complete! Overall score: " + String.format(Locale.US, "%.0f", finalPronunciationResult.getPronunciationScore()) + "%"); // XÓA TOAST NÀY
                        Log.i(TAG_ACTIVITY, "Pronunciation analysis complete! Overall score: " + String.format(Locale.US, "%.0f", finalPronunciationResult.getPronunciationScore()) + "%"); // Giữ lại log nếu cần
                        highlightDifferencesAzure(this.lastCleanScriptDisplayed, finalPronunciationResult, finalRecognizedText);
                        if (!hasValidRecordingAfterAzure) {
                            Log.w(TAG_ACTIVITY, "Azure success, but recording file is now invalid/missing. Path: " + this.currentRecordingFilePath);
                            updatePlaybackUIState(false, false);
                        } else {
                            prepareMediaPlayerForPlayback();
                        }
                    } else if (finalRecognizedText != null && !finalRecognizedText.isEmpty()) {
                        showToast("Speech recognized successfully (no detailed pronunciation assessment)."); // Giữ lại toast này nếu muốn, hoặc xóa nếu "không thông báo overall gì cả" bao gồm cả trường hợp này
                        highlightDifferencesSimple(this.lastCleanScriptDisplayed, finalRecognizedText);
                        if (!hasValidRecordingAfterAzure) {
                            updatePlaybackUIState(false, false);
                        } else {
                            prepareMediaPlayerForPlayback();
                        }
                    } else {
                        showError("Could not recognize speech or an unknown error occurred during processing.");
                        restoreOriginalScriptText();
                        updatePlaybackUIState(false, false);
                    }
                    Log.d(TAG_ACTIVITY, "Azure Finally UI: UI update complete.");
                });
            }
        }).exceptionally(ex -> {
            Log.e(TAG_ACTIVITY, "Azure Exceptionally: Critical error in background task ", ex);
            runOnUiThread(() -> {
                Log.d(TAG_ACTIVITY, "Azure Exceptionally UI: Hiding ProgressBar due to critical error.");
                if (pgbAzureProcessing != null) {
                    pgbAzureProcessing.setVisibility(View.GONE);
                }
                if(btnBackward != null) btnBackward.setEnabled(this.lastCanGoPreviousState);
                if(btnForward != null) btnForward.setEnabled(this.lastCanGoNextState);
                if(imgClose != null) { imgClose.setEnabled(true); imgClose.setAlpha(1.0f); }
                if(imgHome != null) { imgHome.setEnabled(true); imgHome.setAlpha(1.0f); }

                showError("Critical error in Azure background task: " + ex.getMessage());
                restoreOriginalScriptText();
                updatePlaybackUIState(false, false);
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
        final int TARGET_SAMPLE_RATE_AZURE = 16000;
        int actualOutputSampleRate = 8000;
        final int TARGET_CHANNELS_AZURE = 1;
        final int TARGET_BIT_DEPTH_AZURE = 16;

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
                    Log.d(TAG_ACTIVITY, "transcodeToWav: Found audio track: " + mime + ", Input Format: " + inputFormat);
                    if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        Log.d(TAG_ACTIVITY, "transcodeToWav: Original Sample Rate: " + inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                    }
                    if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        Log.d(TAG_ACTIVITY, "transcodeToWav: Original Channel Count: " + inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                    }
                    break;
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                Log.e(TAG_ACTIVITY, "transcodeToWav: No audio track found in " + inputPath);
                if (fos != null) fos.close();
                if (outputFile.exists()) outputFile.delete();
                return null;
            }
            extractor.selectTrack(audioTrackIndex);

            String inputMime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (inputMime == null) {
                Log.e(TAG_ACTIVITY, "transcodeToWav: Could not get MIME type from inputFormat.");
                if (fos != null) fos.close();
                if (outputFile.exists()) outputFile.delete();
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
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufIndex);
                        inputBuffer.clear();
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            Log.d(TAG_ACTIVITY, "transcodeToWav: End of input data from extractor (Input EOS).");
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
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufIndex);

                    if (bufferInfo.size > 0 && outputBuffer != null) {
                        byte[] pcmChunk = new byte[bufferInfo.size];
                        outputBuffer.get(pcmChunk);
                        outputBuffer.clear();
                        fos.write(pcmChunk);
                        totalPcmBytesWritten += pcmChunk.length;
                    }
                    decoder.releaseOutputBuffer(outputBufIndex, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG_ACTIVITY, "transcodeToWav: Received Output EOS flag from decoder.");
                        outputEos = true;
                    }
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG_ACTIVITY, "transcodeToWav: Decoder output buffers changed.");
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    actualOutputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int actualOutputChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    Log.d(TAG_ACTIVITY, "transcodeToWav: Decoder output format changed to: " + newFormat +
                            " (Actual SampleRate: " + actualOutputSampleRate + ", Actual Channels: " + actualOutputChannels + ")");
                    if (actualOutputSampleRate != TARGET_SAMPLE_RATE_AZURE || actualOutputChannels != TARGET_CHANNELS_AZURE) {
                        Log.w(TAG_ACTIVITY, "transcodeToWav: WARNING: Output PCM format is " + actualOutputSampleRate + "Hz/" + actualOutputChannels + "ch. " +
                                "Ideal for Azure Pronunciation Assessment is " + TARGET_SAMPLE_RATE_AZURE + "Hz/" + TARGET_CHANNELS_AZURE + "ch. " +
                                "Resampling/remixing might be needed for optimal results if not already done.");
                    }
                }
            }

            fos.flush();
            fos.close();
            fos = null;

            randomAccessFile = new RandomAccessFile(outputFile, "rw");
            writeWavHeader(randomAccessFile, totalPcmBytesWritten,
                    actualOutputSampleRate, TARGET_CHANNELS_AZURE, TARGET_BIT_DEPTH_AZURE);
            randomAccessFile.close();
            randomAccessFile = null;

            Log.i(TAG_ACTIVITY, "transcodeToWav: Transcoding successful: " + outputFile.getAbsolutePath() +
                    ", PCM Size: " + totalPcmBytesWritten + " bytes, Output Sample Rate for WAV: " + actualOutputSampleRate);
            return outputFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG_ACTIVITY, "transcodeToWav: Error during transcoding ", e);
            if (outputFile.exists()) {
                if (outputFile.delete()) {
                    Log.d(TAG_ACTIVITY, "transcodeToWav: Deleted incomplete output file.");
                }
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
                Log.e(TAG_ACTIVITY, "transcodeToWav: Error releasing resources in finally block ", e);
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
        } else if (numBytes == 8 && Long.BYTES >= 8) {
            buffer.putLong(value);
        } else {
            Log.e(TAG_ACTIVITY, "longToByteArray: Unsupported numBytes for long conversion: " + numBytes);
            if (numBytes == 4) buffer.putInt((int)value);
            else if (numBytes == 2) buffer.putShort((short)value);
        }
        return buffer.array();
    }

    private void highlightDifferencesSimple(String originalText, String recognizedText) {
        if (txtScriptToRepeat == null || originalText == null || recognizedText == null) {
            Log.w(TAG_ACTIVITY, "highlightDifferencesSimple: Invalid input (TextView, originalText, or recognizedText is null).");
            restoreOriginalScriptText();
            return;
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder(originalText);
        if (originalText.trim().isEmpty()) {
            txtScriptToRepeat.setText(spannable);
            return;
        }

        String[] originalWords = originalText.trim().toLowerCase(Locale.ROOT).split("\\s+");
        String[] recognizedWords = recognizedText.trim().isEmpty() ? new String[0] : recognizedText.trim().toLowerCase(Locale.ROOT).split("\\s+");

        int currentSearchStartOffsetInOriginal = 0;
        int recIdx = 0;

        Pattern originalWordPattern = Pattern.compile("\\b[a-zA-Z0-9]+(?:['‘’][a-zA-Z0-9]+)*\\b");
        Matcher originalMatcher = originalWordPattern.matcher(originalText);

        while (originalMatcher.find(currentSearchStartOffsetInOriginal)) {
            String displayWord = originalMatcher.group(0);
            String cleanedDisplayWordLower = displayWord.replaceAll("[^a-zA-Z0-9']", "").toLowerCase(Locale.ROOT);

            int startOriginalDisplay = originalMatcher.start();
            int endOriginalDisplay = originalMatcher.end();

            boolean matchFound = false;
            if (recIdx < recognizedWords.length) {
                String cleanedRecWord = recognizedWords[recIdx].replaceAll("[^a-zA-Z0-9']", "").toLowerCase(Locale.ROOT);
                if (cleanedDisplayWordLower.equals(cleanedRecWord) && !cleanedDisplayWordLower.isEmpty()) {
                    matchFound = true;
                }
            }

            if (!matchFound) {
                spannable.setSpan(new ForegroundColorSpan(Color.RED), startOriginalDisplay, endOriginalDisplay, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (matchFound) {
                recIdx++;
            }
            currentSearchStartOffsetInOriginal = endOriginalDisplay;
        }
        txtScriptToRepeat.setText(spannable);
    }


    private void highlightDifferencesAzure(String originalText, PronunciationAssessmentResult assessmentResult, String recognizedTranscriptOverall) {
        if (txtScriptToRepeat == null || originalText == null || assessmentResult == null) {
            Log.e(TAG_ACTIVITY, "highlightDifferencesAzure: Invalid input (TextView, originalText, or assessmentResult is null).");
            restoreOriginalScriptText();
            return;
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder(originalText);
        List<WordLevelTimingResult> assessedWords = assessmentResult.getWords();

        if (assessedWords == null || assessedWords.isEmpty()) {
            Log.w(TAG_ACTIVITY, "No word-level data from PronunciationAssessmentResult for Azure highlighting.");
            if (recognizedTranscriptOverall != null && !recognizedTranscriptOverall.isEmpty()) {
                highlightDifferencesSimple(originalText, recognizedTranscriptOverall);
            } else {
                restoreOriginalScriptText();
            }
            return;
        }

        Log.i(TAG_ACTIVITY, "HighlightAzure: Starting highlighting. Number of Azure assessed words: " + assessedWords.size());
        Log.d(TAG_ACTIVITY, "HighlightAzure: Original display text: \"" + originalText + "\"");

        int searchStartIndexInOriginalDisplay = 0;

        for (WordLevelTimingResult assessedWordData : assessedWords) {
            String wordFromAzure = assessedWordData.getWord();
            String errorType = assessedWordData.getErrorType();
            double accuracyScore = assessedWordData.getAccuracyScore();

            Log.d(TAG_ACTIVITY, "HighlightAzure: Processing Azure word: '" + wordFromAzure +
                    "', ErrorType: " + errorType + ", AccuracyScore: " + accuracyScore);

            if ("Insertion".equalsIgnoreCase(errorType)) {
                Log.d(TAG_ACTIVITY, "HighlightAzure: Skipping 'Insertion' type word: '" + wordFromAzure + "'");
                continue;
            }

            boolean foundThisAzureWordInOriginal = false;
            Pattern originalWordPattern = Pattern.compile("\\b[a-zA-Z0-9]+(?:['‘’][a-zA-Z0-9]+)*\\b");
            Matcher matcher = originalWordPattern.matcher(originalText);

            while (matcher.find(searchStartIndexInOriginalDisplay)) {
                String originalDisplayWordSegment = matcher.group(0);
                String cleanedOriginalDisplayWordLower = originalDisplayWordSegment.replaceAll("[^a-zA-Z0-9']", "").toLowerCase(Locale.ROOT);
                String cleanedWordFromAzureLower = wordFromAzure.toLowerCase(Locale.ROOT);

                Log.v(TAG_ACTIVITY, "HighlightAzure:   Comparing Azure '" + cleanedWordFromAzureLower +
                        "' with original display (cleaned) '" + cleanedOriginalDisplayWordLower +
                        "' (from original segment '" + originalDisplayWordSegment + "' at " + matcher.start() + "-" + matcher.end() + ")");

                if (cleanedOriginalDisplayWordLower.equals(cleanedWordFromAzureLower) && !cleanedOriginalDisplayWordLower.isEmpty()) {
                    int startInOriginalDisplay = matcher.start();
                    int endInOriginalDisplay = matcher.end();

                    Log.d(TAG_ACTIVITY, "HighlightAzure:   MATCH! Original display segment: '" + originalDisplayWordSegment + "' with Azure word: '" + wordFromAzure + "'");

                    boolean needsHighlightRed = false;
                    if ("Mispronunciation".equalsIgnoreCase(errorType)) {
                        needsHighlightRed = true;
                    } else if ("Omission".equalsIgnoreCase(errorType)) {
                        needsHighlightRed = true;
                    } else if (accuracyScore < 60.0 && !"None".equalsIgnoreCase(errorType)) {
                        needsHighlightRed = true;
                    }

                    if (needsHighlightRed) {
                        spannable.setSpan(new ForegroundColorSpan(Color.RED), startInOriginalDisplay, endInOriginalDisplay, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        Log.i(TAG_ACTIVITY, "HighlightAzure:     HIGHLIGHTING RED: '" + originalDisplayWordSegment + "' due to Error: " + errorType + ", Score: " + accuracyScore);
                    } else {
                        Log.d(TAG_ACTIVITY, "HighlightAzure:     NOT HIGHLIGHTING RED (OK/None): '" + originalDisplayWordSegment + "'");
                    }

                    searchStartIndexInOriginalDisplay = endInOriginalDisplay;
                    foundThisAzureWordInOriginal = true;
                    break;
                }
            }

            if (!foundThisAzureWordInOriginal) {
                Log.w(TAG_ACTIVITY, "HighlightAzure:   COULD NOT FIND Azure word '" + wordFromAzure +
                        "' (cleaned: '" + wordFromAzure.toLowerCase(Locale.ROOT) + "') in the remainder of original display text (from index: " + searchStartIndexInOriginalDisplay + "). ");
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
                Log.w(TAG_CALLBACK, "File is empty (0 bytes): " + filePath + ". Azure may report an error or no match.");
            }
            this.fileStream = new FileInputStream(audioFile);
            Log.d(TAG_CALLBACK, "FileInputStream opened for: " + filePath + ", size: " + audioFile.length());
        }

        @Override
        public int read(byte[] dataBuffer) {
            try {
                if (this.fileStream == null) {
                    Log.e(TAG_CALLBACK, "read() called but fileStream is null.");
                    return 0;
                }
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
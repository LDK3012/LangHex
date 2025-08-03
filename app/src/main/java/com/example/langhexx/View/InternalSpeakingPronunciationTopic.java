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
import android.util.Pair; // QUAN TRỌNG: Đảm bảo import này nếu dùng Pair
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

import com.example.langhexx.BuildConfig;
import com.example.langhexx.Controller.SpeakingPronunciationController;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;


public class InternalSpeakingPronunciationTopic extends AppCompatActivity implements SpeakingContract.PronunciationView {

    private static final String TAG_ACTIVITY = "InternalSpeakingVoiceTopic";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 201;
    private static final String AZURE_SPEECH_KEY = BuildConfig.AZURE_STT_API_KEY;
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
    private List<Integer> currentRedWordIndices = null;

    private SpeechConfig azureSpeechConfig;

    private static final boolean USE_WAV_TRANSCODING_FOR_AZURE = true;
    private static final int AZURE_RECOGNITION_TIMEOUT_SECONDS = 60;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pronunciation_learning);

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

        controller = new SpeakingPronunciationController(this, getApplicationContext(), levelName, topicId, topicDisplayTitleStr);
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
            Intent intent = new Intent(InternalSpeakingPronunciationTopic.this, MainActivity.class);
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
                checkAndLoadExistingRecording(); // Attempt to load if path is null but file might exist based on script index
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
            new AlertDialog.Builder(InternalSpeakingPronunciationTopic.this)
                    .setTitle("Confirm Deletion")
                    .setMessage("Are you sure you want to delete this recording and its feedback?")
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
                // Apply any existing loaded feedback to the newly displayed script
                applyFeedbackToScript(this.lastCleanScriptDisplayed, this.currentRedWordIndices);
            } else {
                this.lastCleanScriptDisplayed = "";
                txtScriptToRepeat.setText("");
                // currentRedWordIndices should ideally be nullified if scriptText is null
                // This will be handled by checkAndLoadExistingRecording or delete calls
            }
        }
    }


    @Override
    public void updateScriptCounter(int currentDisplay, int total) {
        if (txtScriptCounter != null) txtScriptCounter.setText(total == 0 ? "0/0" : currentDisplay + "/" + total);
        int oldScriptModelIndex = this.currentScriptModelIndex;
        this.currentScriptModelIndex = currentDisplay - 1; // Assuming currentDisplay is 1-based

        if (oldScriptModelIndex != this.currentScriptModelIndex) { // Only reload if index actually changed
            checkAndLoadExistingRecording(); // This will handle loading audio and feedback
        }
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
            } else if (isCurrentlyRecording) { // While recording, mic button is effectively the stop button
                imgRecord.setEnabled(true);
                imgRecord.setAlpha(1.0f);
            } else { // Azure is busy, disable mic
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

    private String generatePersistentFeedbackFilePath() {
        if (levelName == null || topicId == null || currentScriptModelIndex < 0 || recordingsDir == null ) {
            Log.e(TAG_ACTIVITY, "Cannot generate feedback file path: missing info. Level: " + levelName + ", Topic: " + topicId + ", Index: " + currentScriptModelIndex);
            return null;
        }
        if (!recordingsDir.exists()) {
            // mkdirs() will be called by generatePersistentFilePath if needed, or here if called independently
            if (!recordingsDir.mkdirs()) {
                Log.e(TAG_ACTIVITY, "Cannot create recordings directory during generatePersistentFeedbackFilePath.");
                return null; // Don't show error again if generatePersistentFilePath also failed
            }
        }
        String safeLevelName = levelName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String safeTopicId = topicId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String filename = "rec_" + safeLevelName + "_" + safeTopicId + "_" + currentScriptModelIndex + "_feedback.txt";
        return new File(recordingsDir, filename).getAbsolutePath();
    }

    private void checkAndLoadExistingRecording() {
        if (isCurrentlyRecording || (pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE) ) {
            Log.d(TAG_ACTIVITY, "checkAndLoadExistingRecording: currently recording or Azure processing, skipping.");
            return;
        }

        String persistentPath = generatePersistentFilePath();
        Log.d(TAG_ACTIVITY, "checkAndLoadExistingRecording: Checking for script index " + currentScriptModelIndex + ", audio path: " + persistentPath);

        pausePlayback();
        releaseMediaPlayer();

        this.currentRecordingFilePath = null;
        this.currentRedWordIndices = null; // Reset current feedback state

        if (persistentPath != null) {
            File existingRecording = new File(persistentPath);
            if (existingRecording.exists() && existingRecording.length() > 0) {
                Log.i(TAG_ACTIVITY, "Found existing recording for script index " + currentScriptModelIndex + ": " + persistentPath);
                this.currentRecordingFilePath = persistentPath;

                // Load feedback for this recording
                this.currentRedWordIndices = loadFeedbackData(); // This will be null if no feedback file or empty
                if (this.lastCleanScriptDisplayed != null && !this.lastCleanScriptDisplayed.isEmpty()) {
                    applyFeedbackToScript(this.lastCleanScriptDisplayed, this.currentRedWordIndices);
                } else {
                    Log.w(TAG_ACTIVITY, "lastCleanScriptDisplayed is null or empty when loading recording, cannot apply feedback styling yet.");
                    if(txtScriptToRepeat != null) txtScriptToRepeat.setText(""); // Clear the text view for now
                }
                prepareMediaPlayerForPlayback(); // This updates UI for playback
            } else {
                Log.i(TAG_ACTIVITY, "No existing recording for script index " + currentScriptModelIndex +
                        (existingRecording.exists() ? " (empty file, will be deleted)" : " (file does not exist)"));
                if (existingRecording.exists() && existingRecording.length() == 0) {
                    if(existingRecording.delete()){ Log.d(TAG_ACTIVITY, "Deleted empty recording file: " + persistentPath); }
                }
                this.currentRecordingFilePath = null;
                deleteAssociatedFeedbackFile(); // Ensure feedback file is also gone
                restoreOriginalScriptText(); // Show plain script (will also clear coloring)
                updatePlaybackUIState(false, false);
            }
        } else {
            Log.w(TAG_ACTIVITY, "checkAndLoadExistingRecording: Could not generate persistentPath for audio.");
            this.currentRecordingFilePath = null;
            deleteAssociatedFeedbackFile(); // Attempt to delete feedback even if audio path gen fails
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

        restoreOriginalScriptText();    // Display plain script, clearing any previous red highlights
        deleteAssociatedFeedbackFile(); // Delete any old feedback file for this script item; currentRedWordIndices is nulled inside

        String newRecordingPath = generatePersistentFilePath();
        if (newRecordingPath == null) {
            showError("Could not determine recording path. Cannot start recording.");
            return;
        }

        pausePlayback(); // Stop any playback if user quickly switches from play to record
        releaseMediaPlayer(); // Release player resources

        // Delete any old audio file that might exist for THIS specific script index
        File oldAudioFileForThisScript = new File(newRecordingPath);
        if (oldAudioFileForThisScript.exists()) {
            if (oldAudioFileForThisScript.delete()) {
                Log.i(TAG_ACTIVITY, "Deleted old audio recording to record new: " + newRecordingPath);
            } else {
                Log.w(TAG_ACTIVITY, "Could not delete old audio recording: " + newRecordingPath + ". Overwriting if possible.");
            }
        }
        this.currentRecordingFilePath = newRecordingPath; // Set path for the new recording

        // Ensure recordings directory exists (should be redundant if generatePersistentFilePath worked)
        if (recordingsDir == null || !recordingsDir.exists()) {
            this.recordingsDir = new File(getFilesDir(), "user_voice_recordings");
            if (!recordingsDir.exists() && !recordingsDir.mkdirs()) {
                showError("Error creating storage directory. Cannot record.");
                this.currentRecordingFilePath = null; // Nullify path if dir creation fails
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
            File failedFile = new File(this.currentRecordingFilePath); // Check if a problematic file was created
            if (failedFile.exists()) {
                failedFile.delete();
            }
            this.currentRecordingFilePath = null; // Nullify path on failure
            isCurrentlyRecording = false;
            releaseMediaRecorder();
            updateRecordingUIState(false); // Reset UI
        }
    }

    @Override
    public void stopRecordingUI() {
        if (!isCurrentlyRecording || mediaRecorder == null) {
            Log.d(TAG_ACTIVITY, "Stop recording called but not currently recording or mediaRecorder is null.");
            // If not recording, ensure UI reflects existing state if any
            String persistentPath = generatePersistentFilePath();
            boolean hasFile = persistentPath != null && new File(persistentPath).exists() && new File(persistentPath).length() > 0;

            if (hasFile) {
                // If mediaPlayer is null or path changed, re-prepare
                if(this.currentRecordingFilePath == null || !this.currentRecordingFilePath.equals(persistentPath) || mediaPlayer == null){
                    this.currentRecordingFilePath = persistentPath; // Ensure current path is set
                    if (new File(persistentPath).length() > 0) {
                        prepareMediaPlayerForPlayback();
                    } else { // File exists but is empty
                        updatePlaybackUIState(false, false);
                    }
                } else { // Path is current, media player might be paused
                    updatePlaybackUIState(mediaPlayer != null && mediaPlayer.isPlaying(), true);
                }
            } else { // No valid file exists
                updatePlaybackUIState(false, false);
            }
            isCurrentlyRecording = false; // Ensure state is correct
            updateRecordingUIState(false); // Update general recording button state
            return;
        }

        String savedFilePath = this.currentRecordingFilePath; // Keep a reference to the path being recorded to

        try {
            mediaRecorder.stop();
            Log.d(TAG_ACTIVITY, "MediaRecorder stopped successfully. File: " + savedFilePath);
            File checkFile = new File(savedFilePath);
            if(!checkFile.exists() || checkFile.length() == 0){
                Log.e(TAG_ACTIVITY, "MediaRecorder.stop() successful but file does not exist or is empty: " + savedFilePath);
                // This indicates a serious issue with file saving by MediaRecorder
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
            this.currentRecordingFilePath = null; // Nullify path as saving failed
            savedFilePath = null; // Ensure this is also null for logic below
        } finally {
            releaseMediaRecorder(); // Always release recorder
            isCurrentlyRecording = false; // Update recording state

            // updateRecordingUIState(false) is called implicitly by processRecordingWithAzure or explicitly if no Azure
            // The UI for playback controls or mic button will be shown based on file existence after this.

            if (savedFilePath != null && new File(savedFilePath).exists() && new File(savedFilePath).length() > 0) {
                this.currentRecordingFilePath = savedFilePath; // Confirm path is set
                if (azureSpeechConfig != null) {
                    processRecordingWithAzure(this.currentRecordingFilePath);
                } else {
                    showError("Azure service is not ready. Cannot analyze.");
                    restoreOriginalScriptText(); // Show plain script
                    prepareMediaPlayerForPlayback(); // Prepare for playback without Azure analysis
                }
            } else {
                Log.w(TAG_ACTIVITY, "Invalid recording file after stop: " + (savedFilePath != null ? savedFilePath : "null") + ". Cannot play or analyze.");
                this.currentRecordingFilePath = null; // Ensure path is null
                restoreOriginalScriptText(); // Show plain script
                deleteAssociatedFeedbackFile(); // Clean up any lingering feedback file from a previous attempt for this script
                updatePlaybackUIState(false, false); // Update UI to show no recording
                updateRecordingUIState(false); // Ensure record button is in correct state
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
            if(this.currentRecordingFilePath != null) { // If path was set but file is bad
                File f = new File(this.currentRecordingFilePath);
                if (f.exists() && f.length() == 0) {
                    Log.w(TAG_ACTIVITY, "Deleting empty file during prepareMediaPlayer: " + this.currentRecordingFilePath);
                    if (f.delete()) { Log.d(TAG_ACTIVITY, "Empty file deleted successfully."); }
                }
            }
            this.currentRecordingFilePath = null; // Nullify path if file is unusable
            // Don't delete feedback here, as audio file might be temporarily unavailable or corrupted
            // checkAndLoadExistingRecording is responsible for syncing feedback file if audio is truly gone
            updatePlaybackUIState(false, false);
            return;
        }

        releaseMediaPlayer(); // Release any existing player first

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(currentRecordingFilePath);
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG_ACTIVITY, "MediaPlayer prepared. Duration: " + mp.getDuration() + " ms for " + currentRecordingFilePath);
                if (mp.getDuration() <= 0) { // Invalid duration
                    Log.e(TAG_ACTIVITY, "MediaPlayer prepared with invalid duration: " + mp.getDuration() + ". File may be corrupted: " + currentRecordingFilePath);
                    showError("Cannot play recording, file may be corrupted.");
                    releaseMediaPlayer();
                    // Consider deleting the corrupted audio and its feedback here if this is a persistent issue
                    // For now, just nullify path and update UI
                    // deleteCurrentRecording(); // This might be too aggressive
                    File badFile = new File(currentRecordingFilePath);
                    if(badFile.exists()) badFile.delete();
                    deleteAssociatedFeedbackFile();
                    this.currentRecordingFilePath = null;
                    updatePlaybackUIState(false, false);
                    return;
                }
                if (sbrAudio != null) sbrAudio.setMax(mp.getDuration());
                updatePlayTimeWithMillis(0, mp.getDuration());
                updatePlaybackUIState(false, true); // Not playing, but file exists
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG_ACTIVITY, "MediaPlayer playback completed.");
                isCurrentlyPlaying = false;
                try {
                    if (mediaPlayer != null && mediaPlayer.getDuration() > 0) { // Check if player still valid
                        updatePlayTimeWithMillis(mediaPlayer.getDuration(), mediaPlayer.getDuration());
                        if (sbrAudio != null) sbrAudio.setProgress(sbrAudio.getMax());
                    } else { // Player might have been released or duration became invalid
                        updatePlayTimeWithMillis(0,0);
                        if (sbrAudio != null) sbrAudio.setProgress(0);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG_ACTIVITY, "MediaPlayer state error on completion: " + e.getMessage());
                    updatePlayTimeWithMillis(0,0); // Reset time display
                    if (sbrAudio != null) sbrAudio.setProgress(0);
                }
                updatePlaybackUIState(false, true); // Not playing, but file should still exist
                if(seekBarHandler != null) seekBarHandler.removeCallbacks(updateSeekBarRunnable);
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG_ACTIVITY, "MediaPlayer error: what=" + what + ", extra=" + extra + " for " + currentRecordingFilePath);
                showError("Error playing audio (what=" + what + "). File may be corrupted.");
                releaseMediaPlayer();
                // Aggressively delete corrupted file and its feedback
                File badFile = new File(currentRecordingFilePath);
                if(badFile.exists()) badFile.delete();
                deleteAssociatedFeedbackFile();
                this.currentRecordingFilePath = null;
                updatePlaybackUIState(false, false);
                return true; // Error handled
            });
            mediaPlayer.prepareAsync();
            Log.d(TAG_ACTIVITY, "MediaPlayer.prepareAsync() called for: " + currentRecordingFilePath);
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            Log.e(TAG_ACTIVITY, "MediaPlayer setDataSource or prepareAsync failed for "+currentRecordingFilePath, e);
            showError("Could not load audio for playback: " + e.getMessage());
            releaseMediaPlayer();
            // If loading fails, consider the file problematic
            File problematicFile = new File(currentRecordingFilePath);
            if (problematicFile.exists()) problematicFile.delete();
            deleteAssociatedFeedbackFile();
            this.currentRecordingFilePath = null;
            updatePlaybackUIState(false, false);
        }
    }


    private void startPlayback() {
        if (mediaPlayer == null || currentRecordingFilePath == null || !new File(currentRecordingFilePath).exists() || new File(currentRecordingFilePath).length() == 0) {
            if (currentRecordingFilePath != null && new File(currentRecordingFilePath).exists() && mediaPlayer == null) {
                Log.d(TAG_ACTIVITY, "startPlayback: MediaPlayer null but file exists, re-preparing...");
                prepareMediaPlayerForPlayback(); // This will attempt to re-initialize
                showToast("Preparing audio..."); // Inform user
                return;
            } else { // No valid file or path
                showToast("No valid recording to play.");
                if (currentRecordingFilePath != null && new File(currentRecordingFilePath).exists() && new File(currentRecordingFilePath).length() == 0) {
                    Log.w(TAG_ACTIVITY, "Attempted to play zero-length file, deleting: " + currentRecordingFilePath);
                    File f = new File(currentRecordingFilePath); if(f.exists() && f.delete()){ /* log */ }
                    deleteAssociatedFeedbackFile();
                    this.currentRecordingFilePath = null;
                }
                updatePlaybackUIState(false, false);
                return;
            }
        }

        boolean isPrepared = false;
        try {
            // A simple check to see if player is in a usable state (has duration)
            mediaPlayer.getDuration(); // Throws IllegalStateException if not prepared
            isPrepared = true;
        } catch (IllegalStateException e) {
            Log.w(TAG_ACTIVITY, "startPlayback: MediaPlayer not ready (IllegalStateException on getDuration). File: " + currentRecordingFilePath, e);
            // If player is not null but not playing, it might be preparing or in an error state
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                // It might be preparing async, or failed. prepareMediaPlayerForPlayback will handle bad states.
                showToast("Audio not ready. Please try again in a moment.");
                // If it's consistently not preparing, prepareMediaPlayerForPlayback might delete the file if corrupted
            } else if (mediaPlayer == null) { // Should not happen if first check passed, but defensive
                prepareMediaPlayerForPlayback();
            }
            return;
        }


        if (isPrepared && !mediaPlayer.isPlaying()) {
            try {
                int currentSeekBarPos = (sbrAudio != null) ? sbrAudio.getProgress() : 0;
                // If seekbar is at the end (or beyond due to rounding), reset to start
                if (mediaPlayer.getDuration() > 0 && currentSeekBarPos >= mediaPlayer.getDuration()) {
                    currentSeekBarPos = 0;
                }
                mediaPlayer.seekTo(currentSeekBarPos);

                mediaPlayer.start();
                isCurrentlyPlaying = true;
                updatePlaybackUIState(true, true); // Playing, file exists
                if (seekBarHandler != null) seekBarHandler.post(updateSeekBarRunnable);
                Log.d(TAG_ACTIVITY, "Started playback from position " + currentSeekBarPos);
            } catch (IllegalStateException e) {
                Log.e(TAG_ACTIVITY, "MediaPlayer.start() failed (IllegalStateException): " + e.getMessage() + ". File: " + currentRecordingFilePath, e);
                showError("Playback failed. Audio may be corrupted or still preparing.");
                // Attempt to recover by re-preparing. This might delete the file if it's truly bad.
                releaseMediaPlayer();
                prepareMediaPlayerForPlayback();
            }
        } else if (!isPrepared) {
            // This case should ideally be caught by the getDuration check earlier
            showToast("Audio is still preparing, please wait...");
        } else if (mediaPlayer.isPlaying()) {
            Log.d(TAG_ACTIVITY, "Playback already in progress."); // Or pause it, depending on desired toggle behavior
        }
    }


    private void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
                isCurrentlyPlaying = false;
                updatePlaybackUIState(false, true); // Not playing, file exists
                if(seekBarHandler != null) seekBarHandler.removeCallbacks(updateSeekBarRunnable);
                Log.d(TAG_ACTIVITY, "Paused playback.");
            } catch (IllegalStateException e) {
                Log.e(TAG_ACTIVITY, "MediaPlayer.pause() failed: " + e.getMessage());
                // Player might be in a bad state.
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
        isCurrentlyPlaying = false; // Ensure state is updated
        if(seekBarHandler != null) seekBarHandler.removeCallbacksAndMessages(null); // Clear pending updates
    }


    private void deleteAssociatedFeedbackFile() {
        String feedbackFilePath = generatePersistentFeedbackFilePath();
        if (feedbackFilePath != null) {
            File feedbackFile = new File(feedbackFilePath);
            if (feedbackFile.exists()) {
                if (feedbackFile.delete()) {
                    Log.i(TAG_ACTIVITY, "Associated feedback file deleted: " + feedbackFilePath);
                } else {
                    Log.w(TAG_ACTIVITY, "Could not delete associated feedback file: " + feedbackFilePath);
                }
            }
        }
        this.currentRedWordIndices = null; // Clear from memory as well
    }

    private void deleteCurrentRecording() {
        pausePlayback();
        releaseMediaPlayer();

        String pathToDelete = this.currentRecordingFilePath; // Path of current audio
        if (pathToDelete == null) { // If not set (e.g., after an error), try to generate it for current script
            pathToDelete = generatePersistentFilePath();
        }

        this.currentRecordingFilePath = null; // Clear current audio path from instance variable

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
                // This is okay if pathToDelete was generated but file never existed or was already deleted
                Log.d(TAG_ACTIVITY, "Recording file not found to delete (already deleted or never existed): " + pathToDelete);
            }
        } else {
            Log.w(TAG_ACTIVITY, "deleteCurrentRecording: No valid audio file path to delete for current script.");
        }

        deleteAssociatedFeedbackFile(); // Delete the corresponding feedback file

        isCurrentlyPlaying = false; // Update playback state
        updatePlaybackUIState(false, false); // Update UI to show no recording
        restoreOriginalScriptText(); // This will display the clean script, clearing any red highlights

        if (sbrAudio != null) {
            sbrAudio.setProgress(0);
            sbrAudio.setMax(100); // Default max for seekbar when no audio
        }
        if (txtTime != null) txtTime.setText("00:00 / 00:00");
    }


    @Override
    public void updateRecordingUIState(boolean isRecordingActiveState) {
        // This method is primarily for the record button's visual state during active recording.
        // Overall UI (playback controls vs mic button) is handled by updatePlaybackUIState.
        boolean isAzureProcessing = pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE;
        if (isAzureProcessing) {
            // If Azure is processing, the mic button should be hidden anyway by updatePlaybackUIState
            return;
        }

        if (this.isCurrentlyRecording) { // isRecordingActiveState should be true
            if (imgRecord != null) {
                imgRecord.setImageResource(R.drawable.voice_icon_pause_); // Recording icon
                if (pulsatingAnimation != null && (imgRecord.getAnimation() == null || !imgRecord.getAnimation().hasStarted() || imgRecord.getAnimation().hasEnded())) {
                    imgRecord.startAnimation(pulsatingAnimation);
                }
                imgRecord.setVisibility(View.VISIBLE); // Ensure visible
                imgRecord.setEnabled(true); // Enable to stop recording
            }
            if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.GONE); // Hide playback

            // Disable navigation while recording
            if(btnBackward != null) btnBackward.setEnabled(false);
            if(btnForward != null) btnForward.setEnabled(false);
            if(imgClose != null) {imgClose.setEnabled(false); imgClose.setAlpha(0.5f);}
            if(imgHome != null) {imgHome.setEnabled(false); imgHome.setAlpha(0.5f);}

        } else { // Not recording (isRecordingActiveState should be false)
            if (imgRecord != null) {
                imgRecord.setImageResource(R.drawable.micro); // Default mic icon
                if (pulsatingAnimation != null && imgRecord.getAnimation() != null) {
                    imgRecord.clearAnimation(); // Stop animation
                }
                // Visibility of imgRecord vs layoutPlaybackControls is handled by updatePlaybackUIState
            }
            // Enable navigation etc. (also handled/overridden by updatePlaybackUIState based on file presence)
            // The main call to updatePlaybackUIState after recording stops will set the correct overall UI.
            boolean hasFile = this.currentRecordingFilePath != null && new File(this.currentRecordingFilePath).exists() && new File(this.currentRecordingFilePath).length() > 0;
            updatePlaybackUIState(this.isCurrentlyPlaying, hasFile);
        }
    }


    @Override
    public void updatePlaybackUIState(boolean isMediaPlayerPlaying, boolean hasAudioFile) {
        this.isCurrentlyPlaying = isMediaPlayerPlaying; // Sync internal state
        boolean isAzureProcessing = pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE;

        if (isAzureProcessing) {
            if (imgRecord != null) {
                if (imgRecord.getAnimation() != null) imgRecord.clearAnimation();
                imgRecord.setVisibility(View.GONE); // Hide record button
            }
            if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.GONE); // Hide playback controls
            // Disable navigation buttons during Azure processing
            if(btnBackward != null) btnBackward.setEnabled(false);
            if(btnForward != null) btnForward.setEnabled(false);
            if(imgClose != null) { imgClose.setEnabled(false); imgClose.setAlpha(0.5f); }
            if(imgHome != null) { imgHome.setEnabled(false); imgHome.setAlpha(0.5f); }
            return; // UI is locked for Azure processing
        }

        // If currently recording, UI is different (handled by updateRecordingUIState, but this can be a fallback)
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
        } else { // Not recording and not Azure processing
            if (imgRecord != null && imgRecord.getAnimation() != null) { // Stop animation if any
                imgRecord.clearAnimation();
            }

            if (hasAudioFile) {
                if (imgRecord != null) imgRecord.setVisibility(View.GONE); // Hide mic button
                if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.VISIBLE); // Show playback

                boolean canInteractWithPlayback = (mediaPlayer != null); // Basic check
                try { // More robust check
                    if(mediaPlayer != null) mediaPlayer.getDuration(); else canInteractWithPlayback = false;
                } catch (IllegalStateException e) { canInteractWithPlayback = false; }


                if (imgPlayAudio != null) {
                    imgPlayAudio.setImageResource(isMediaPlayerPlaying ? R.drawable.ic_pause : R.drawable.icon_play_audio);
                    imgPlayAudio.setEnabled(canInteractWithPlayback);
                    imgPlayAudio.setAlpha(canInteractWithPlayback ? 1.0f : 0.5f);
                }
                if (imgDelete != null) { // Delete button always enabled if file exists
                    imgDelete.setEnabled(true);
                    imgDelete.setAlpha(1.0f);
                }
                if (sbrAudio != null) {
                    sbrAudio.setEnabled(canInteractWithPlayback);
                    if (!canInteractWithPlayback && mediaPlayer == null) { // Reset seekbar if player is gone
                        sbrAudio.setMax(100); sbrAudio.setProgress(0);
                        if(txtTime != null) txtTime.setText("00:00 / 00:00");
                    }
                }
            } else { // No audio file
                if (imgRecord != null) {
                    imgRecord.setImageResource(R.drawable.micro); // Show mic icon
                    imgRecord.setVisibility(View.VISIBLE);
                    imgRecord.setEnabled(true); // Enable for new recording
                    imgRecord.setAlpha(1.0f);
                }
                if (layoutPlaybackControls != null) layoutPlaybackControls.setVisibility(View.GONE); // Hide playback

                if (sbrAudio != null) {
                    sbrAudio.setEnabled(false);
                    sbrAudio.setProgress(0);
                    sbrAudio.setMax(100); // Default max
                }
                if (txtTime != null) txtTime.setText("00:00 / 00:00"); // Reset time display
            }

            // Enable navigation buttons based on controller state
            if (imgClose != null) {imgClose.setEnabled(true); imgClose.setAlpha(1.0f);}
            if (imgHome != null) {imgHome.setEnabled(true); imgHome.setAlpha(1.0f);}
            if (btnBackward != null) {
                btnBackward.setEnabled(this.lastCanGoPreviousState);
                // Update background based on enabled state
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
            sbrAudio.setMax(max > 0 ? max : 100); // Ensure max is positive
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
        long validTotalMillis = Math.max(0, totalMillis); // Ensure non-negative
        long validCurrentMillis = Math.max(0, Math.min(currentMillis, validTotalMillis)); // Ensure current is within 0 and total

        String currentTimeStr = String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(validCurrentMillis),
                TimeUnit.MILLISECONDS.toSeconds(validCurrentMillis) % 60
        );
        String totalTimeStr = String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(validTotalMillis),
                TimeUnit.MILLISECONDS.toSeconds(validTotalMillis) % 60
        );
        updatePlayTime(currentTimeStr, totalTimeStr); // Update the TextView

        if (sbrAudio != null) {
            boolean canUpdateSeekBar = false;
            try {
                if (mediaPlayer != null && validTotalMillis > 0) { // MediaPlayer must be valid and have duration
                    canUpdateSeekBar = true;
                }
            } catch (IllegalStateException e) {
                // MediaPlayer might not be in a state to get duration (e.g., not prepared, released)
                Log.w(TAG_ACTIVITY, "updatePlayTimeWithMillis: MediaPlayer not ready for sbrAudio update.");
            }

            if (canUpdateSeekBar) {
                if (!sbrAudio.isPressed()) { // Only update if user is not dragging the seekbar
                    if(sbrAudio.getMax() != (int)validTotalMillis) sbrAudio.setMax((int)validTotalMillis); // Update max if different
                    sbrAudio.setProgress((int) validCurrentMillis);
                }
            } else if (validTotalMillis <= 0) { // If no valid total duration (e.g., no media or bad media)
                sbrAudio.setMax(100); // Reset to default max
                sbrAudio.setProgress(0); // Reset progress
            }
        }
    }


    private Runnable updateSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isCurrentlyPlaying) { // Check both player and playing state
                try {
                    if (mediaPlayer.isPlaying()) { // Double check if actually playing
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        int duration = mediaPlayer.getDuration();

                        if (duration <= 0) { // Should not happen if prepared correctly
                            Log.e(TAG_ACTIVITY, "Error: duration <= 0 while playing in updateSeekBarRunnable. Stopping playback.");
                            pausePlayback(); // Stop playback to prevent further issues
                            updatePlayTimeWithMillis(0,0); // Reset time display
                            return; // Do not reschedule
                        }
                        updatePlayTimeWithMillis(currentPosition, duration); // Update time and seekbar
                        if (seekBarHandler != null) seekBarHandler.postDelayed(this, 250); // Reschedule
                    } else if (isCurrentlyPlaying) {
                        // isCurrentlyPlaying is true, but mediaPlayer.isPlaying() is false.
                        // This might happen if playback was completed or paused by another event.
                        Log.w(TAG_ACTIVITY, "updateSeekBarRunnable: isCurrentlyPlaying=true but mediaPlayer not playing. State mismatch.");
                        isCurrentlyPlaying = false; // Correct the state
                        updatePlaybackUIState(false, currentRecordingFilePath != null && new File(currentRecordingFilePath).exists());
                        // No reschedule if not truly playing
                    } else { // Not playing (isCurrentlyPlaying is false)
                        if (seekBarHandler != null) seekBarHandler.removeCallbacks(this);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG_ACTIVITY, "MediaPlayer in invalid state while updating seekbar: " + e.getMessage());
                    // If player is in bad state, stop trying to update.
                    isCurrentlyPlaying = false; // Assume not playing
                    updatePlaybackUIState(false, currentRecordingFilePath != null && new File(currentRecordingFilePath).exists());
                    if (seekBarHandler != null) seekBarHandler.removeCallbacks(this);
                } catch (Exception e) { // Catch any other unexpected errors
                    Log.e(TAG_ACTIVITY, "Exception in updateSeekBarRunnable: " + e.getMessage(), e);
                    if (seekBarHandler != null) seekBarHandler.removeCallbacks(this); // Stop updates on error
                }
            } else { // MediaPlayer is null or not supposed to be playing
                if (seekBarHandler != null) seekBarHandler.removeCallbacks(this);
            }
        }
    };


    private void cleanUpAudioResources() {
        Log.d(TAG_ACTIVITY, "Cleaning up audio resources...");
        if (seekBarHandler != null) {
            seekBarHandler.removeCallbacksAndMessages(null); // Clear all pending messages and callbacks
        }
        releaseMediaPlayer(); // Release media player
        releaseMediaRecorder(); // Release media recorder

        if (imgRecord != null && pulsatingAnimation != null && imgRecord.getAnimation() != null) {
            // Check if animation is running before trying to clear it
            if (imgRecord.getAnimation().hasStarted() && !imgRecord.getAnimation().hasEnded()){
                imgRecord.clearAnimation();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG_ACTIVITY, "onStop called.");
        // If recording, auto-stop it. The stopRecordingUI will handle saving and Azure if configured.
        if (isCurrentlyRecording && mediaRecorder != null) {
            Log.w(TAG_ACTIVITY, "Currently recording onStop. Auto-stopping recording.");
            // Calling controller.onMicButtonClicked() might be cleaner if it handles the stop logic
            // For now, directly call stopRecordingUI()
            stopRecordingUI(); // This should save the file and then potentially trigger Azure
        }
        // If playing, pause it.
        if (isCurrentlyPlaying && mediaPlayer != null) {
            Log.d(TAG_ACTIVITY, "Currently playing onStop. Pausing playback.");
            pausePlayback();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG_ACTIVITY, "onDestroy Activity called.");
        cleanUpAudioResources(); // Ensure all resources are released

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
            controller.onDestroy(); // Allow controller to clean up
            controller = null;
        }
        if (pulsatingAnimation != null) {
            pulsatingAnimation.cancel(); // Cancel animation
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
            // If path changed or player is not set up for the current path, re-prepare
            if (!path.equals(oldPath) || mediaPlayer == null) {
                pausePlayback(); // Stop any current playback
                releaseMediaPlayer(); // Release old player
                prepareMediaPlayerForPlayback(); // Prepare for the new/updated path
                // Also try to load feedback for this new path
                this.currentRedWordIndices = loadFeedbackData();
                applyFeedbackToScript(this.lastCleanScriptDisplayed, this.currentRedWordIndices);
            }
        } else { // Path is null or file is invalid
            Log.w(TAG_ACTIVITY, "setRecordingFilePath: path null or file does not exist/is empty. Path: " + path);
            if (path == null && oldPath != null) {
                Log.d(TAG_ACTIVITY, "setRecordingFilePath: new path is null, old path was: " + oldPath + ". This implies deletion or reset.");
            }
            pausePlayback();
            releaseMediaPlayer();
            deleteAssociatedFeedbackFile(); // If path becomes null, associated feedback should be gone
            updatePlaybackUIState(false, false); // Update UI to reflect no valid recording
            restoreOriginalScriptText(); // Show plain script
        }
    }

    // This method seems redundant now as UI updates are more granularly controlled by
    // updatePlaybackUIState, updateRecordingUIState, and Azure processing UI changes.
    // Consider removing if not used elsewhere.
    private void setUiInteraction(boolean allowInteraction) {
        boolean isAzureProcessing = pgbAzureProcessing != null && pgbAzureProcessing.getVisibility() == View.VISIBLE;
        boolean effectiveAllowInteraction = allowInteraction && !isAzureProcessing && !isCurrentlyRecording;

        // Record button specific logic (visibility based on playback controls)
        if(imgRecord != null) {
            boolean shouldMicBeVisible = (layoutPlaybackControls == null || layoutPlaybackControls.getVisibility() == View.GONE);
            imgRecord.setEnabled(effectiveAllowInteraction && shouldMicBeVisible && !isAzureProcessing && !isCurrentlyRecording);
            imgRecord.setAlpha((effectiveAllowInteraction && shouldMicBeVisible && !isAzureProcessing && !isCurrentlyRecording) ? 1.0f : 0.5f);
        }

        boolean hasFileForPlayback = currentRecordingFilePath != null && new File(currentRecordingFilePath).exists() && new File(currentRecordingFilePath).length() > 0;
        boolean canPlaybackControlsBeActive = effectiveAllowInteraction && hasFileForPlayback && !isAzureProcessing && !isCurrentlyRecording;

        if(imgPlayAudio != null) {
            imgPlayAudio.setEnabled(canPlaybackControlsBeActive && mediaPlayer != null);
            imgPlayAudio.setAlpha((canPlaybackControlsBeActive && mediaPlayer != null) ? 1.0f : 0.5f);
        }
        if(imgDelete != null) {
            imgDelete.setEnabled(canPlaybackControlsBeActive); // Delete can be active if file exists and not processing/recording
            imgDelete.setAlpha(canPlaybackControlsBeActive ? 1.0f : 0.5f);
        }
        if(sbrAudio != null) {
            sbrAudio.setEnabled(canPlaybackControlsBeActive && mediaPlayer != null);
        }

        // Navigation buttons
        if(btnBackward != null) {
            btnBackward.setEnabled(effectiveAllowInteraction && lastCanGoPreviousState && !isAzureProcessing && !isCurrentlyRecording);
        }
        if(btnForward != null) {
            btnForward.setEnabled(effectiveAllowInteraction && lastCanGoNextState && !isAzureProcessing && !isCurrentlyRecording);
        }

        // Top bar buttons
        if(imgClose != null) imgClose.setEnabled(allowInteraction && !isAzureProcessing && !isCurrentlyRecording);
        if(imgHome != null) imgHome.setEnabled(allowInteraction && !isAzureProcessing && !isCurrentlyRecording);
    }

    private void restoreOriginalScriptText() {
        if (txtScriptToRepeat != null && this.lastCleanScriptDisplayed != null && !this.lastCleanScriptDisplayed.isEmpty()) {
            txtScriptToRepeat.setText(this.lastCleanScriptDisplayed); // Set plain text
            txtScriptToRepeat.setTextColor(ContextCompat.getColor(this, android.R.color.black)); // Ensure default color
        } else if (txtScriptToRepeat != null) { // If lastCleanScriptDisplayed is null or empty
            txtScriptToRepeat.setText("");
            // txtScriptToRepeat.setTextColor(ContextCompat.getColor(this, android.R.color.black)); // Default color
        }
        // currentRedWordIndices is NOT nulled here. It's managed by load/save/delete operations.
        // This method just resets the visual appearance of the TextView to the plain script.
    }

    private void processRecordingWithAzure(String audioFilePath) {
        if (azureSpeechConfig == null) {
            showError("Azure speech service is not configured. Cannot analyze.");
            restoreOriginalScriptText(); // Show plain script
            // If audio file exists, prepare for playback without Azure analysis
            if (new File(audioFilePath).exists() && new File(audioFilePath).length() > 0) {
                prepareMediaPlayerForPlayback(); // This will call updatePlaybackUIState
            } else {
                updatePlaybackUIState(false, false);
            }
            return;
        }
        if (audioFilePath == null || !new File(audioFilePath).exists() || new File(audioFilePath).length() == 0) {
            showError("Invalid or empty recording file. Cannot analyze.");
            restoreOriginalScriptText();
            deleteAssociatedFeedbackFile(); // Ensure no feedback for bad audio
            updatePlaybackUIState(false, false);
            return;
        }

        final String referenceTextCleaned = this.lastCleanScriptDisplayed // Use the full, uncleaned script for display matching later
                .replaceAll("[\\p{Punct}&&[^'-]]", "") // Clean for Azure's reference text
                .toLowerCase(Locale.ROOT)
                .trim();

        if (referenceTextCleaned.isEmpty()) {
            showError("Reference text is empty. Cannot perform pronunciation assessment.");
            restoreOriginalScriptText();
            // If audio file is good, prepare for playback. No feedback to save.
            if (new File(audioFilePath).exists() && new File(audioFilePath).length() > 0) {
                prepareMediaPlayerForPlayback();
            } else {
                updatePlaybackUIState(false, false);
            }
            saveFeedbackData(null); // Ensure no feedback file is saved/lingers
            return;
        }

        runOnUiThread(() -> {
            Log.d(TAG_ACTIVITY, "processRecordingWithAzure: Starting. Setting UI to processing state.");
            if (pgbAzureProcessing != null) pgbAzureProcessing.setVisibility(View.VISIBLE);

            // Update UI to reflect Azure processing (hide mic/playback, disable nav)
            updatePlaybackUIState(false, false); // isAzureProcessing flag inside will handle it

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
            int actualSampleRateForSdk = 8000; // Default for AMR_NB, will be updated by transcode if successful


            if (USE_WAV_TRANSCODING_FOR_AZURE) {
                Log.i(TAG_ACTIVITY, "Starting transcoding to WAV for file: " + audioFilePath);
                // Pass a Pair to get back path and sample rate
                Pair<String, Integer> transcodeResult = transcodeToWavAndGetSampleRate(audioFilePath);
                tempWavFilePath = transcodeResult.first;
                actualSampleRateForSdk = transcodeResult.second; // This will be target (e.g. 16000) or original if failed/not changed

                if (tempWavFilePath != null && new File(tempWavFilePath).exists()) {
                    effectiveAudioFilePath = tempWavFilePath;
                    Log.i(TAG_ACTIVITY, "Successfully transcoded to WAV: " + effectiveAudioFilePath + " with sample rate: " + actualSampleRateForSdk + "Hz");
                } else {
                    Log.w(TAG_ACTIVITY, "Transcoding to WAV failed. Using original file: " + audioFilePath + ". Analysis quality may be affected.");
                    // If transcode failed, actualSampleRateForSdk should reflect the original if known,
                    // or stick to a default like 8000 for 3GP/AMR-NB if not resampled.
                    // For simplicity, if transcode fails, we assume 8000Hz for AMR-NB which is what MediaRecorder uses.
                    actualSampleRateForSdk = 8000;
                }
            }

            try {
                File audioFile = new File(effectiveAudioFilePath);
                if (!audioFile.exists() || audioFile.length() == 0) {
                    throw new FileNotFoundException("Audio file does not exist or is empty after check/transcode: " + effectiveAudioFilePath);
                }
                Log.i(TAG_ACTIVITY, "Azure Processing: Starting to process file: " + effectiveAudioFilePath + ", Size: " + audioFile.length() + " bytes. Using sample rate for SDK: " + actualSampleRateForSdk + "Hz.");

                pullStreamCallback = new MyPullAudioInputStreamCallback(effectiveAudioFilePath);
                AudioStreamFormat streamFormat;

                // If using transcoded WAV, use PCM format. Otherwise, use compressed.
                if (USE_WAV_TRANSCODING_FOR_AZURE && tempWavFilePath != null && effectiveAudioFilePath.equals(tempWavFilePath)) {
                    short channelsForWav = 1; // Mono
                    short bitDepthForWav = 16; // 16-bit
                    // actualSampleRateForSdk should be the output rate of the transcoder (ideally 16000)
                    streamFormat = AudioStreamFormat.getWaveFormatPCM(actualSampleRateForSdk, bitDepthForWav, channelsForWav);
                    Log.i(TAG_ACTIVITY, "Azure Processing: Using WAV PCM format. SampleRate: " + actualSampleRateForSdk + "Hz, BitDepth: " + bitDepthForWav + ", Channels: " + channelsForWav);
                } else {
                    Log.i(TAG_ACTIVITY, "Azure Processing: Using COMPRESSED AudioStreamFormat (ANY) for " + effectiveAudioFilePath);
                    streamFormat = AudioStreamFormat.getCompressedFormat(AudioStreamContainerFormat.ANY);
                    // Azure SDK might try to infer format. For 3GP AMR-NB, it's typically 8kHz.
                    // If GStreamer isn't available or format isn't directly supported, this might fail.
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
                                PronunciationAssessmentGranularity.Word, true); // Enable syllable level if needed & supported by language
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
                    } else { // Recognized speech, but no pronunciation assessment result (could happen if reference text is too different, etc.)
                        Log.w(TAG_ACTIVITY, "PronunciationAssessmentResult is NULL despite recognized speech. Recognized text: \"" + recognizedText + "\". Reference: \"" + referenceTextCleaned + "\"");
                        if (recognizedText.isEmpty()) {
                            errorForUi = "Speech recognized but no content for assessment.";
                        } else {
                            // Fallback to simple highlighting if no assessment details
                            errorForUi = null; // Not an error per se, but will use simple highlighting.
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

                        String gstreamerErrorHex = "0x29"; // SPXERR_GSTREAMER_NOT_FOUND_ERROR
                        String gstreamerErrorName = "spxerr_gstreamer_not_found_error"; // Name of the error in some SDK versions/logs
                        String gstreamerKeyword = "gstreamer"; // General keyword

                        if (errorCode == com.microsoft.cognitiveservices.speech.CancellationErrorCode.ConnectionFailure ||
                                errorCode == com.microsoft.cognitiveservices.speech.CancellationErrorCode.ServiceUnavailable) {
                            errorForUi = "Connection error or Azure service unavailable. Check network.";
                        } else if (errorCode == com.microsoft.cognitiveservices.speech.CancellationErrorCode.AuthenticationFailure) {
                            errorForUi = "Azure authentication error. Check Speech Key and Region.";
                        } else if (errDetailsLower.contains("audio format") ||
                                errDetailsLower.contains("unsupported") ||
                                errDetailsLower.contains(gstreamerKeyword) || // Check for "gstreamer"
                                errDetailsOriginal.contains(gstreamerErrorHex) || // Check for specific GStreamer error hex
                                errDetailsLower.contains(gstreamerErrorName) ) { // Check for GStreamer error name
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
                        else { // Other cancellation errors
                            errorForUi = "Error from Azure [" + errorCode + "]: " + errDetailsOriginal;
                        }
                    }
                }

            } catch (FileNotFoundException fnfEx) {
                errorForUi = "Recording file to process not found: " + fnfEx.getMessage();
                Log.e(TAG_ACTIVITY, "Azure Processing: FileNotFoundException ", fnfEx);
            } catch (IllegalArgumentException iae) { // Errors from SDK configuration
                errorForUi = "Invalid configuration or parameters for Azure: " + iae.getMessage();
                Log.e(TAG_ACTIVITY, "Azure Processing: IllegalArgumentException ", iae);
            } catch (InterruptedException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
                errorForUi = "Error waiting for Azure result (timeout or canceled): " + ex.getMessage();
                Log.e(TAG_ACTIVITY, "Azure Processing: Exception while waiting for future.get() ", ex);
                if (ex.getCause() != null) { Log.e(TAG_ACTIVITY, "Azure Processing: Caused by: ", ex.getCause()); }
            } catch (Exception e) { // Catch-all for other unexpected issues
                errorForUi = "Unknown error occurred during speech analysis: " + e.getMessage();
                Log.e(TAG_ACTIVITY, "Azure Processing: Generic Exception (Unknown error)", e);
                if (e.getCause() != null) {
                    Log.e(TAG_ACTIVITY, "Azure Processing: Cause of unknown error: ", e.getCause());
                    azureDetailedError += " | Cause: " + e.getCause().getMessage();
                }
            } finally {
                // Close SDK resources
                if (speechResult != null) { try { speechResult.close(); } catch (Exception e) { Log.e(TAG_ACTIVITY, "Error closing speechResult", e); } }
                if (recognizer != null) { try { recognizer.close(); } catch (Exception e) { Log.e(TAG_ACTIVITY, "Error closing recognizer", e); } }
                if (audioConfig != null) { try { audioConfig.close(); } catch (Exception e) { Log.e(TAG_ACTIVITY, "Error closing audioConfig", e); } }
                if (pullAudioInputStream != null) { try { pullAudioInputStream.close(); } catch (Exception e) { Log.e(TAG_ACTIVITY, "Error closing pullAudioInputStream", e); } }
                // pullStreamCallback.close() is called by PullAudioInputStream.close()

                // Delete temporary WAV file if created
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

                    // UI state update (enable nav buttons, show mic or playback)
                    // is based on whether audio file is still valid.
                    boolean hasValidRecordingAfterAzure = (this.currentRecordingFilePath != null &&
                            new File(this.currentRecordingFilePath).exists() &&
                            new File(this.currentRecordingFilePath).length() > 0);

                    if (finalErrorForUi != null) {
                        showError(finalErrorForUi);
                        Log.e(TAG_ACTIVITY, "Azure UI Error: " + finalErrorForUi + (finalAzureDetailedErrorLog.isEmpty() ? "" : " | Azure detailed error: " + finalAzureDetailedErrorLog));
                        restoreOriginalScriptText(); // Show plain text on error
                        deleteAssociatedFeedbackFile(); // Clear any associated feedback file on Azure error

                        if (hasValidRecordingAfterAzure) {
                            prepareMediaPlayerForPlayback(); // Allows playback of original audio
                        } else {
                            updatePlaybackUIState(false, false); // No valid audio
                        }
                    } else if (finalPronunciationResult != null) {
                        // Successfully got pronunciation assessment
                        Log.i(TAG_ACTIVITY, "Pronunciation analysis complete! Overall score: " + String.format(Locale.US, "%.0f", finalPronunciationResult.getPronunciationScore()) + "%");
                        highlightDifferencesAzure(this.lastCleanScriptDisplayed, finalPronunciationResult, finalRecognizedText); // This saves feedback
                        if (!hasValidRecordingAfterAzure) {
                            Log.w(TAG_ACTIVITY, "Azure success, but recording file is now invalid/missing. Path: " + this.currentRecordingFilePath);
                            updatePlaybackUIState(false, false); // Update UI, no audio to play
                        } else {
                            prepareMediaPlayerForPlayback(); // Prepare for playback, UI will show playback controls
                        }
                    } else if (finalRecognizedText != null && !finalRecognizedText.isEmpty()) {
                        // Recognized speech but no detailed pronunciation (e.g., fallback)
                        showToast("Speech recognized (no detailed pronunciation assessment).");
                        highlightDifferencesSimple(this.lastCleanScriptDisplayed, finalRecognizedText); // This saves feedback
                        if (!hasValidRecordingAfterAzure) {
                            updatePlaybackUIState(false, false);
                        } else {
                            prepareMediaPlayerForPlayback();
                        }
                    } else { // No error, but no pronunciation result and no recognized text (e.g., NoMatch but not caught as error)
                        showError("Could not recognize speech or an unknown error occurred during processing.");
                        restoreOriginalScriptText();
                        deleteAssociatedFeedbackFile(); // Clear feedback
                        if (hasValidRecordingAfterAzure) {
                            prepareMediaPlayerForPlayback();
                        } else {
                            updatePlaybackUIState(false, false);
                        }
                    }
                    // Ensure general UI (nav buttons etc.) is re-enabled correctly after Azure processing
                    if (!isCurrentlyRecording) { // Should always be false here
                        setNavigationButtonsEnabled(lastCanGoPreviousState, lastCanGoNextState);
                        if (imgClose != null) { imgClose.setEnabled(true); imgClose.setAlpha(1.0f); }
                        if (imgHome != null) { imgHome.setEnabled(true); imgHome.setAlpha(1.0f); }
                        // updatePlaybackUIState called within branches above handles mic/playback controls
                    }
                    Log.d(TAG_ACTIVITY, "Azure Finally UI: UI update complete.");
                });
            }
        }).exceptionally(ex -> { // Critical error in the async task itself
            Log.e(TAG_ACTIVITY, "Azure Exceptionally: Critical error in background task ", ex);
            runOnUiThread(() -> {
                Log.d(TAG_ACTIVITY, "Azure Exceptionally UI: Hiding ProgressBar due to critical error.");
                if (pgbAzureProcessing != null) {
                    pgbAzureProcessing.setVisibility(View.GONE);
                }
                // Re-enable UI elements generally
                setNavigationButtonsEnabled(lastCanGoPreviousState, lastCanGoNextState);
                if (imgClose != null) { imgClose.setEnabled(true); imgClose.setAlpha(1.0f); }
                if (imgHome != null) { imgHome.setEnabled(true); imgHome.setAlpha(1.0f); }

                showError("Critical error in Azure background task: " + ex.getMessage());
                restoreOriginalScriptText();
                deleteAssociatedFeedbackFile(); // Clear feedback due to critical failure

                // Check if original audio file is still playable
                boolean hasValidRecording = (this.currentRecordingFilePath != null &&
                        new File(this.currentRecordingFilePath).exists() &&
                        new File(this.currentRecordingFilePath).length() > 0);
                if (hasValidRecording) {
                    prepareMediaPlayerForPlayback();
                } else {
                    updatePlaybackUIState(false, false);
                }
            });
            return null;
        });
    }


    // Returns Pair<String, Integer> where String is output path, Integer is sample rate
    private Pair<String, Integer> transcodeToWavAndGetSampleRate(String inputPath) {
        final int DEFAULT_INPUT_RATE = 8000; // for AMR_NB
        final int TARGET_SAMPLE_RATE_AZURE = 16000; // Ideal for Azure

        if (inputPath == null) {
            Log.e(TAG_ACTIVITY, "transcodeToWav: Input file path is null.");
            return new Pair<>(null, DEFAULT_INPUT_RATE);
        }
        File inputFile = new File(inputPath);
        if (!inputFile.exists() || inputFile.length() == 0) {
            Log.e(TAG_ACTIVITY, "transcodeToWav: Input file does not exist or is empty: " + inputPath);
            return new Pair<>(null, DEFAULT_INPUT_RATE);
        }

        File outputFile = new File(getCacheDir(), "temp_audio_" + System.currentTimeMillis() + ".wav");
        Log.i(TAG_ACTIVITY, "transcodeToWav: Starting transcoding " + inputPath + " to " + outputFile.getAbsolutePath());

        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        FileOutputStream fos = null;
        RandomAccessFile randomAccessFile = null;

        final int TIMEOUT_US = 10000;
        int actualOutputSampleRate = DEFAULT_INPUT_RATE; // Will be updated by decoder if format changes
        final int TARGET_CHANNELS_AZURE = 1; // Mono
        final int TARGET_BIT_DEPTH_AZURE = 16; // 16-bit PCM

        try {
            fos = new FileOutputStream(outputFile);
            // Write placeholder for WAV header, will be overwritten later
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
                        int originalRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        Log.d(TAG_ACTIVITY, "transcodeToWav: Original Sample Rate: " + originalRate);
                        actualOutputSampleRate = originalRate; // Assume this initially if not changed by decoder
                    }
                    if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        Log.d(TAG_ACTIVITY, "transcodeToWav: Original Channel Count: " + inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                    }
                    break;
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                Log.e(TAG_ACTIVITY, "transcodeToWav: No audio track found in " + inputPath);
                if (fos != null) try { fos.close(); } catch (IOException e) {/*ignore*/}
                if (outputFile.exists()) outputFile.delete();
                return new Pair<>(null, actualOutputSampleRate); // Return original rate or default
            }
            extractor.selectTrack(audioTrackIndex);

            String inputMime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (inputMime == null) {
                Log.e(TAG_ACTIVITY, "transcodeToWav: Could not get MIME type from inputFormat.");
                if (fos != null) try { fos.close(); } catch (IOException e) {/*ignore*/}
                if (outputFile.exists()) outputFile.delete();
                return new Pair<>(null, actualOutputSampleRate);
            }
            decoder = MediaCodec.createDecoderByType(inputMime);

            // Attempt to configure decoder to output at TARGET_SAMPLE_RATE_AZURE if possible
            // This is often not supported directly by decoders for compressed formats.
            // Resampling might need a separate step if decoder outputs original sample rate.
            // For now, we let the decoder output its natural PCM format.
            MediaFormat decoderOutputFormat = new MediaFormat(); // inputFormat; // Start with input
            // It's generally better to let decoder output its natural PCM format and then resample if needed.
            // Forcing output sample rate on decoder.configure is often not supported for all codecs/formats.
            // decoderOutputFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, TARGET_SAMPLE_RATE_AZURE);
            // decoderOutputFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, TARGET_CHANNELS_AZURE);
            // We will get the actual output sample rate from MediaCodec.INFO_OUTPUT_FORMAT_CHANGED

            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            ByteBuffer[] inputBuffers = decoder.getInputBuffers(); // Deprecated in API 21
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers(); // Deprecated in API 21
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean inputEos = false;
            boolean outputEos = false;
            long totalPcmBytesWritten = 0;

            // actualOutputSampleRate will be updated from INFO_OUTPUT_FORMAT_CHANGED
            // If not updated, it remains the input sample rate (or default if input had none)

            while (!outputEos) {
                Thread.yield(); // Give other threads a chance
                if (!inputEos) {
                    int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inputBufIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufIndex); // Use API-level specific method if needed
                        if(inputBuffer == null) continue; // Should not happen with valid index
                        inputBuffer.clear();
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) { // End of stream
                            Log.d(TAG_ACTIVITY, "transcodeToWav: End of input data from extractor (Input EOS).");
                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEos = true;
                        } else {
                            decoder.queueInputBuffer(inputBufIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance(); // Move to next sample
                        }
                    }
                }

                int outputBufIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                if (outputBufIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufIndex); // Use API-level specific method if needed
                    if(outputBuffer == null) { // Should not happen
                        decoder.releaseOutputBuffer(outputBufIndex, false);
                        continue;
                    }

                    if (bufferInfo.size > 0) { // Valid data in buffer
                        byte[] pcmChunk = new byte[bufferInfo.size];
                        outputBuffer.get(pcmChunk); // Copy data from buffer
                        outputBuffer.clear(); // Clear buffer
                        fos.write(pcmChunk); // Write to file
                        totalPcmBytesWritten += pcmChunk.length;
                    }
                    decoder.releaseOutputBuffer(outputBufIndex, false); // Release buffer back to codec

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG_ACTIVITY, "transcodeToWav: Received Output EOS flag from decoder.");
                        outputEos = true; // End of output stream
                    }
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG_ACTIVITY, "transcodeToWav: Decoder output buffers changed.");
                    // outputBuffers = decoder.getOutputBuffers(); // Deprecated, but if supporting older APIs
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    actualOutputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int actualOutputChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    Log.d(TAG_ACTIVITY, "transcodeToWav: Decoder output format changed to: " + newFormat +
                            " (Actual SampleRate: " + actualOutputSampleRate + ", Actual Channels: " + actualOutputChannels + ")");
                    if (actualOutputSampleRate != TARGET_SAMPLE_RATE_AZURE || actualOutputChannels != TARGET_CHANNELS_AZURE) {
                        Log.w(TAG_ACTIVITY, "transcodeToWav: WARNING: Decoder output PCM format is " + actualOutputSampleRate + "Hz/" + actualOutputChannels + "ch. " +
                                "Ideal for Azure Pronunciation Assessment is " + TARGET_SAMPLE_RATE_AZURE + "Hz/" + TARGET_CHANNELS_AZURE + "ch. " +
                                "Further resampling might be needed if this rate is not optimal for Azure, though Azure SDK might handle some resampling for PCM.");
                        // For now, we use whatever the decoder gives. If it's not 16kHz, Azure quality might suffer.
                    }
                } // No else needed for INFO_TRY_AGAIN_LATER, loop will continue
            }

            fos.flush();
            fos.close();
            fos = null; // Mark as closed

            // Write the final WAV header with correct sizes
            randomAccessFile = new RandomAccessFile(outputFile, "rw");
            writeWavHeader(randomAccessFile, totalPcmBytesWritten,
                    actualOutputSampleRate, TARGET_CHANNELS_AZURE, TARGET_BIT_DEPTH_AZURE); // Use actualOutputSampleRate
            randomAccessFile.close();
            randomAccessFile = null; // Mark as closed

            Log.i(TAG_ACTIVITY, "transcodeToWav: Transcoding successful: " + outputFile.getAbsolutePath() +
                    ", PCM Size: " + totalPcmBytesWritten + " bytes, Output Sample Rate for WAV: " + actualOutputSampleRate);
            return new Pair<>(outputFile.getAbsolutePath(), actualOutputSampleRate);

        } catch (Exception e) { // Catch all exceptions during transcoding
            Log.e(TAG_ACTIVITY, "transcodeToWav: Error during transcoding ", e);
            if (outputFile.exists()) { // Clean up partially created file
                if (outputFile.delete()) {
                    Log.d(TAG_ACTIVITY, "transcodeToWav: Deleted incomplete output file.");
                }
            }
            // Return null path, and the sample rate determined before failure (or default)
            return new Pair<>(null, actualOutputSampleRate);
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
        long totalDataLen = pcmDataSize + 36; // 36 bytes for header fields after "RIFF" and size
        long byteRate = (long)sampleRate * numChannels * bitsPerSample / 8;
        int blockAlign = numChannels * bitsPerSample / 8; // Bytes per sample (all channels)

        raf.seek(0); // Go to the beginning of the file

        // RIFF chunk descriptor
        raf.writeBytes("RIFF"); // ChunkID
        raf.write(longToByteArray(totalDataLen, 4, ByteOrder.LITTLE_ENDIAN)); // ChunkSize (Total file size - 8 bytes)
        raf.writeBytes("WAVE"); // Format

        // fmt sub-chunk
        raf.writeBytes("fmt "); // Subchunk1ID
        raf.write(longToByteArray(16, 4, ByteOrder.LITTLE_ENDIAN)); // Subchunk1Size (16 for PCM)
        raf.write(longToByteArray(1, 2, ByteOrder.LITTLE_ENDIAN));  // AudioFormat (1 for PCM)
        raf.write(longToByteArray(numChannels, 2, ByteOrder.LITTLE_ENDIAN)); // NumChannels
        raf.write(longToByteArray(sampleRate, 4, ByteOrder.LITTLE_ENDIAN));  // SampleRate
        raf.write(longToByteArray(byteRate, 4, ByteOrder.LITTLE_ENDIAN));    // ByteRate
        raf.write(longToByteArray(blockAlign, 2, ByteOrder.LITTLE_ENDIAN)); // BlockAlign
        raf.write(longToByteArray(bitsPerSample, 2, ByteOrder.LITTLE_ENDIAN)); // BitsPerSample

        // data sub-chunk
        raf.writeBytes("data"); // Subchunk2ID
        raf.write(longToByteArray(pcmDataSize, 4, ByteOrder.LITTLE_ENDIAN)); // Subchunk2Size (Size of pcm data)

        Log.d(TAG_ACTIVITY, "writeWavHeader: WAV header written. pcmDataSize=" + pcmDataSize +
                ", totalDataLen=" + totalDataLen + ", sampleRate=" + sampleRate +
                ", numChannels=" + numChannels + ", bitsPerSample=" + bitsPerSample);
    }

    private byte[] longToByteArray(long value, int numBytes, ByteOrder byteOrder) {
        ByteBuffer buffer = ByteBuffer.allocate(numBytes);
        buffer.order(byteOrder);
        if (numBytes == 2) {
            buffer.putShort((short) value);
        } else if (numBytes == 4) {
            buffer.putInt((int) value);
        } else if (numBytes == 8 && Long.BYTES >= 8) { // Ensure long can be represented
            buffer.putLong(value);
        } else {
            // Fallback or error for unsupported numBytes, though for WAV header, 2 and 4 are most common.
            Log.e(TAG_ACTIVITY, "longToByteArray: Unsupported numBytes for long conversion: " + numBytes + " for value " + value);
            // Defaulting to int/short for safety if numBytes is wrong but value fits
            if (numBytes == 4) buffer.putInt((int)value);
            else if (numBytes == 2) buffer.putShort((short)value);
            // else buffer will be zero-filled for other numBytes, which is likely an error.
        }
        return buffer.array();
    }


    private void saveFeedbackData(List<Integer> redWordIndices) {
        String feedbackFilePath = generatePersistentFeedbackFilePath();
        if (feedbackFilePath == null) {
            Log.e(TAG_ACTIVITY, "Could not generate feedback file path. Feedback not saved.");
            return;
        }

        this.currentRedWordIndices = redWordIndices != null ? new ArrayList<>(redWordIndices) : null;

        if (redWordIndices == null || redWordIndices.isEmpty()) {
            File oldFeedbackFile = new File(feedbackFilePath);
            if (oldFeedbackFile.exists()) {
                if (oldFeedbackFile.delete()) {
                    Log.i(TAG_ACTIVITY, "Deleted old feedback file as new feedback has no errors: " + feedbackFilePath);
                } else {
                    Log.w(TAG_ACTIVITY, "Could not delete old feedback file: " + feedbackFilePath);
                }
            }
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(feedbackFilePath);
             OutputStreamWriter osw = new OutputStreamWriter(fos);
             BufferedWriter writer = new BufferedWriter(osw)) {
            for (Integer index : redWordIndices) {
                writer.write(index.toString());
                writer.newLine();
            }
            Log.i(TAG_ACTIVITY, "Feedback data saved to: " + feedbackFilePath + " with indices: " + redWordIndices.toString());
        } catch (IOException e) {
            Log.e(TAG_ACTIVITY, "Error saving feedback data to " + feedbackFilePath, e);
            showError("Could not save pronunciation feedback details.");
        }
    }

    private List<Integer> loadFeedbackData() {
        String feedbackFilePath = generatePersistentFeedbackFilePath();
        if (feedbackFilePath == null || !new File(feedbackFilePath).exists()) {
            Log.d(TAG_ACTIVITY, "No feedback file found or path is null for script index " + currentScriptModelIndex + ": " + feedbackFilePath);
            return null;
        }

        List<Integer> redIndices = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(feedbackFilePath);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (!line.trim().isEmpty()) {
                        redIndices.add(Integer.parseInt(line.trim()));
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG_ACTIVITY, "Invalid number format in feedback file: " + line, e);
                }
            }
            Log.i(TAG_ACTIVITY, "Feedback data loaded from: " + feedbackFilePath + " Indices: " + redIndices.toString());
            return redIndices.isEmpty() ? null : redIndices;
        } catch (IOException e) {
            Log.e(TAG_ACTIVITY, "Error loading feedback data from " + feedbackFilePath, e);
            return null;
        }
    }

    private void applyFeedbackToScript(String scriptText, List<Integer> redWordIndices) {
        if (txtScriptToRepeat == null || scriptText == null) {
            Log.w(TAG_ACTIVITY, "applyFeedbackToScript: TextView or scriptText is null.");
            if (txtScriptToRepeat != null && scriptText == null) txtScriptToRepeat.setText("");
            return;
        }
        if (scriptText.isEmpty()){
            txtScriptToRepeat.setText("");
            return;
        }

        if (redWordIndices == null || redWordIndices.isEmpty()) {
            txtScriptToRepeat.setText(scriptText);
            txtScriptToRepeat.setTextColor(ContextCompat.getColor(this, android.R.color.black));
            return;
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder(scriptText);
        Pattern originalWordPattern = Pattern.compile("\\b[a-zA-Z0-9]+(?:['‘’][a-zA-Z0-9]+)*\\b");
        Matcher matcher = originalWordPattern.matcher(scriptText);
        int wordIndexInScript = 0;

        while (matcher.find()) {
            if (redWordIndices.contains(wordIndexInScript)) {
                spannable.setSpan(new ForegroundColorSpan(Color.RED), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            wordIndexInScript++;
        }
        txtScriptToRepeat.setText(spannable);
    }

    private void highlightDifferencesSimple(String originalText, String recognizedText) {
        if (txtScriptToRepeat == null || originalText == null) {
            Log.w(TAG_ACTIVITY, "highlightDifferencesSimple: Invalid input (TextView or originalText is null).");
            restoreOriginalScriptText(); // Shows plain text
            saveFeedbackData(null);      // Clears any saved feedback for this
            return;
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder(originalText);
        List<Integer> redIndices = new ArrayList<>();

        if (originalText.trim().isEmpty()) {
            txtScriptToRepeat.setText(spannable);
            saveFeedbackData(null);
            return;
        }

        Pattern displayWordPattern = Pattern.compile("\\b[a-zA-Z0-9]+(?:['‘’][a-zA-Z0-9]+)*\\b");
        Matcher displayMatcher = displayWordPattern.matcher(originalText);

        String[] recognizedWordsCleaned = (recognizedText == null || recognizedText.trim().isEmpty()) ? new String[0] :
                recognizedText.trim().toLowerCase(Locale.ROOT)
                        .replaceAll("[\\p{Punct}&&[^'-]]+", "")
                        .split("\\s+");

        int displayWordIndex = 0;
        int recognizedWordPointer = 0;

        while (displayMatcher.find()) {
            String currentDisplayWordOriginalCase = displayMatcher.group(0);
            String currentDisplayWordCleanedLower = currentDisplayWordOriginalCase.replaceAll("[^a-zA-Z0-9']", "").toLowerCase(Locale.ROOT);

            boolean matchFound = false;
            if (recognizedWordPointer < recognizedWordsCleaned.length) {
                if (currentDisplayWordCleanedLower.equals(recognizedWordsCleaned[recognizedWordPointer]) && !currentDisplayWordCleanedLower.isEmpty()) {
                    matchFound = true;
                }
            }

            if (!matchFound) {
                spannable.setSpan(new ForegroundColorSpan(Color.RED), displayMatcher.start(), displayMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                redIndices.add(displayWordIndex);
            }

            if (matchFound) {
                recognizedWordPointer++;
            }
            displayWordIndex++;
        }

        txtScriptToRepeat.setText(spannable);
        saveFeedbackData(redIndices.isEmpty() ? null : redIndices);
    }


    private void highlightDifferencesAzure(String originalText, PronunciationAssessmentResult assessmentResult, String recognizedTranscriptOverall) {
        if (txtScriptToRepeat == null || originalText == null || assessmentResult == null) {
            Log.e(TAG_ACTIVITY, "highlightDifferencesAzure: Invalid input (TextView, originalText, or assessmentResult is null).");
            restoreOriginalScriptText();
            saveFeedbackData(null);
            return;
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder(originalText);
        List<WordLevelTimingResult> assessedWordsFromAzure = assessmentResult.getWords();
        List<Integer> redIndices = new ArrayList<>();

        if (originalText.trim().isEmpty()) {
            txtScriptToRepeat.setText(spannable);
            saveFeedbackData(null);
            return;
        }

        if (assessedWordsFromAzure == null || assessedWordsFromAzure.isEmpty()) {
            Log.w(TAG_ACTIVITY, "No word-level data from PronunciationAssessmentResult for Azure highlighting.");
            if (recognizedTranscriptOverall != null && !recognizedTranscriptOverall.isEmpty()) {
                highlightDifferencesSimple(originalText, recognizedTranscriptOverall);
            } else {
                restoreOriginalScriptText();
                saveFeedbackData(null);
            }
            return;
        }

        Log.i(TAG_ACTIVITY, "HighlightAzure: Starting. Azure words: " + assessedWordsFromAzure.size() + ". Original: \"" + originalText + "\"");

        List<Pair<String, Pair<Integer, Integer>>> originalDisplayWordSegments = new ArrayList<>();
        Pattern displayWordPattern = Pattern.compile("\\b[a-zA-Z0-9]+(?:['‘’][a-zA-Z0-9]+)*\\b");
        Matcher displayMatcher = displayWordPattern.matcher(originalText);
        while (displayMatcher.find()) {
            originalDisplayWordSegments.add(
                    new Pair<>(displayMatcher.group(0), new Pair<>(displayMatcher.start(), displayMatcher.end()))
            );
        }

        int azureWordIdx = 0;
        for (int i = 0; i < originalDisplayWordSegments.size(); i++) {
            Pair<String, Pair<Integer, Integer>> currentDisplaySegment = originalDisplayWordSegments.get(i);
            String displayWordText = currentDisplaySegment.first;
            int displayWordStart = currentDisplaySegment.second.first;
            int displayWordEnd = currentDisplaySegment.second.second;

            String cleanedDisplayWordLower = displayWordText.replaceAll("[^a-zA-Z0-9']", "").toLowerCase(Locale.ROOT);
            boolean errorForThisDisplayWord = false;

            if (cleanedDisplayWordLower.isEmpty()) { // Skip empty segments from regex if any
                continue;
            }

            if (azureWordIdx < assessedWordsFromAzure.size()) {
                WordLevelTimingResult currentAzureWordData = assessedWordsFromAzure.get(azureWordIdx);

                // Skip Azure "Insertion" words as they don't align with an original word
                while ("Insertion".equalsIgnoreCase(currentAzureWordData.getErrorType()) && azureWordIdx < assessedWordsFromAzure.size() - 1) {
                    azureWordIdx++;
                    currentAzureWordData = assessedWordsFromAzure.get(azureWordIdx);
                }

                // If after skipping insertions, the current Azure word is still an insertion,
                // it means the original display word has no corresponding non-insertion Azure word.
                if ("Insertion".equalsIgnoreCase(currentAzureWordData.getErrorType())) {
                    errorForThisDisplayWord = true; // Original word likely omitted
                    Log.d(TAG_ACTIVITY, "HighlightAzure: Display word '" + displayWordText + "' (idx " + i + ") marked RED. Azure counterpart is Insertion or list ends with Insertion.");
                } else {
                    String azureWordText = currentAzureWordData.getWord();
                    String cleanedAzureWordLower = azureWordText.replaceAll("[^a-zA-Z0-9']", "").toLowerCase(Locale.ROOT);

                    if (cleanedDisplayWordLower.equals(cleanedAzureWordLower)) {
                        String errorType = currentAzureWordData.getErrorType();
                        double accuracyScore = currentAzureWordData.getAccuracyScore();
                        Log.d(TAG_ACTIVITY, "HighlightAzure: Matched Display:'" + displayWordText + "' (idx " + i + ") with Azure:'" + azureWordText + "'. Error: " + errorType + ", Score: " + accuracyScore);

                        if ("Mispronunciation".equalsIgnoreCase(errorType) || "Omission".equalsIgnoreCase(errorType) ||
                                (accuracyScore < 60.0 && !"None".equalsIgnoreCase(errorType))) {
                            errorForThisDisplayWord = true;
                        }
                        azureWordIdx++; // Matched, move to next Azure word
                    } else {
                        // Words do not match. Original display word is likely an Omission or severe Mispronunciation.
                        errorForThisDisplayWord = true;
                        Log.d(TAG_ACTIVITY, "HighlightAzure: Display word '" + displayWordText + "' (idx " + i + ") marked RED. Mismatch with Azure word '" + azureWordText + "'. Assumed omission/mispronunciation of display word.");
                        // We don't advance azureWordIdx here because currentAzureWordData might match the *next* display word.
                        // This is a greedy approach for display words. If Azure has an extra word, it will be skipped later.
                        // If Azure is missing a word, this display word is marked.
                    }
                }
            } else {
                // Azure word list is exhausted, but original display words remain. These are omissions.
                errorForThisDisplayWord = true;
                Log.d(TAG_ACTIVITY, "HighlightAzure: Display word '" + displayWordText + "' (idx " + i + ") marked RED (Omission - Azure list exhausted).");
            }

            if (errorForThisDisplayWord) {
                spannable.setSpan(new ForegroundColorSpan(Color.RED), displayWordStart, displayWordEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                redIndices.add(i);
            }
        }

        txtScriptToRepeat.setText(spannable);
        Log.i(TAG_ACTIVITY, "HighlightAzure: Highlighting complete. Red indices: " + redIndices.toString());
        saveFeedbackData(redIndices.isEmpty() ? null : redIndices);
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
                // FileInputStream will still be created, but read will return 0 or -1 immediately.
            }
            this.fileStream = new FileInputStream(audioFile);
            Log.d(TAG_CALLBACK, "FileInputStream opened for: " + filePath + ", size: " + audioFile.length());
        }

        @Override
        public int read(byte[] dataBuffer) {
            try {
                if (this.fileStream == null) { // Should not happen if constructor succeeded
                    Log.e(TAG_CALLBACK, "read() called but fileStream is null.");
                    return 0; // Indicates end of stream to Azure SDK
                }
                int bytesRead = this.fileStream.read(dataBuffer, 0, dataBuffer.length);
                if (bytesRead == -1) { // End of file reached
                    return 0; // SDK expects 0 to indicate end of stream
                }
                return bytesRead;
            } catch (IOException e) {
                Log.e(TAG_CALLBACK, "Error reading from file stream: " + e.getMessage(), e);
                return 0; // Indicate error/end of stream
            }
        }

        @Override
        public void close() {
            try {
                if (this.fileStream != null) {
                    this.fileStream.close();
                    Log.d(TAG_CALLBACK, "FileInputStream closed.");
                    this.fileStream = null; // Nullify to prevent reuse
                }
            } catch (IOException e) {
                Log.e(TAG_CALLBACK, "Error closing file stream: " + e.getMessage(), e);
            }
        }
    }
}
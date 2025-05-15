package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import com.example.langhexx.Controller.SpeakingVoiceController;
import com.example.langhexx.Model.SpeakingContract;
import com.example.langhexx.R;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class InternalSpeakingVoiceTopic extends AppCompatActivity implements SpeakingContract.PronunciationView {

    private static final String TAG_ACTIVITY = "InternalSpeakingVoiceTopic";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 201;

    Button btnBackward, btnForward;
    ImageButton imgPlayAudio, imgDelete, imgRecord;
    ImageView imgClose, imgHome;
    TextView txtScreenTitle, txtScriptToRepeat, txtScriptCounter, txtTime;
    SeekBar sbrAudio;
    LinearLayout layoutPlaybackControls;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_learning);


        Intent intent = getIntent();
        levelName = intent.getStringExtra("levelName");
        topicId = intent.getStringExtra("topicId");
        topicDisplayTitleStr = intent.getStringExtra("topicTitle");
        if (levelName == null || levelName.isEmpty() || topicId == null || topicId.isEmpty()) {
            Toast.makeText(this, "Error: Missing Level or Topic ID.", Toast.LENGTH_LONG).show();
            Log.e(TAG_ACTIVITY, "LevelName or TopicId is null or empty. Closing Activity.");
            finish();
            return;
        }
        addControls();
        controller = new SpeakingVoiceController(this, getApplicationContext(), levelName, topicId, topicDisplayTitleStr);
        addEvents();
        imgRecord.setVisibility(View.VISIBLE);
        layoutPlaybackControls.setVisibility(View.GONE);
        updatePlaybackUIState(false, false);

        if (controller != null) {
            controller.viewDidLoad();
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

        txtScreenTitle = findViewById(R.id.txtTitle);
        txtScriptToRepeat = findViewById(R.id.txtScript);
        txtScriptCounter = findViewById(R.id.txtCounter);
        txtTime = findViewById(R.id.txtTime);
        sbrAudio = findViewById(R.id.sbrAudio);
        layoutPlaybackControls = findViewById(R.id.layout_playback_controls);

        pulsatingAnimation = AnimationUtils.loadAnimation(this, R.anim.pulsating_effect);

        imgPlayAudio.setEnabled(false);
        imgDelete.setEnabled(false);
        sbrAudio.setEnabled(false);
        txtTime.setText("00:00 / 00:00");
    }

    private void showExitConfirmationDialog(Runnable onConfirmAction) {
        new AlertDialog.Builder(this)
                .setTitle("Exit Confirmation")
                .setMessage("You have a recording in progress. Are you sure you want to exit ?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    if (onConfirmAction != null) {
                        onConfirmAction.run();
                    }
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void addEvents() {
        imgClose.setOnClickListener(v -> {
            boolean hasRecordingFile = currentRecordingFilePath != null && new File(currentRecordingFilePath).exists();
            if (hasRecordingFile) {
                showExitConfirmationDialog(() -> {
                    if (controller != null) controller.onCloseButtonClicked();
                    else finishActivity();
                });
            } else {
                if (controller != null) controller.onCloseButtonClicked();
                else finishActivity();
            }
        });

        imgHome.setOnClickListener(view -> {
            boolean hasRecordingFile = currentRecordingFilePath != null && new File(currentRecordingFilePath).exists();
            if (hasRecordingFile) {
                showExitConfirmationDialog(() -> {
                    if (controller != null) controller.onHomeButtonClicked();
                });
            } else {
                if (controller != null) controller.onHomeButtonClicked();
            }
        });

        btnBackward.setOnClickListener(v -> {
            if (controller != null && btnBackward.isEnabled()) controller.onPreviousScriptClicked();
        });

        btnForward.setOnClickListener(v -> {
            if (controller != null && btnForward.isEnabled()) controller.onNextScriptClicked();
        });

        imgRecord.setOnClickListener(v -> {
            if (controller != null) controller.onMicButtonClicked();
        });

        imgPlayAudio.setOnClickListener(v -> {
            if (currentRecordingFilePath == null) {
                showToast("No recording to play.");
                return;
            }
            if (isCurrentlyPlaying) {
                pausePlayback();
            } else {
                startPlayback();
            }
        });

        imgDelete.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(InternalSpeakingVoiceTopic.this) // Ensure AlertDialog is from androidx.appcompat.app
                    .setTitle("Confirm")
                    .setMessage("Are you sure want to delete this record !")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        deleteCurrentRecording();
                    })
                    .setNegativeButton("No", (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .show();
        });


        sbrAudio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mediaPlayer != null && fromUser) {
                    mediaPlayer.seekTo(progress);
                    updatePlayTimeWithMillis(progress, mediaPlayer.getDuration());
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
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    seekBarHandler.post(updateSeekBarRunnable);
                } else if (mediaPlayer != null) {
                    updatePlayTimeWithMillis(seekBar.getProgress(), mediaPlayer.getDuration());
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        boolean hasRecordingFile = currentRecordingFilePath != null && new File(currentRecordingFilePath).exists();
        if (hasRecordingFile) {
            showExitConfirmationDialog(() -> {
                if (controller != null) {
                    controller.onCloseButtonClicked();
                } else {
                    finishActivity();
                }
            });
        } else {
            if (controller != null) {
                controller.onCloseButtonClicked();
            } else {
                finishActivity();
            }
        }
    }

    @Override
    public void displayScreenTitle(String title) {
        if (txtScreenTitle != null) txtScreenTitle.setText(title);
    }

    @Override
    public void displayScriptToRepeat(String scriptText) {
        if (txtScriptToRepeat != null) txtScriptToRepeat.setText(scriptText);
    }

    @Override
    public void updateScriptCounter(int current, int total) {
        if (txtScriptCounter != null) txtScriptCounter.setText(total == 0 ? "0/0" : current + "/" + total);
    }

    @Override
    public void setNavigationButtonsEnabled(boolean isPreviousEnabled, boolean isNextEnabled) {
        if (btnBackward != null) {
            btnBackward.setEnabled(isPreviousEnabled);
            btnBackward.setBackground(ContextCompat.getDrawable(this, isPreviousEnabled ? R.drawable.bg_button_next_rounded : R.drawable.bg_button_previous_rounded));
        }
        if (btnForward != null) {
            btnForward.setEnabled(isNextEnabled);
            btnForward.setBackground(ContextCompat.getDrawable(this, isNextEnabled ? R.drawable.bg_button_next_rounded : R.drawable.bg_button_previous_rounded));
        }
    }

    @Override
    public void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG_ACTIVITY, "Error: " + message);
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showCustomToast(boolean success, String message) {
        showToast((success ? "Success: " : "Failed: ") + message);
    }

    @Override
    public void setMicButtonEnabled(boolean enabled) {
        if (imgRecord != null) {
            imgRecord.setEnabled(enabled);
            imgRecord.setAlpha(enabled ? 1.0f : 0.5f);
        }
    }

    @Override
    public void playSound(boolean isCorrect) { /* Not used here */ }

    @Override
    public void speakText(String text, String utteranceId) {
        Log.d(TAG_ACTIVITY, "TTS Request: " + text);
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void finishActivity() {
        Log.d(TAG_ACTIVITY, "finishActivity called.");
        cleanUpAudioResources();
        finish();
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
                showToast("Recording permission denied. Feature unavailable.");
            }
        }
    }

    @Override
    public void startRecordingUI() {
        if (isCurrentlyRecording) {
            showToast("Already recording.");
            return;
        }

        File audioDir = new File(getCacheDir(), "audio_recordings");
        if (!audioDir.exists() && !audioDir.mkdirs()) {
            showError("Failed to create audio directory.");
            return;
        }
        if (currentRecordingFilePath != null) {
         //
        }
        currentRecordingFilePath = new File(audioDir, "user_recording_" + System.currentTimeMillis() + ".3gp").getAbsolutePath();

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(currentRecordingFilePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isCurrentlyRecording = true;
            updateRecordingUIState(true);
        } catch (IOException e) {
            Log.e(TAG_ACTIVITY, "MediaRecorder prepare() failed", e);
            showError("Recording failed to start.");
            currentRecordingFilePath = null;
            isCurrentlyRecording = false;
            updateRecordingUIState(false);
            releaseMediaRecorder();
        }
    }

    @Override
    public void stopRecordingUI() {
        if (!isCurrentlyRecording || mediaRecorder == null) {
            Log.d(TAG_ACTIVITY, "Stop recording called but not recording or mediaRecorder is null.");
            if (currentRecordingFilePath != null) {
                updatePlaybackUIState(false, true);
            } else {
                updatePlaybackUIState(false, false);
            }
            isCurrentlyRecording = false;
            updateRecordingUIState(false);
            return;
        }
        try {
            mediaRecorder.stop();
            Log.d(TAG_ACTIVITY, "MediaRecorder stopped successfully.");
        } catch (RuntimeException stopException) {
            Log.e(TAG_ACTIVITY, "MediaRecorder stop() failed: " + stopException.getMessage());
            if (currentRecordingFilePath != null) {
                File problematicFile = new File(currentRecordingFilePath);
                if (problematicFile.exists()) {
                    if(problematicFile.delete()){
                        Log.d(TAG_ACTIVITY, "Deleted problematic recording file: " + currentRecordingFilePath);
                    } else {
                        Log.e(TAG_ACTIVITY, "Failed to delete problematic recording file: " + currentRecordingFilePath);
                    }
                }
                currentRecordingFilePath = null;
            }
            showError("Failed to save recording properly.");
        } finally {
            releaseMediaRecorder();
            isCurrentlyRecording = false;
            updateRecordingUIState(false);
            if (currentRecordingFilePath != null) {
                prepareMediaPlayerForPlayback();
            } else {
                updatePlaybackUIState(false, false);
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
        if (currentRecordingFilePath == null) {
            Log.d(TAG_ACTIVITY, "prepareMediaPlayerForPlayback: No recording file path.");
            updatePlaybackUIState(false, false);
            return;
        }
        releaseMediaPlayer();

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(currentRecordingFilePath);
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG_ACTIVITY, "MediaPlayer prepared. Duration: " + mp.getDuration());
                if (sbrAudio != null) sbrAudio.setMax(mp.getDuration());
                updatePlayTimeWithMillis(0, mp.getDuration());
                if (imgPlayAudio != null) imgPlayAudio.setEnabled(true);
                if (sbrAudio != null) sbrAudio.setEnabled(true);
                if (imgDelete != null) imgDelete.setEnabled(true);
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG_ACTIVITY, "MediaPlayer playback completed.");
                isCurrentlyPlaying = false;
                if (mediaPlayer != null) {
                    updatePlayTimeWithMillis(mediaPlayer.getCurrentPosition(), mediaPlayer.getDuration());
                    if (sbrAudio != null) sbrAudio.setProgress(sbrAudio.getMax());
                } else {
                    updatePlayTimeWithMillis(0,0);
                    if (sbrAudio != null) sbrAudio.setProgress(0);
                }
                updatePlaybackUIState(false, true);
                seekBarHandler.removeCallbacks(updateSeekBarRunnable);
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG_ACTIVITY, "MediaPlayer error: what=" + what + ", extra=" + extra);
                showError("Error playing audio.");
                releaseMediaPlayer();
                updatePlaybackUIState(false, false);
                return true;
            });
            mediaPlayer.prepareAsync();
            Log.d(TAG_ACTIVITY, "MediaPlayer.prepareAsync() called.");
        } catch (IOException e) {
            Log.e(TAG_ACTIVITY, "MediaPlayer setDataSource failed", e);
            showError("Could not load audio for playback.");
            releaseMediaPlayer();
            updatePlaybackUIState(false, false);
        }
    }

    private void startPlayback() {
        if (mediaPlayer == null || currentRecordingFilePath == null) {
            if (currentRecordingFilePath != null && mediaPlayer == null) {
                Log.d(TAG_ACTIVITY, "startPlayback: MediaPlayer is null, preparing...");
                prepareMediaPlayerForPlayback();
                showToast("Preparing audio...");
                return;
            } else {
                showToast("No recording available to play.");
                return;
            }
        }

        boolean isPrepared = false;
        try {
            mediaPlayer.getDuration();
            isPrepared = true;
        } catch (IllegalStateException e) {
            Log.w(TAG_ACTIVITY, "MediaPlayer not in prepared state for getDuration. Will try to prepare/wait.");
            isPrepared = false;
        }


        if (isPrepared && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
                isCurrentlyPlaying = true;
                updatePlaybackUIState(true, true);
                seekBarHandler.post(updateSeekBarRunnable);
                Log.d(TAG_ACTIVITY, "Playback started.");
            } catch (IllegalStateException e) {
                Log.e(TAG_ACTIVITY, "MediaPlayer start failed (IllegalStateException): " + e.getMessage() + ". Player might not be prepared yet.");
                showError("Playback failed. Audio might still be preparing.");
                if (!mediaPlayer.isPlaying()) {
                    prepareMediaPlayerForPlayback();
                }
            }
        } else if (!isPrepared) {
            showToast("Audio is preparing, please wait...");
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                prepareMediaPlayerForPlayback();
            }
        } else if (mediaPlayer.isPlaying()) {
            Log.d(TAG_ACTIVITY, "Playback already playing.");
        }
    }

    private void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
                isCurrentlyPlaying = false;
                updatePlaybackUIState(false, true);
                seekBarHandler.removeCallbacks(updateSeekBarRunnable);
                Log.d(TAG_ACTIVITY, "Playback paused.");
            } catch (IllegalStateException e) {
                Log.e(TAG_ACTIVITY, "MediaPlayer pause failed: " + e.getMessage());
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
        seekBarHandler.removeCallbacks(updateSeekBarRunnable);
    }

    private void deleteCurrentRecording() {
        pausePlayback();
        releaseMediaPlayer();

        if (currentRecordingFilePath != null) {
            File file = new File(currentRecordingFilePath);
            if (file.exists()) {
                if (file.delete()) {
                    Log.d(TAG_ACTIVITY, "Recording file deleted: " + currentRecordingFilePath);
                    showToast("Recording deleted.");
                } else {
                    showError("Failed to delete recording file.");
                    Log.e(TAG_ACTIVITY, "Failed to delete recording file: " + currentRecordingFilePath);
                }
            } else {
                Log.d(TAG_ACTIVITY, "Recording file not found for deletion: " + currentRecordingFilePath);
            }
            currentRecordingFilePath = null;
        }
        isCurrentlyPlaying = false;
        updatePlaybackUIState(false, false);
    }


    @Override
    public void updateRecordingUIState(boolean isRecordingActive) {
        this.isCurrentlyRecording = isRecordingActive;
        if (isRecordingActive) {
            imgRecord.setImageResource(R.drawable.voice_icon_pause_);
            if (pulsatingAnimation != null && (imgRecord.getAnimation() == null || !imgRecord.getAnimation().hasStarted() || imgRecord.getAnimation().hasEnded())) {
                imgRecord.startAnimation(pulsatingAnimation);
            }
            imgRecord.setVisibility(View.VISIBLE);
            layoutPlaybackControls.setVisibility(View.GONE);

            if(btnBackward != null) btnBackward.setEnabled(false);
            if(btnForward != null) btnForward.setEnabled(false);

        } else {
            imgRecord.setImageResource(R.drawable.micro);
            if (pulsatingAnimation != null) {
                imgRecord.clearAnimation();
            }
            updatePlaybackUIState(isCurrentlyPlaying, currentRecordingFilePath != null && new File(currentRecordingFilePath).exists());
            if (controller != null) {
              //
            } else {
              //
                setNavigationButtonsEnabled(model.getCurrentQuestionIndex() > 0, model.getCurrentQuestionIndex() < model.getQuestionCount() - 1);
            }
        }
    }

    @Override
    public void updatePlaybackUIState(boolean isPlaying, boolean hasRecording) {
        this.isCurrentlyPlaying = isPlaying;

        if (hasRecording && !isCurrentlyRecording) {
            if (pulsatingAnimation != null) {
                imgRecord.clearAnimation();
            }
            imgRecord.setVisibility(View.GONE);
            layoutPlaybackControls.setVisibility(View.VISIBLE);

            imgPlayAudio.setEnabled(true);
            imgDelete.setEnabled(true);
            sbrAudio.setEnabled(true);
            imgPlayAudio.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.icon_play_audio);
            imgPlayAudio.setAlpha(1.0f);
            imgDelete.setAlpha(1.0f);
        } else {
            imgRecord.setVisibility(View.VISIBLE);
            layoutPlaybackControls.setVisibility(View.GONE);

            if (isCurrentlyRecording) {
                imgRecord.setImageResource(R.drawable.voice_icon_pause_);
                if (pulsatingAnimation != null && (imgRecord.getAnimation() == null || !imgRecord.getAnimation().hasStarted() || imgRecord.getAnimation().hasEnded())) {
                    imgRecord.startAnimation(pulsatingAnimation);
                }
            } else {
                imgRecord.setImageResource(R.drawable.micro);
                if (pulsatingAnimation != null) {
                    imgRecord.clearAnimation();
                }
            }
            imgPlayAudio.setEnabled(false);
            imgDelete.setEnabled(false);
            sbrAudio.setEnabled(false);
            imgPlayAudio.setImageResource(R.drawable.icon_play_audio);
            imgPlayAudio.setAlpha(0.5f);
            imgDelete.setAlpha(0.5f);
            if (sbrAudio != null) sbrAudio.setProgress(0);
            if (txtTime != null) txtTime.setText("00:00 / 00:00");
        }
    }


    @Override
    public void updateSeekBarProgress(int progress, int max) {
        if (sbrAudio != null) {
            if (max >= 0) sbrAudio.setMax(max);
            if (progress >=0 && progress <= max) sbrAudio.setProgress(progress);
            else if (progress < 0) sbrAudio.setProgress(0);
            else sbrAudio.setProgress(max);

        }
    }

    @Override
    public void updatePlayTime(String currentTime, String totalTime) {
        if (txtTime != null) {
            txtTime.setText(String.format(Locale.getDefault(), "%s / %s", currentTime, totalTime));
        }
    }

    private void updatePlayTimeWithMillis(long currentMillis, long totalMillis) {
        if (totalMillis < 0) totalMillis = 0;
        if (currentMillis < 0) currentMillis = 0;
        if (currentMillis > totalMillis) currentMillis = totalMillis;

        String currentTimeStr = String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(currentMillis),
                TimeUnit.MILLISECONDS.toSeconds(currentMillis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(currentMillis))
        );
        String totalTimeStr = String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(totalMillis),
                TimeUnit.MILLISECONDS.toSeconds(totalMillis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(totalMillis))
        );
        updatePlayTime(currentTimeStr, totalTimeStr);
        if (sbrAudio != null && mediaPlayer != null) {
            try {
                if(!mediaPlayer.isPlaying()){
                    sbrAudio.setMax((int)totalMillis);
                    sbrAudio.setProgress((int)currentMillis);
                }
            } catch (IllegalStateException e) {
                Log.w(TAG_ACTIVITY, "updatePlayTimeWithMillis: MediaPlayer not prepared for duration/position access.");
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
                        if (sbrAudio != null) {
                            if (sbrAudio.getMax() != duration && duration > 0) {
                                sbrAudio.setMax(duration);
                            }
                            sbrAudio.setProgress(currentPosition);
                        }
                        updatePlayTimeWithMillis(currentPosition, duration);
                        seekBarHandler.postDelayed(this, 200);
                    } else if (isCurrentlyPlaying) {
                        seekBarHandler.postDelayed(this, 500);
                    } else {
                        seekBarHandler.removeCallbacks(this);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG_ACTIVITY, "MediaPlayer in illegal state during seekbar update: " + e.getMessage() + ". Might be preparing or released.");
                    if (mediaPlayer != null && isCurrentlyPlaying) {
                        seekBarHandler.postDelayed(this, 500);
                    } else {
                        seekBarHandler.removeCallbacks(this);
                    }
                } catch (Exception e) {
                    Log.e(TAG_ACTIVITY, "Exception in updateSeekBarRunnable: " + e.getMessage());
                    seekBarHandler.removeCallbacks(this);
                }
            } else {
                seekBarHandler.removeCallbacks(this);
            }
        }
    };

    private void cleanUpAudioResources() {
        Log.d(TAG_ACTIVITY, "Cleaning up audio resources.");
        seekBarHandler.removeCallbacksAndMessages(null);
        releaseMediaPlayer();
        releaseMediaRecorder();
        if (imgRecord != null && pulsatingAnimation != null && imgRecord.getAnimation() == pulsatingAnimation) {
            imgRecord.clearAnimation();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG_ACTIVITY, "onStop called.");
        if (isCurrentlyRecording) {
            Log.d(TAG_ACTIVITY, "Recording active onStop. Stopping recording.");
            stopRecordingUI(); // This will attempt to save the recording.
        }
        if (isCurrentlyPlaying && mediaPlayer != null) {
            Log.d(TAG_ACTIVITY, "Playback active onStop. Pausing playback.");
            pausePlayback();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG_ACTIVITY, "onDestroy Activity called.");
        cleanUpAudioResources();
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
        this.currentRecordingFilePath = path;
        if (path != null && new File(path).exists()) {
            prepareMediaPlayerForPlayback(); // Prepare for playback
            updatePlaybackUIState(false, true);
        } else {
            if (path == null) {
                deleteCurrentRecording();
            }
            updatePlaybackUIState(false, false);
        }
    }

    private SpeakingContract.Model model = new SpeakingContract.Model() {
        private int currentIdx = 0;
        private int count = 5;
        @Override public void loadQuestions(String levelName, String topicId, SpeakingContract.QuestionListener listener) {}
        @Override public void evaluateAnswer(String script, String userAnswerRecordingPath, SpeakingContract.EvaluationListener listener) {}
        @Override public String getCurrentQuestion() { return "Đây là câu hỏi/script mẫu số " + (currentIdx + 1); }
        @Override public int getCurrentQuestionIndex() { return currentIdx; }
        @Override public int getQuestionCount() { return count; }
        @Override public void advanceQuestionIndex() { if(currentIdx < count - 1) currentIdx++; }
        @Override public void previousQuestionIndex() { if(currentIdx > 0) currentIdx--; }
        @Override public void cleanup() {}
    };
}
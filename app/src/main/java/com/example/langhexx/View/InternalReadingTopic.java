package com.example.langhexx.View;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.langhexx.Controller.ReadingController;
import com.example.langhexx.Controller.ReadingQuestionListAdapter;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.ReadingQuestion;
import com.example.langhexx.R;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class InternalReadingTopic extends AppCompatActivity implements
        ReadingController.ViewInterface,
        ReadingQuestionListAdapter.OnAnswerSelectedListener {

    private static final String TAG = "InternalReadTopicVIEW";

    private ImageView imgClose, imgHome;
    private ListView lvQuestions;
    private Button btnSubmit;
    private TextView tvExerciseDisplayTitle;
    private TextView tvPassageDisplay;
    private TextView instructionText;
    private boolean isPlaying = false;
    private ImageView btnPlay;

    private List<ReadingQuestion> questionsListForAdapter;
    private ReadingQuestionListAdapter questionListAdapter;
    private ReadingController controller;

    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_reading_topic);
        Log.d(TAG, "onCreate");

        addControls();
        controller = new ReadingController(this, getIntent());
        setupListView();
        controller.initialize();
        addEvents();
    }

    private void addControls() {
        lvQuestions = findViewById(R.id.lvQuestions);
        btnSubmit = findViewById(R.id.btnSubmit);
        tvExerciseDisplayTitle = findViewById(R.id.txtTitle);
        tvPassageDisplay = findViewById(R.id.txtParagraph);
        instructionText = findViewById(R.id.textView16);
        imgClose = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);
        btnPlay = findViewById(R.id.btnPlay);
        setUIElementsVisibility(false);
    }

    private void setupListView() {
        questionsListForAdapter = new ArrayList<>();
        questionListAdapter = new ReadingQuestionListAdapter(this, questionsListForAdapter, this);
        lvQuestions.setAdapter(questionListAdapter);
    }

    private void addEvents() {
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                if (controller != null) {
                    controller.onSubmitButtonClicked();
                }
            });
        }

        imgClose.setOnClickListener(view -> finish());

        imgHome.setOnClickListener(view -> {
            Intent intent = new Intent(InternalReadingTopic.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        btnPlay.setOnClickListener(v -> {
            if (isPlaying) {
                stopMediaPlayer();
                btnPlay.setImageResource(R.drawable.ic_play);
                isPlaying = false;
            } else {
                String textToSpeak = tvPassageDisplay.getText().toString().trim();
                if (textToSpeak.isEmpty()) {
                    showToast("No passage to speak.");
                    return;
                }
                isPlaying = true;
                btnPlay.setImageResource(R.drawable.reading_icon_pause);
                if (controller != null) {
                    controller.synthesizeTextToSpeech(textToSpeak, new ReadingController.TTSCallback() {
                        @Override
                        public void onStart() {
                            runOnUiThread(() -> {
                                btnPlay.setImageResource(R.drawable.reading_icon_pause);
                                isPlaying = true;
                            });
                        }
                        @Override
                        public void onDone(byte[] audioData) {
                            runOnUiThread(() -> playAudioFromBytes(audioData));
                        }
                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                showFailToast(message);
                                btnPlay.setImageResource(R.drawable.ic_play);
                                isPlaying = false;
                            });
                        }
                    });
                }
            }
        });


    }

    private void playAudioFromBytes(byte[] audioBytes) {
        try {
            stopMediaPlayer();

            String fileName = "tts_output.mp3";
            java.io.File tempMp3 = new java.io.File(getCacheDir(), fileName);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempMp3);
            fos.write(audioBytes);
            fos.close();

            android.net.Uri uri = android.net.Uri.fromFile(tempMp3);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getApplicationContext(), uri);
            mediaPlayer.setOnPreparedListener(mp -> {
                mediaPlayer.start();
                isPlaying = true; // Set lại trạng thái
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlay.setImageResource(R.drawable.ic_play);
                isPlaying = false;
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                btnPlay.setImageResource(R.drawable.ic_play);
                isPlaying = false;
                showFailToast("Playback failed.");
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Play error: " + e.getMessage());
            showFailToast("Playback failed.");
            btnPlay.setImageResource(R.drawable.ic_play);
            isPlaying = false;
        }
    }


    private void stopMediaPlayer() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(null);
                mediaPlayer.setOnErrorListener(null);
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception e) {

        }
        isPlaying = false;
    }


    @Override public void displayExerciseTitle(String title) {
        runOnUiThread(() -> {
            if (tvExerciseDisplayTitle != null) {
                tvExerciseDisplayTitle.setText(title != null ? title : "Reading Exercise");
            }
        });
    }

    @Override public void displayPassage(String text) {
        runOnUiThread(() -> {
            if (tvPassageDisplay != null) {
                tvPassageDisplay.setText(text != null && !text.isEmpty() ? text : "Content not available.");
            }
        });
    }

    @Override public void updateAdapterData(List<ReadingQuestion> newQuestions) {
        runOnUiThread(() -> {
            if (questionListAdapter != null && newQuestions != null) {
                questionListAdapter.updateData(newQuestions);
            } else {
                if (lvQuestions != null) lvQuestions.setVisibility(View.GONE);
                if (instructionText != null) instructionText.setVisibility(View.GONE);
            }
        });
    }

    @Override public void showResultsInAdapter(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctnessMap) {
        runOnUiThread(() -> {
            if (questionListAdapter != null) {
                questionListAdapter.showResults(userAnswers, correctnessMap);
            }
        });
    }

    @Override public void resetAdapterState() {
        runOnUiThread(() -> {
            if (questionListAdapter != null) {
                questionListAdapter.resetQuizState();
            }
        });
    }

    @Override public void setButtonState(int state, String text) {
        runOnUiThread(() -> {
            if (btnSubmit != null) {
                btnSubmit.setText(text);
                btnSubmit.setEnabled(state != ReadingController.STATE_FINISHED_NO_NEXT || hasNextExercise());
                btnSubmit.setAlpha(btnSubmit.isEnabled() ? 1.0f : 0.5f);
            }
        });
    }

    private boolean hasNextExercise() { return true; }

    @Override public Map<Integer, Integer> getAdapterSelectedAnswers() {
        return questionListAdapter != null ? questionListAdapter.getSelectedAnswers() : new HashMap<>();
    }

    @Override public boolean areAdapterAnswersAllCorrect() {
        return questionListAdapter != null && questionListAdapter.areAllAnswersCorrect();
    }

    @Override public void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override public void showFailToast(String message) {
        runOnUiThread(() -> CustomToast.showFail(this, message, R.drawable.fail_icon));
    }

    @Override public void showConfirmationDialog(String title, String message, Runnable onConfirm) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Submit", (dialog, which) -> onConfirm.run())
                .setNegativeButton("Cancel", null)
                .show());
    }

    @Override public void navigateToNextExercise(String levelName, String topicId, String nextExerciseId, String topicDisplayTitle, String nextExerciseDisplayTitle) {
        Intent nextIntent = new Intent(this, InternalReadingTopic.class);
        nextIntent.putExtra("LEVEL_NAME", levelName);
        nextIntent.putExtra("TOPIC_ID", topicId);
        nextIntent.putExtra("EXERCISE_ID", nextExerciseId);
        nextIntent.putExtra("TOPIC_TITLE", topicDisplayTitle);
        nextIntent.putExtra("EXERCISE_TITLE", nextExerciseDisplayTitle);
        startActivity(nextIntent);
        finish();
    }

    @Override public void finishActivity() {
        finish();
    }

    @Override public void scrollToQuestion(int index) {
        if (lvQuestions != null && questionListAdapter != null) {
            lvQuestions.post(() -> lvQuestions.smoothScrollToPosition(index));
        }
    }

    @Override public void setUIElementsVisibility(boolean visible) {
        runOnUiThread(() -> {
            int vis = visible ? View.VISIBLE : View.INVISIBLE;
            if (tvExerciseDisplayTitle != null) tvExerciseDisplayTitle.setVisibility(View.VISIBLE);
            boolean hasPassage = tvPassageDisplay != null && !tvPassageDisplay.getText().toString().isEmpty();
            if (tvPassageDisplay != null) tvPassageDisplay.setVisibility(visible && hasPassage ? View.VISIBLE : View.GONE);
            boolean hasQuestions = questionListAdapter != null && questionListAdapter.getCount() > 0;
            if (lvQuestions != null) lvQuestions.setVisibility(visible && hasQuestions ? View.VISIBLE : View.GONE);
            if (instructionText != null) instructionText.setVisibility(visible && hasQuestions ? View.VISIBLE : View.GONE);
            if (btnSubmit != null) btnSubmit.setVisibility(visible && hasQuestions ? View.VISIBLE : View.GONE);
        });
    }

    @Override public void setAdapterAnswerListener() {}
    @Override public void applySavedAnswersToAdapter(Map<Integer, Integer> savedAnswers) {}
    @Override public void onAnswerSelected(int questionIndex, int selectedOptionId) {}

    @Override protected void onPause() { super.onPause(); }
    @Override protected void onResume() { super.onResume(); }
    @Override protected void onStop() { super.onStop(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (controller != null) controller.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override public void onBackPressed() {
        super.onBackPressed();
    }
}

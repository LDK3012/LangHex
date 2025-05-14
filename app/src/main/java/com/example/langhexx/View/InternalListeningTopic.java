package com.example.langhexx.View;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.langhexx.Controller.ListeningController;
import com.example.langhexx.Controller.ListeningQuestionListAdapter;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.ListeningQuestion;
import com.example.langhexx.R;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InternalListeningTopic extends AppCompatActivity implements
        ListeningController.ViewInterface,
        SeekBar.OnSeekBarChangeListener,
        ListeningQuestionListAdapter.OnAnswerSelectedListener {

    private static final String TAG = "InternalListenTopicVIEW";

    private ImageView imgClose, imgHome;
    private ImageButton btnPlayAudio;
    private SeekBar seekBarAudio;
    private ListView lvQuestions;
    private Button btnSubmit;
    private ProgressBar progressBarAudioLoading;
    private TextView tvScreenTitle;
    private ListeningQuestionListAdapter questionListAdapter;
    private ListeningController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_listening_topic);
        Log.d(TAG, "onCreate");

        addControls();
        controller = new ListeningController(this, getIntent());
        setupListView();
        controller.initialize();

        addEvents();
    }

    private void addControls() {
        Log.d(TAG, "addControls");
        btnPlayAudio = findViewById(R.id.btnPlayAudio);
        seekBarAudio = findViewById(R.id.seekBarAudio);
        lvQuestions = findViewById(R.id.lvQuestions);
        btnSubmit = findViewById(R.id.btnSubmit);
        progressBarAudioLoading = findViewById(R.id.progressBarAudioLoading);
        tvScreenTitle = findViewById(R.id.tvScreenTitle);
        imgClose = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);
        if (progressBarAudioLoading != null) {
            progressBarAudioLoading.setVisibility(View.VISIBLE);
        }
        if (btnPlayAudio != null) {
            btnPlayAudio.setVisibility(View.INVISIBLE);
            btnPlayAudio.setEnabled(false);
        }
        if (seekBarAudio != null) {
            seekBarAudio.setEnabled(false);
            seekBarAudio.setProgress(0);
        }
    }

    private void setupListView() {
        Log.d(TAG, "setupListView");
        questionListAdapter = new ListeningQuestionListAdapter(this, new ArrayList<>(), this);
        lvQuestions.setAdapter(questionListAdapter);
    }

    private void addEvents() {
        Log.d(TAG, "addEvents");
        if (btnPlayAudio != null) {
            btnPlayAudio.setOnClickListener(v -> {
                if (controller != null) {
                    controller.togglePlayPause();
                }
            });
        }
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                if (controller != null) {
                    controller.onSubmitButtonClicked();
                }
            });
        }
        if (seekBarAudio != null) {
            seekBarAudio.setOnSeekBarChangeListener(this);
        }

        if (imgClose != null) {
            imgClose.setOnClickListener(view -> finish());
        }

        if (imgHome != null) {
            imgHome.setOnClickListener(view -> {
                Intent intent = new Intent(InternalListeningTopic.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void displayExerciseTitle(String title) {
        Log.d(TAG, "displayExerciseTitle: " + title);
        runOnUiThread(() -> {
            if (tvScreenTitle != null) {
                tvScreenTitle.setText(title != null ? title : "Exercise");
            }
        });
    }

    @Override
    public void updateAdapterData(List<ListeningQuestion> newQuestions) {
        Log.d(TAG, "updateAdapterData in View: Received " + (newQuestions != null ? newQuestions.size() : 0) + " questions.");
        runOnUiThread(() -> {
            if (questionListAdapter != null && newQuestions != null) {
                questionListAdapter.updateData(newQuestions);
                Log.d(TAG, "Adapter notified with " + newQuestions.size() + " questions.");
            } else {
                Log.w(TAG, "Adapter is null or newQuestions is null, cannot update UI.");
                if (questionListAdapter != null) {
                    questionListAdapter.updateData(new ArrayList<>());
                }
            }
            setUIElementsVisibility(newQuestions != null && !newQuestions.isEmpty());
        });
    }

    @Override
    public void showResultsInAdapter(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctnessMap) {
        Log.d(TAG, "showResultsInAdapter");
        runOnUiThread(() -> {
            if (questionListAdapter != null) {
                questionListAdapter.showResults(userAnswers, correctnessMap);
            } else {
                Log.e(TAG, "Cannot show results, adapter is null");
            }
        });
    }

    @Override
    public void resetAdapterState() {
        Log.d(TAG, "resetAdapterState in View");
        runOnUiThread(() -> {
            if (questionListAdapter != null) {
                questionListAdapter.resetQuizState();
            } else {
                Log.e(TAG, "Cannot reset adapter state, adapter is null");
            }
        });
    }

    @Override
    public void setSubmitButtonState(int state, String text, boolean enabled) {
        Log.d(TAG, "setSubmitButtonState: State=" + state + ", Text=" + text + ", Enabled=" + enabled);
        runOnUiThread(() -> {
            if (btnSubmit != null) {
                btnSubmit.setText(text);
                btnSubmit.setEnabled(enabled);
                btnSubmit.setAlpha(enabled ? 1.0f : 0.5f);
            }
        });
    }

    @Override
    public Map<Integer, Integer> getAdapterSelectedAnswers() {
        if (questionListAdapter != null) {
            return questionListAdapter.getSelectedAnswers();
        }
        Log.e(TAG, "getAdapterSelectedAnswers: Adapter is null");
        return new HashMap<>();
    }

    @Override
    public boolean areAdapterAnswersAllCorrect() {
        if (questionListAdapter != null) {
            return questionListAdapter.areAllAnswersCorrect();
        }
        Log.e(TAG, "areAdapterAnswersAllCorrect: Adapter is null");
        return false;
    }

    @Override
    public void showToast(String message) {
        Log.d(TAG, "showToast: " + message);
        runOnUiThread(() -> Toast.makeText(InternalListeningTopic.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void showFailToast(String message) {
        Log.d(TAG, "showFailToast: " + message);
        runOnUiThread(() -> CustomToast.showFail(InternalListeningTopic.this, message, R.drawable.fail_icon));
    }

    @Override
    public void showConfirmationDialog(String title, String message, Runnable onConfirm) {
        Log.d(TAG, "showConfirmationDialog");
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Submit", (dialog, which) -> onConfirm.run())
                .setNegativeButton("Cancel", (dialog, which) -> Log.d(TAG, "Submission cancelled."))
                .setCancelable(false)
                .show());
    }

    @Override
    public void navigateToNextExercise(String levelName, String topicId, String nextExerciseId, String topicDisplayTitle, String nextExerciseDisplayTitle) {
        Log.i(TAG, "Navigating to next Listening exercise: Level=" + levelName +
                ", TopicID=" + topicId + ", NextExerciseID=" + nextExerciseId +
                ", TopicTitle=" + topicDisplayTitle + ", NextExerciseTitle=" + nextExerciseDisplayTitle);
        Intent nextIntent = new Intent(InternalListeningTopic.this, InternalListeningTopic.class);
        nextIntent.putExtra("LEVEL_NAME", levelName);
        nextIntent.putExtra("TOPIC_ID", topicId);
        nextIntent.putExtra("EXERCISE_ID", nextExerciseId);
        nextIntent.putExtra("TOPIC_TITLE", topicDisplayTitle);
        nextIntent.putExtra("EXERCISE_TITLE", nextExerciseDisplayTitle);
        startActivity(nextIntent);
        finish();
    }

    @Override
    public void finishActivity() {
        Log.i(TAG, "finishActivity called by controller.");
        finish();
    }

    @Override
    public void scrollToQuestion(int index) {
        Log.d(TAG, "scrollToQuestion: " + index);
        if (lvQuestions != null) {
            lvQuestions.post(() -> {
                if (questionListAdapter != null && index >= 0 && index < questionListAdapter.getCount()) {
                    lvQuestions.smoothScrollToPosition(index);
                } else {
                    Log.w(TAG, "Invalid index for scrollToQuestion or adapter not ready: " + index);
                }
            });
        }
    }

    @Override
    public void setUIElementsVisibility(boolean visible) {
        Log.d(TAG, "setUIElementsVisibility: " + visible);
        runOnUiThread(() -> {
            if (lvQuestions != null) {
                boolean hasItems = questionListAdapter != null && questionListAdapter.getCount() > 0;
                lvQuestions.setVisibility(visible && hasItems ? View.VISIBLE : View.INVISIBLE);
            }
            if (btnSubmit != null) {
                boolean hasItems = questionListAdapter != null && questionListAdapter.getCount() > 0;
                btnSubmit.setVisibility(visible && hasItems ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

    @Override
    public void showAudioLoading() {
        Log.d(TAG, "showAudioLoading");
        runOnUiThread(() -> {
            if (progressBarAudioLoading != null) progressBarAudioLoading.setVisibility(View.VISIBLE);
            if (btnPlayAudio != null) {
                btnPlayAudio.setVisibility(View.INVISIBLE);
                btnPlayAudio.setEnabled(false);
            }
            if (seekBarAudio != null) {
                seekBarAudio.setEnabled(false);
                seekBarAudio.setProgress(0);
            }
        });
    }

    @Override
    public void showAudioReady(int duration) {
        Log.d(TAG, "showAudioReady, Duration: " + duration);
        runOnUiThread(() -> {
            if (progressBarAudioLoading != null) progressBarAudioLoading.setVisibility(View.GONE);
            if (btnPlayAudio != null) {
                btnPlayAudio.setVisibility(View.VISIBLE);
                btnPlayAudio.setImageResource(R.drawable.icon_play_audio);
                btnPlayAudio.setEnabled(true);
            }
            if (seekBarAudio != null) {
                seekBarAudio.setEnabled(true);
                seekBarAudio.setMax(duration);
                seekBarAudio.setProgress(0);
            }
        });
    }

    @Override
    public void updateAudioProgress(int progress, int max) {
        runOnUiThread(() -> {
            if (seekBarAudio != null && seekBarAudio.isEnabled()) {
                if(seekBarAudio.getMax() != max && max > 0) seekBarAudio.setMax(max);
                if (progress >= 0 && progress <= seekBarAudio.getMax()) {
                    seekBarAudio.setProgress(progress);
                }
            }
        });
    }

    @Override
    public void setPlayButtonState(boolean isPlaying) {
        Log.d(TAG, "setPlayButtonState: isPlaying=" + isPlaying);
        runOnUiThread(() -> {
            if (btnPlayAudio != null) {
                btnPlayAudio.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.icon_play_audio);
            }
        });
    }

    /**
     * Đảm bảo resetAudioControls luôn đưa UI về trạng thái loading với ProgressBar.
     */
    @Override
    public void resetAudioControls() {
        Log.d(TAG, "resetAudioControls - Reverting to loading UI state");
        runOnUiThread(() -> {
            if (progressBarAudioLoading != null) {
                progressBarAudioLoading.setVisibility(View.VISIBLE);
            }
            if (btnPlayAudio != null) {
                btnPlayAudio.setVisibility(View.INVISIBLE);
                btnPlayAudio.setImageResource(R.drawable.icon_play_audio);
                btnPlayAudio.setEnabled(false);
            }
            if (seekBarAudio != null) {
                seekBarAudio.setEnabled(false);
                seekBarAudio.setProgress(0);
                seekBarAudio.setMax(100);
            }
        });
    }

    @Override
    public void showAudioError(String errorMessage) {
        Log.e(TAG, "showAudioError: " + errorMessage);
        showToast("Audio Error: " + errorMessage);
        resetAudioControls();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        //
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "SeekBar tracking started by user.");
        //
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "SeekBar tracking stopped by user at: " + seekBar.getProgress());
        if (controller != null) {
            controller.seekAudio(seekBar.getProgress());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause.");
        if (controller != null) {
            controller.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume.");
        if (controller != null) {
            controller.onResume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy.");
        if (controller != null) {
            controller.onDestroy();
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed.");
        super.onBackPressed();
    }

    @Override
    public void applySavedAnswersToAdapter(Map<Integer, Integer> savedAnswers) {
        Log.d(TAG, "applySavedAnswersToAdapter called. Current adapter might handle this via initial data.");
    }

    @Override
    public void setAdapterAnswerListener() {
        Log.d(TAG,"Adapter listener set via constructor.");
    }

    @Override
    public void onAnswerSelected(int questionIndex, int selectedOptionId) {
        Log.d(TAG, "onAnswerSelected: Q" + (questionIndex + 1) + ", OptionID (RadioButton ID): " + selectedOptionId);
        if (controller != null) {
            controller.saveAnswerSelection(questionIndex, selectedOptionId);
        }
    }
}
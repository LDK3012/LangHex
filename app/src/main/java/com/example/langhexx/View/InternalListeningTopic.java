package com.example.langhexx.View;

import android.content.Context; // Added for ViewInterface
import android.content.Intent;
// Removed MediaPlayer, TextToSpeech, UtteranceProgressListener imports as they are handled by Controller
import android.os.Bundle;
// Removed Handler import
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

import androidx.annotation.NonNull; // Keep for potential future use? (Not strictly needed now)
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.langhexx.Controller.ListeningController; // Import the Controller
import com.example.langhexx.Controller.ListeningQuestionListAdapter;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.ListeningQuestion;
import com.example.langhexx.R;

// Removed Firebase imports as they are handled by Controller
// Removed File, IOException, Locale, UUID imports

import java.util.ArrayList; // Keep for initializing list
import java.util.HashMap; // Keep for getAdapterSelectedAnswers return type
import java.util.List;
import java.util.Map;


public class InternalListeningTopic extends AppCompatActivity implements
        ListeningController.ViewInterface, // Implement the controller's interface
        SeekBar.OnSeekBarChangeListener { // Keep for seekbar interaction delegation

    private static final String TAG = "InternalListenTopicVIEW"; // Changed TAG for clarity

    private ImageView imgClose, imgHome;

    // --- UI Elements ---
    private ImageButton btnPlayAudio;
    private SeekBar seekBarAudio;
    private ListView lvQuestions;
    private Button btnSubmit;
    private ProgressBar progressBarAudioLoading;
    private TextView tvScreenTitle; // Renamed for clarity (was tvExerciseDisplayTitle previously often)
    private ListeningQuestionListAdapter questionListAdapter;

    // --- Controller ---
    private ListeningController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_listening_topic);
        Log.d(TAG, "onCreate");

        addControls(); // Find UI elements
        setupListView(); // Setup adapter (initially empty)

        controller = new ListeningController(this, getIntent());
        controller.initialize(); // Start loading data, TTS init, etc.

        addEvents();
    }

    private void addControls() {
        Log.d(TAG, "addControls");
        btnPlayAudio = findViewById(R.id.btnPlayAudio);
        seekBarAudio = findViewById(R.id.seekBarAudio);
        lvQuestions = findViewById(R.id.lvQuestions);
        btnSubmit = findViewById(R.id.btnSubmit);
        progressBarAudioLoading = findViewById(R.id.progressBarAudioLoading);
        tvScreenTitle = findViewById(R.id.tvScreenTitle); // Make sure ID is correct in layout
        imgClose = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);

    }

    private void setupListView() {
        Log.d(TAG, "setupListView");
        // Adapter needs an initial empty list
        questionListAdapter = new ListeningQuestionListAdapter(this, new ArrayList<>());
        lvQuestions.setAdapter(questionListAdapter);
    }

    private void addEvents() {
        Log.d(TAG, "addEvents");
        if (btnPlayAudio != null) {
            btnPlayAudio.setOnClickListener(v -> {
                if (controller != null) {
                    controller.togglePlayPause(); // Delegate to controller
                }
            });
        }
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                if (controller != null) {
                    controller.onSubmitButtonClicked(); // Delegate to controller
                }
            });
        }
        if (seekBarAudio != null) {
            seekBarAudio.setOnSeekBarChangeListener(this); // Keep 'this' as listener initially
        }

        imgClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        imgHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(InternalListeningTopic.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    // --- Implementation of ListeningController.ViewInterface ---

    @Override
    public Context getContext() {
        return this; // Provide activity context when controller needs it
    }

    @Override
    public void displayExerciseTitle(String title) {
        Log.d(TAG, "displayExerciseTitle: " + title);
        runOnUiThread(() -> {
            if (tvScreenTitle != null) {
                tvScreenTitle.setText(title != null ? title : "Exercise"); // Set title, provide default
            }
        });
    }

    @Override
    public void updateAdapterData(List<ListeningQuestion> newQuestions) {
        Log.d(TAG, "updateAdapterData: Received " + (newQuestions != null ? newQuestions.size() : 0) + " questions.");
        runOnUiThread(() -> {
            if (questionListAdapter != null && newQuestions != null) {
                questionListAdapter.updateData(newQuestions); // Update adapter's data
                Log.d(TAG, "Adapter notified with " + newQuestions.size() + " questions.");
                // Visibility of list itself is handled by setUIElementsVisibility
            } else {
                Log.w(TAG, "Adapter is null or newQuestions is null, cannot update UI.");
                if (questionListAdapter != null) {
                    questionListAdapter.updateData(new ArrayList<>()); // Clear adapter if null data
                }
            }
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
        Log.d(TAG, "resetAdapterState");
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
        Log.d(TAG, "setSubmitButtonState: State=" + state + ", Text=" + text);
        runOnUiThread(() -> {
            if (btnSubmit != null) {
                btnSubmit.setText(text);
                // Determine enabled state based on common patterns (adjust if controller needs more fine-grained control)
                // More robust: Controller could pass enabled flag separately if needed
                btnSubmit.setEnabled(enabled);
                btnSubmit.setAlpha(enabled ? 1.0f : 0.5f); // Visual cue for disabled
            }
        });
    }

    @Override
    public Map<Integer, Integer> getAdapterSelectedAnswers() {
        if (questionListAdapter != null) {
            return questionListAdapter.getSelectedAnswers();
        }
        Log.e(TAG, "getAdapterSelectedAnswers: Adapter is null");
        return new HashMap<>(); // Return empty map if adapter is null
    }

    @Override
    public boolean areAdapterAnswersAllCorrect() {
        // This method might not be needed if the adapter's internal check isn't used by the controller
        // If controller relies on its own scoring, this could be removed from interface/activity.
        // Keeping it for now as it was in Reading example.
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
        // Ensure you have the CustomToast class and fail_icon drawable
        runOnUiThread(() -> CustomToast.showFail(InternalListeningTopic.this, message, R.drawable.fail_icon));
    }

    @Override
    public void showConfirmationDialog(String title, String message, Runnable onConfirm) {
        Log.d(TAG, "showConfirmationDialog");
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Submit", (dialog, which) -> onConfirm.run()) // Execute Runnable on confirm
                .setNegativeButton("Cancel", (dialog, which) -> Log.d(TAG, "Submission cancelled."))
                .setCancelable(false) // Prevent dismissing by tapping outside
                .show());
    }

    @Override
    public void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle) {
        Log.i(TAG, "navigateToNextExercise: " + nextExerciseTitle);
        Intent nextIntent = new Intent(InternalListeningTopic.this, InternalListeningTopic.class); // Navigate to self
        nextIntent.putExtra("LEVEL_NAME", levelName);
        nextIntent.putExtra("TOPIC_TITLE", topicTitle);
        nextIntent.putExtra("EXERCISE_TITLE", nextExerciseTitle);
        startActivity(nextIntent);
        finish(); // Finish current activity
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
            // Use post to ensure scrolling happens after layout calculation
            lvQuestions.post(() -> lvQuestions.smoothScrollToPosition(index));
        }
    }

    @Override
    public void setUIElementsVisibility(boolean visible) {
        Log.d(TAG, "setUIElementsVisibility: " + visible);
        runOnUiThread(() -> {
            int visibility = visible ? View.VISIBLE : View.INVISIBLE; // Use INVISIBLE to keep layout space
            // Only control elements related to the questions/submit flow here
            // Audio controls visibility is handled by showAudioLoading/Ready/Reset
            if (lvQuestions != null) {
                // Show list only if visible AND adapter has items
                boolean hasItems = questionListAdapter != null && questionListAdapter.getCount() > 0;
                lvQuestions.setVisibility(visible && hasItems ? View.VISIBLE : View.INVISIBLE);
            }
            if (btnSubmit != null) {
                boolean hasItems = questionListAdapter != null && questionListAdapter.getCount() > 0;
                btnSubmit.setVisibility(visible && hasItems ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }


    // --- Audio Control Specific Interface Methods ---

    @Override
    public void showAudioLoading() {
        Log.d(TAG, "showAudioLoading");
        runOnUiThread(() -> {
            if (progressBarAudioLoading != null) progressBarAudioLoading.setVisibility(View.VISIBLE);
            if (btnPlayAudio != null) btnPlayAudio.setVisibility(View.INVISIBLE);
            if (seekBarAudio != null) seekBarAudio.setEnabled(false);
            if (btnPlayAudio != null) btnPlayAudio.setEnabled(false);
        });
    }

    @Override
    public void showAudioReady(int duration) {
        Log.d(TAG, "showAudioReady, Duration: " + duration);
        runOnUiThread(() -> {
            if (progressBarAudioLoading != null) progressBarAudioLoading.setVisibility(View.GONE);
            if (btnPlayAudio != null) {
                btnPlayAudio.setVisibility(View.VISIBLE);
                btnPlayAudio.setImageResource(R.drawable.icon_play_audio); // Default to Play icon
                btnPlayAudio.setEnabled(true);
            }
            if (seekBarAudio != null) {
                seekBarAudio.setEnabled(true);
                seekBarAudio.setMax(duration);
                seekBarAudio.setProgress(0); // Start at beginning
            }
        });
    }

    @Override
    public void updateAudioProgress(int progress, int max) {
        // No Log here as it's called frequently
        runOnUiThread(() -> {
            if (seekBarAudio != null && seekBarAudio.isEnabled()) { // Check if enabled
                // Update max just in case duration wasn't ready initially (unlikely but safe)
                if(seekBarAudio.getMax() != max) seekBarAudio.setMax(max);
                // Ensure progress doesn't exceed max visually
                seekBarAudio.setProgress(Math.min(progress, max));
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

    @Override
    public void resetAudioControls() {
        Log.d(TAG, "resetAudioControls");
        runOnUiThread(() -> {
            if (progressBarAudioLoading != null) progressBarAudioLoading.setVisibility(View.GONE);
            if (btnPlayAudio != null) {
                btnPlayAudio.setVisibility(View.VISIBLE);
                btnPlayAudio.setImageResource(R.drawable.icon_play_audio);
                btnPlayAudio.setEnabled(false); // Disabled initially
            }
            if (seekBarAudio != null) {
                seekBarAudio.setEnabled(false);
                seekBarAudio.setProgress(0);
                seekBarAudio.setMax(100); // Reset max to default or 0
            }
        });
    }

    @Override
    public void showAudioError(String errorMessage) {
        Log.e(TAG, "showAudioError: " + errorMessage);
        // Show toast AND reset controls typically
        showToast("Audio Error: " + errorMessage); // Use regular toast for errors
        resetAudioControls(); // Put controls back into a safe, disabled state
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        // Can be used for displaying time dynamically if needed, but seeking action is onStopTrackingTouch
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "SeekBar tracking started by user.");
        // No direct action needed on controller here, stopSeekBarUpdate is handled internally by controller if playing
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "SeekBar tracking stopped by user at: " + seekBar.getProgress());
        if (controller != null) {
            controller.seekAudio(seekBar.getProgress()); // Delegate seek action to controller
        }
    }
    // --- End SeekBar.OnSeekBarChangeListener ---


    // --- Lifecycle Methods (Forwarding to Controller) ---
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

}

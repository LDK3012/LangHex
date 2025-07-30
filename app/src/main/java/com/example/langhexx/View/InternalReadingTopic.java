package com.example.langhexx.View;

import android.content.Intent;
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
import com.example.langhexx.Controller.ReadingQuestionListAdapter; // Ensure correct adapter import
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.ReadingQuestion; // Ensure correct model import
import com.example.langhexx.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Implement the adapter's listener interface
public class InternalReadingTopic extends AppCompatActivity implements
        ReadingController.ViewInterface,
        ReadingQuestionListAdapter.OnAnswerSelectedListener { // Add listener implementation

    private static final String TAG = "InternalReadTopicVIEW";

    private ImageView imgClose, imgHome;
    private ListView lvQuestions;
    private Button btnSubmit;
    private TextView tvExerciseDisplayTitle;
    private TextView tvPassageDisplay;
    private TextView instructionText;

    // Keep local list for the adapter, but let controller manage data flow
    private List<ReadingQuestion> questionsListForAdapter;
    private ReadingQuestionListAdapter questionListAdapter;
    private ReadingController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_reading_topic);
        Log.d(TAG, "onCreate");

        addControls();

        // Initialize Controller first
        controller = new ReadingController(this, getIntent());

        // Setup ListView after controller is created so listener can be passed
        setupListView();

        // Controller handles data loading and initial UI population via interface methods
        controller.initialize();

        addEvents();
    }

    private void addControls() {
        Log.d(TAG, "addControls");
        lvQuestions = findViewById(R.id.lvQuestions);
        btnSubmit = findViewById(R.id.btnSubmit);
        tvExerciseDisplayTitle = findViewById(R.id.txtTitle);
        tvPassageDisplay = findViewById(R.id.txtParagraph);
        instructionText = findViewById(R.id.textView16);
        imgClose = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);
        // Initial visibility setup, controller will manage updates
        setUIElementsVisibility(false);
    }

    private void setupListView() {
        Log.d(TAG, "setupListView");
        questionsListForAdapter = new ArrayList<>(); // Initialize empty list for adapter
        // Pass 'this' as the listener to the adapter's constructor
        questionListAdapter = new ReadingQuestionListAdapter(this, questionsListForAdapter, this);
        lvQuestions.setAdapter(questionListAdapter);
    }

    private void addEvents() {
        Log.d(TAG, "addEvents");
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                if (controller != null) {
                    controller.onSubmitButtonClicked();
                }
            });
        }

        if (imgClose != null) {
            imgClose.setOnClickListener(view -> finish());
        }

        if (imgHome != null) {
            imgHome.setOnClickListener(view -> {
                Intent intent = new Intent(InternalReadingTopic.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    // --- ReadingController.ViewInterface Methods ---

    @Override
    public void displayExerciseTitle(String title) {
        Log.d(TAG, "displayExerciseTitle: " + title);
        runOnUiThread(() -> {
            if (tvExerciseDisplayTitle != null) {
                tvExerciseDisplayTitle.setText(title != null ? title : "Reading Exercise");
            }
        });
    }

    @Override
    public void displayPassage(String text) {
        Log.d(TAG, "displayPassage: " + (text != null && text.length() > 50 ? text.substring(0, 50) + "..." : text));
        runOnUiThread(() -> {
            if (tvPassageDisplay != null) {
                tvPassageDisplay.setText(text != null && !text.isEmpty() ? text : "Content not available.");
                // Manage visibility based on content existence within setUIElementsVisibility if needed
            }
        });
    }

    @Override
    public void updateAdapterData(List<ReadingQuestion> newQuestions) {
        Log.d(TAG, "updateAdapterData (View): Received " + (newQuestions != null ? newQuestions.size() : 0) + " questions.");
        runOnUiThread(() -> {
            if (questionListAdapter != null && newQuestions != null) {
                // The controller prepares the list (with initial selections), just pass it to adapter
                questionListAdapter.updateData(newQuestions);
                Log.d(TAG, "Adapter data updated via updateData.");
            } else {
                Log.w(TAG, "Adapter is null or newQuestions is null in updateAdapterData.");
                // Consider hiding list view if no data
                if(lvQuestions != null) lvQuestions.setVisibility(View.GONE);
                if(instructionText != null) instructionText.setVisibility(View.GONE);
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
                questionListAdapter.resetQuizState(); // Adapter handles resetting its UI state
            } else {
                Log.e(TAG, "Cannot reset adapter state, adapter is null");
            }
        });
    }

    @Override
    public void setButtonState(int state, String text) {
        Log.d(TAG, "setButtonState: State=" + state + ", Text=" + text);
        runOnUiThread(() -> {
            if (btnSubmit != null) {
                btnSubmit.setText(text);
                // Ensure button is enabled unless explicitly finished without next
                btnSubmit.setEnabled(state != ReadingController.STATE_FINISHED_NO_NEXT || hasNextExercise()); // Basic logic, might need refinement
                btnSubmit.setAlpha(btnSubmit.isEnabled() ? 1.0f : 0.5f);
            }
        });
    }
    // Helper needed for setButtonState logic, controller might expose this
    private boolean hasNextExercise() {
        // This is a placeholder, ideally controller manages this state fully
        // or provides a method like controller.hasNextExercise()
        return true; // Assume true for basic enabling logic
    }


    @Override
    public Map<Integer, Integer> getAdapterSelectedAnswers() {
        if (questionListAdapter != null) {
            return questionListAdapter.getSelectedAnswers(); // Get current selections from adapter
        }
        Log.e(TAG, "getAdapterSelectedAnswers: Adapter is null");
        return new HashMap<>();
    }

    @Override
    public boolean areAdapterAnswersAllCorrect() {
        if (questionListAdapter != null) {
            return questionListAdapter.areAllAnswersCorrect(); // Check correctness via adapter
        }
        Log.e(TAG, "areAdapterAnswersAllCorrect: Adapter is null");
        return false;
    }

    @Override
    public void showToast(String message) {
        Log.d(TAG, "showToast: " + message);
        runOnUiThread(() -> Toast.makeText(InternalReadingTopic.this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void showFailToast(String message) {
        Log.d(TAG, "showFailToast: " + message);
        runOnUiThread(() -> CustomToast.showFail(InternalReadingTopic.this, message, R.drawable.fail_icon));
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
        Log.i(TAG, "navigateToNextExercise: Level=" + levelName + ", TopicID=" + topicId +
                ", NextExerciseID=" + nextExerciseId + ", TopicTitle=" + topicDisplayTitle + ", NextExerciseTitle=" + nextExerciseDisplayTitle);
        Intent nextIntent = new Intent(InternalReadingTopic.this, InternalReadingTopic.class);
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
        Log.i(TAG, "finishActivity");
        finish();
    }

    @Override
    public void scrollToQuestion(int index) {
        Log.d(TAG, "scrollToQuestion: " + index);
        if (lvQuestions != null && questionListAdapter != null) { // Check adapter too
            lvQuestions.post(() -> {
                if (index >= 0 && index < questionListAdapter.getCount()) {
                    lvQuestions.smoothScrollToPosition(index);
                } else {
                    Log.w(TAG, "Invalid index for scrollToQuestion: " + index);
                }
            });
        } else {
            Log.w(TAG, "Cannot scroll, ListView or Adapter is null.");
        }
    }

    @Override
    public void setUIElementsVisibility(boolean visible) {
        Log.d(TAG, "setUIElementsVisibility: " + visible);
        runOnUiThread(() -> {
            int visibility = visible ? View.VISIBLE : View.INVISIBLE;
            // Always show title
            if (tvExerciseDisplayTitle != null) tvExerciseDisplayTitle.setVisibility(View.VISIBLE);

            // Show passage only if visible and has content
            boolean passageHasContent = false;
            if (tvPassageDisplay != null) {
                CharSequence currentPassageText = tvPassageDisplay.getText();
                passageHasContent = currentPassageText != null && !currentPassageText.toString().isEmpty() && !currentPassageText.toString().equals("Content not available.");
                tvPassageDisplay.setVisibility(visible && passageHasContent ? View.VISIBLE : View.GONE);
            }

            // Show questions/instructions/submit only if visible and questions exist
            boolean questionsAvailable = questionListAdapter != null && questionListAdapter.getCount() > 0;
            if (lvQuestions != null) lvQuestions.setVisibility(visible && questionsAvailable ? View.VISIBLE : View.GONE);
            if (instructionText != null) instructionText.setVisibility(visible && questionsAvailable ? View.VISIBLE : View.GONE);
            if (btnSubmit != null) btnSubmit.setVisibility(visible && questionsAvailable ? View.VISIBLE : View.GONE);

            // Log if nothing is shown
            if(visible && !passageHasContent && !questionsAvailable) {
                Log.d(TAG, "Setting UI visible, but no passage and no questions to display.");
            }
        });
    }

    // --- Added Methods for ViewInterface ---
    @Override
    public void setAdapterAnswerListener() {
        // Listener is set in setupListView via constructor in this implementation
        Log.d(TAG, "Adapter listener requested by controller (set in constructor).");
    }

    @Override
    public void applySavedAnswersToAdapter(Map<Integer, Integer> savedAnswers) {
        // This method might not be directly needed if the controller modifies
        // the model data before calling updateAdapterData.
        Log.d(TAG, "applySavedAnswersToAdapter called (currently handled via model update).");
        // If needed, implement: runOnUiThread(() -> questionListAdapter.applySavedSelections(savedAnswers));
    }

    // --- Implementation of ReadingQuestionListAdapter.OnAnswerSelectedListener ---
    @Override
    public void onAnswerSelected(int questionIndex, int selectedOptionId) {
//        Log.d(TAG, "onAnswerSelected (View): Q" + questionIndex + ", OptionID: " + selectedOptionId);
//        if (controller != null) {
//            // Notify controller to save the selection
//            controller.saveAnswerSelection(questionIndex, selectedOptionId);
//        }
    }

    // --- Activity Lifecycle Methods (No changes needed for progress saving) ---
    @Override protected void onPause() { super.onPause(); Log.d(TAG,"onPause."); }
    @Override protected void onResume() { super.onResume(); Log.d(TAG,"onResume."); }
    @Override protected void onStop() { super.onStop(); Log.d(TAG,"onStop."); }
    @Override protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG,"onDestroy.");
        if (controller != null) {
            controller.onDestroy(); // Let controller clean up if needed
        }
    }
    @Override public void onBackPressed() {
        super.onBackPressed();
        Log.d(TAG,"onBackPressed.");
    }
}
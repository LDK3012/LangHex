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

import com.example.langhexx.Controller.ReadingController; // Import Controller
import com.example.langhexx.Controller.ReadingQuestionListAdapter;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.ReadingQuestion;
import com.example.langhexx.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InternalReadingTopic extends AppCompatActivity implements ReadingController.ViewInterface { // Implement View Interface

    private static final String TAG = "InternalReadTopicVIEW";

    private ImageView imgClose, imgHome;

    // --- UI Elements ---
    private ListView lvQuestions;
    private Button btnSubmit;
    private TextView tvExerciseDisplayTitle;
    private TextView tvPassageDisplay;
    private TextView instructionText;
    // --- Adapters and Local Data List (Managed by View for UI binding) ---
    private List<ReadingQuestion> questionsList; // Holds data for the adapter
    private ReadingQuestionListAdapter questionListAdapter;

    // --- Controller ---
    private ReadingController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_reading_topic);
        Log.d(TAG, "onCreate");

        addControls();
        setupListView(); // Setup adapter early

        // Initialize Controller, passing the View (this) and Intent
        controller = new ReadingController(this, getIntent());

        // Controller will now handle loading data and initial setup logic
        controller.initialize(); // Start loading data etc.

        addEvents();
    }

    private void addControls() {
        Log.d(TAG, "addControls");
        lvQuestions = findViewById(R.id.lvQuestions);
        btnSubmit = findViewById(R.id.btnSubmit);
        tvExerciseDisplayTitle = findViewById(R.id.txtTitle);
        tvPassageDisplay = findViewById(R.id.txtParagraph);
        instructionText = findViewById(R.id.textView16); // Find instruction text
        imgClose = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);

        // Initial state (hidden until Controller provides data)
        setUIElementsVisibility(false);
    }

    private void setupListView() {
        Log.d(TAG, "setupListView");
        questionsList = new ArrayList<>(); // Initialize list
        questionListAdapter = new ReadingQuestionListAdapter(this, questionsList);
        lvQuestions.setAdapter(questionListAdapter);
    }

    private void addEvents() {
        Log.d(TAG, "addEvents");
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                // Delegate button click logic to the Controller
                controller.onSubmitButtonClicked();
            });
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
                Intent intent = new Intent(InternalReadingTopic.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });

    }

    // --- Implementation of ReadingController.ViewInterface ---

    @Override
    public void displayExerciseTitle(String title) {
        Log.d(TAG, "displayExerciseTitle: " + title);
        runOnUiThread(() -> {
            if (tvExerciseDisplayTitle != null) {
                tvExerciseDisplayTitle.setText(title);
            }
        });
    }

    @Override
    public void displayPassage(String text) {
        Log.d(TAG, "displayPassage: " + (text != null && !text.isEmpty() ? text.substring(0, Math.min(text.length(), 50))+"..." : "empty"));
        runOnUiThread(() -> {
            if (tvPassageDisplay != null) {
                tvPassageDisplay.setText(text != null ? text : "Content not available.");
            }
        });
    }

    @Override
    public void updateAdapterData(List<ReadingQuestion> newQuestions) {
        Log.d(TAG, "updateAdapterData: Received " + (newQuestions != null ? newQuestions.size() : 0) + " questions.");
        runOnUiThread(() -> {
            if (questionListAdapter != null && newQuestions != null) {
                questionListAdapter.updateData(newQuestions);
                Log.d(TAG, "Adapter notified with " + newQuestions.size() + " questions.");
                setUIElementsVisibility(true);
            } else {
                Log.w(TAG, "Adapter is null or newQuestions is null, cannot update UI.");
                // Optionally hide list if no questions
                if (lvQuestions != null) lvQuestions.setVisibility(View.GONE);
                if (instructionText != null) instructionText.setVisibility(View.GONE);
                if (btnSubmit != null) btnSubmit.setVisibility(View.GONE);

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
    public void setButtonState(int state, String text) {
        Log.d(TAG, "setButtonState: State=" + state + ", Text=" + text);
        runOnUiThread(() -> {
            if (btnSubmit != null) {
                btnSubmit.setText(text);
                btnSubmit.setEnabled(true);
                btnSubmit.setAlpha(1.0f);
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
        if (questionListAdapter != null) {
            return questionListAdapter.areAllAnswersCorrect();
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
                .setPositiveButton("Submit", (dialog, which) -> onConfirm.run()) // Execute Runnable on confirm
                .setNegativeButton("Cancel", (dialog, which) -> Log.d(TAG, "Submission cancelled."))
                .setCancelable(false)
                .show());
    }

    @Override
    public void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle) {
        Log.i(TAG, "navigateToNextExercise: " + nextExerciseTitle);
        Intent nextIntent = new Intent(InternalReadingTopic.this, InternalReadingTopic.class);
        nextIntent.putExtra("LEVEL_NAME", levelName);
        nextIntent.putExtra("TOPIC_TITLE", topicTitle);
        nextIntent.putExtra("EXERCISE_TITLE", nextExerciseTitle);
        startActivity(nextIntent);
        finish(); // Finish current activity after starting the next one
    }

    @Override
    public void finishActivity() {
        Log.i(TAG, "finishActivity");
        finish();
    }

    @Override
    public void scrollToQuestion(int index) {
        Log.d(TAG, "scrollToQuestion: " + index);
        if (lvQuestions != null) {
            lvQuestions.post(() -> lvQuestions.smoothScrollToPosition(index));
        }
    }

    @Override
    public void setUIElementsVisibility(boolean visible) {
        Log.d(TAG, "setUIElementsVisibility: " + visible);
        runOnUiThread(() -> {
            int visibility = visible ? View.VISIBLE : View.INVISIBLE;
            if (tvExerciseDisplayTitle != null) tvExerciseDisplayTitle.setVisibility(visibility);
            if (tvPassageDisplay != null) tvPassageDisplay.setVisibility(visibility);
            // Only show list/instructions/button if there's actual content (Controller determines 'visible')
            if (lvQuestions != null) lvQuestions.setVisibility(visible && questionListAdapter != null && questionListAdapter.getCount() > 0 ? View.VISIBLE : View.GONE);
            if (instructionText != null) instructionText.setVisibility(visible && questionListAdapter != null && questionListAdapter.getCount() > 0 ? View.VISIBLE : View.GONE);
            if (btnSubmit != null) btnSubmit.setVisibility(visible && questionListAdapter != null && questionListAdapter.getCount() > 0 ? View.VISIBLE : View.GONE);

            // Hide passage view specifically if passage text is null/empty, even if 'visible' is true overall
            if (tvPassageDisplay != null && visible) {
                CharSequence currentText = tvPassageDisplay.getText();
                if (currentText == null || currentText.toString().isEmpty() || currentText.toString().equals("Content not available.")) {
                    tvPassageDisplay.setVisibility(View.GONE);
                } else {
                    tvPassageDisplay.setVisibility(View.VISIBLE);
                }
            } else if (tvPassageDisplay != null && !visible){
                tvPassageDisplay.setVisibility(View.INVISIBLE); // Ensure it's hidden if visibility=false
            }

        });
    }

    // --- Lifecycle Methods (Forwarding to Controller if needed, or just logging) ---
    @Override protected void onPause() { super.onPause(); Log.d(TAG,"onPause."); }
    @Override protected void onResume() { super.onResume(); Log.d(TAG,"onResume."); }
    @Override protected void onStop() { super.onStop(); Log.d(TAG,"onStop."); }
    @Override protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG,"onDestroy.");
        if (controller != null) {
            controller.onDestroy(); // Allow controller to clean up if needed
        }
    }
    @Override public void onBackPressed() { super.onBackPressed(); Log.d(TAG,"onBackPressed."); }

}
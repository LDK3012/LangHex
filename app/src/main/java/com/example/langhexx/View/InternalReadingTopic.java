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
import com.example.langhexx.Controller.ReadingQuestionListAdapter;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.ReadingQuestion;
import com.example.langhexx.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InternalReadingTopic extends AppCompatActivity implements ReadingController.ViewInterface {

    private static final String TAG = "InternalReadTopicVIEW";

    private ImageView imgClose, imgHome;

    // --- UI Elements ---
    private ListView lvQuestions;
    private Button btnSubmit;
    private TextView tvExerciseDisplayTitle;
    private TextView tvPassageDisplay;
    private TextView instructionText;
    // --- Adapters and Local Data List (Managed by View for UI binding) ---
    private List<ReadingQuestion> questionsList;
    private ReadingQuestionListAdapter questionListAdapter;

    // --- Controller ---
    private ReadingController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_reading_topic);
        Log.d(TAG, "onCreate");

        addControls();
        setupListView();

        // Initialize Controller, passing the View (this) and Intent
        controller = new ReadingController(this, getIntent());

        // Controller will now handle loading data and initial setup logic
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
                controller.onSubmitButtonClicked();
            });
        }

        if (imgClose != null) {
            imgClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finish();
                }
            });
        }

        if (imgHome != null) {
            imgHome.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(InternalReadingTopic.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }
            });
        }
    }

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
                tvPassageDisplay.setText(text != null && !text.isEmpty() ? text : "Content not available.");
            }
        });
    }

    @Override
    public void updateAdapterData(List<ReadingQuestion> newQuestions) {
        Log.d(TAG, "updateAdapterData: Received " + (newQuestions != null ? newQuestions.size() : 0) + " questions.");
        runOnUiThread(() -> {
            if (questionListAdapter != null && newQuestions != null) {
                this.questionsList.clear(); // Xóa dữ liệu cũ trong list của Activity
                this.questionsList.addAll(newQuestions); // Thêm dữ liệu mới
                questionListAdapter.updateData(this.questionsList); // Cập nhật adapter với list mới
                Log.d(TAG, "Adapter notified with " + this.questionsList.size() + " questions.");
            } else {
                Log.w(TAG, "Adapter is null or newQuestions is null, cannot update UI.");
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

    // SỬA PHƯƠNG THỨC NÀY ĐỂ KHỚP VỚI INTERFACE
    @Override
    public void navigateToNextExercise(String levelName, String topicId, String nextExerciseId, String topicDisplayTitle, String nextExerciseDisplayTitle) {
        Log.i(TAG, "navigateToNextExercise: Level=" + levelName + ", TopicID=" + topicId +
                ", NextExerciseID=" + nextExerciseId + ", TopicTitle=" + topicDisplayTitle + ", NextExerciseTitle=" + nextExerciseDisplayTitle);
        Intent nextIntent = new Intent(InternalReadingTopic.this, InternalReadingTopic.class);
        nextIntent.putExtra("LEVEL_NAME", levelName);
        nextIntent.putExtra("TOPIC_ID", topicId); // Truyền topicId
        nextIntent.putExtra("EXERCISE_ID", nextExerciseId); // Truyền exerciseId của bài tiếp theo
        nextIntent.putExtra("TOPIC_TITLE", topicDisplayTitle); // Truyền topic display title
        nextIntent.putExtra("EXERCISE_TITLE", nextExerciseDisplayTitle); // Truyền exercise display title của bài tiếp theo
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
        if (lvQuestions != null) {
            lvQuestions.post(() -> {
                if (index >= 0 && index < questionListAdapter.getCount()) {
                    lvQuestions.smoothScrollToPosition(index);
                } else {
                    Log.w(TAG, "Invalid index for scrollToQuestion: " + index);
                }
            });
        }
    }

    @Override
    public void setUIElementsVisibility(boolean visible) {
        Log.d(TAG, "setUIElementsVisibility: " + visible);
        runOnUiThread(() -> {
            int visibility = visible ? View.VISIBLE : View.INVISIBLE;
            if (tvExerciseDisplayTitle != null) tvExerciseDisplayTitle.setVisibility(View.VISIBLE);

            if (tvPassageDisplay != null) {
                CharSequence currentPassageText = tvPassageDisplay.getText();
                boolean passageHasContent = currentPassageText != null && !currentPassageText.toString().isEmpty() && !currentPassageText.toString().equals("Content not available.");
                tvPassageDisplay.setVisibility(visible && passageHasContent ? View.VISIBLE : View.GONE);
            }

            boolean questionsAvailable = questionListAdapter != null && questionListAdapter.getCount() > 0;
            if (lvQuestions != null) lvQuestions.setVisibility(visible && questionsAvailable ? View.VISIBLE : View.GONE);
            if (instructionText != null) instructionText.setVisibility(visible && questionsAvailable ? View.VISIBLE : View.GONE);
            if (btnSubmit != null) btnSubmit.setVisibility(visible && questionsAvailable ? View.VISIBLE : View.GONE);
            if(visible && !questionsAvailable && (tvPassageDisplay == null || tvPassageDisplay.getVisibility() == View.GONE)) {
                Log.d(TAG, "No questions and no passage to display.");
                if (btnSubmit != null && controller != null && questionsList.isEmpty()) {
                        //
                }
            }
        });
    }


    @Override protected void onPause() { super.onPause(); Log.d(TAG,"onPause."); }
    @Override protected void onResume() { super.onResume(); Log.d(TAG,"onResume."); }
    @Override protected void onStop() { super.onStop(); Log.d(TAG,"onStop."); }
    @Override protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG,"onDestroy.");
        if (controller != null) {
            controller.onDestroy();
        }
    }
    @Override public void onBackPressed() {
        super.onBackPressed();
        Log.d(TAG,"onBackPressed.");
    }
}
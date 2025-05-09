package com.example.langhexx.View;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color; // Thêm import Color
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler; // Thêm Handler
import android.os.Looper;  // Thêm Looper
import android.text.Editable;
import android.text.Spannable; // Thêm Spannable
import android.text.SpannableStringBuilder; // Thêm SpannableStringBuilder
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan; // Thêm ForegroundColorSpan
import android.text.style.UnderlineSpan;     // Thêm UnderlineSpan
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.langhexx.Controller.WritingController;
import com.example.langhexx.Model.ErrorDetail; // Thêm import ErrorDetail
import com.example.langhexx.R;

import java.util.ArrayList; // Thêm ArrayList
import java.util.List;      // Thêm List

public class InternalWritingTopic extends AppCompatActivity implements WritingController.ViewInterface {

    private static final String TAG = "InternalWritingTopicV";

    // --- UI Elements ---
    private ImageView backButton;
    private TextView titleTextView;
    private TextView questionTextView;
    private EditText answerEditText;
    private Button submitButton;
    private TextView feedbackTriggerButton;

    // --- Feedback Panel UI Elements ---
    private View includedFeedbackPanel;
    private ImageView iconTaskResponse, iconCoherenceCohesion, iconGrammarVocabulary, iconLength;
    private TextView taskResponseTitleTextView, coherenceTitleTextView, grammarTitleTextView, lengthTitleTextView;
    private TextView textFeedbackTaskResponse, textFeedbackCoherenceCohesion, textFeedbackGrammarVocabulary, textFeedbackLength;
    private Button btnSeeRevisedVersion;

    private AlertDialog loadingDialog;
    private ScrollView mainScrollView;

    private WritingController controller;

    // Lưu trữ các span lỗi hiện tại để có thể xóa
    private List<Object> currentErrorSpans = new ArrayList<>();

    // Debounce cho TextWatcher
    private Handler textChangeHandler = new Handler(Looper.getMainLooper());
    private Runnable textChangeRunnable;
    private final long TEXT_CHANGE_DEBOUNCE_MS = 1000; // Thời gian chờ trước khi gọi controller (1 giây)


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_writing_topic);
        Log.d(TAG, "onCreate");
        addControls();
        controller = new WritingController(this, getIntent());
        controller.initialize();
        addEvents();
    }

    private void addControls() {
        Log.d(TAG, "addControls");
        mainScrollView = findViewById(R.id.main_scroll_view);
        backButton = findViewById(R.id.btnBack);
        titleTextView = findViewById(R.id.tvScreenTitle);
        questionTextView = findViewById(R.id.question_textview);
        answerEditText = findViewById(R.id.answer_edittext);
        submitButton = findViewById(R.id.submit_button);
        feedbackTriggerButton = findViewById(R.id.feedback_trigger_button);

        includedFeedbackPanel = findViewById(R.id.included_feedback_panel_internal);
        // ... (gán các view con của includedFeedbackPanel như cũ) ...
        iconTaskResponse = includedFeedbackPanel.findViewById(R.id.icon_task_response);
        iconCoherenceCohesion = includedFeedbackPanel.findViewById(R.id.icon_coherence_cohesion);
        iconGrammarVocabulary = includedFeedbackPanel.findViewById(R.id.icon_grammar_vocabulary);
        iconLength = includedFeedbackPanel.findViewById(R.id.icon_length);

        taskResponseTitleTextView = includedFeedbackPanel.findViewById(R.id.taskResponseTxt);
        coherenceTitleTextView = includedFeedbackPanel.findViewById(R.id.coherenceTxt);
        grammarTitleTextView = includedFeedbackPanel.findViewById(R.id.grammarTxt);
        lengthTitleTextView = includedFeedbackPanel.findViewById(R.id.lengthTxt);

        textFeedbackTaskResponse = includedFeedbackPanel.findViewById(R.id.text_feedback_task_response);
        textFeedbackCoherenceCohesion = includedFeedbackPanel.findViewById(R.id.text_feedback_coherence_cohesion);
        textFeedbackGrammarVocabulary = includedFeedbackPanel.findViewById(R.id.text_feedback_grammar_vocabulary);
        textFeedbackLength = includedFeedbackPanel.findViewById(R.id.text_feedback_length);
        btnSeeRevisedVersion = includedFeedbackPanel.findViewById(R.id.btn_see_revised_version);


        TextView reviewTextView = findViewById(R.id.feedback_trigger_button);
        if (reviewTextView != null) { // Đảm bảo reviewTextView không null
            reviewTextView.setPaintFlags(reviewTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        }


        if (includedFeedbackPanel != null) includedFeedbackPanel.setVisibility(View.GONE);
        if (feedbackTriggerButton != null) feedbackTriggerButton.setVisibility(View.GONE);
        if (btnSeeRevisedVersion != null) btnSeeRevisedVersion.setVisibility(View.GONE);
    }

    private void addEvents() {
        Log.d(TAG, "addEvents");
        if (backButton != null) backButton.setOnClickListener(v -> onBackPressed());

        if (submitButton != null) submitButton.setOnClickListener(v -> {
            String userAnswer = answerEditText.getText().toString().trim();
            // Hủy bỏ debounce khi submit
            if (textChangeRunnable != null) {
                textChangeHandler.removeCallbacks(textChangeRunnable);
            }
            controller.onSubmitButtonClicked(userAnswer);
        });

        if (feedbackTriggerButton != null) feedbackTriggerButton.setOnClickListener(v -> {
            if (includedFeedbackPanel != null) {
                if (includedFeedbackPanel.getVisibility() == View.VISIBLE) {
                    // includedFeedbackPanel.setVisibility(View.GONE);
                } else {
                    includedFeedbackPanel.setVisibility(View.VISIBLE);
                    focusOnFeedbackPanel();
                }
            }
        });

        if (btnSeeRevisedVersion != null) btnSeeRevisedVersion.setOnClickListener(v -> {
            Toast.makeText(this, "See revised version - To be implemented", Toast.LENGTH_SHORT).show();
        });

        if (answerEditText != null) answerEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Hủy bỏ callback cũ mỗi khi text thay đổi
                if (textChangeRunnable != null) {
                    textChangeHandler.removeCallbacks(textChangeRunnable);
                }
            }
            @Override public void afterTextChanged(Editable s) {
                final String currentText = s.toString();
                // Đặt callback mới
                textChangeRunnable = () -> {
                    if (controller != null && answerEditText.isEnabled()) {
                        // Gọi onAnswerTextChanged của controller, nơi sẽ xử lý cả logic nút submit và inline analysis
                        controller.onAnswerTextChanged(currentText);
                    }
                };
                textChangeHandler.postDelayed(textChangeRunnable, TEXT_CHANGE_DEBOUNCE_MS);
            }
        });
    }

    // ... (dpToPx, displayStructuredAIFeedback, updateFeedbackItemUI, ... giữ nguyên) ...
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void displayStructuredAIFeedback(
            String taskResponseFeedback, int taskResponseIconType,
            String coherenceCohesionFeedback, int coherenceCohesionIconType,
            String grammarVocabularyFeedback, int grammarVocabularyIconType,
            String lengthFeedback, int lengthIconType) {
        Log.d(TAG, "displayStructuredAIFeedback called");
        runOnUiThread(() -> {
            if (includedFeedbackPanel == null) return;

            boolean allSuccess = taskResponseIconType == 1 && coherenceCohesionIconType == 1 && grammarVocabularyIconType == 1 && lengthIconType == 1;
            int panelBorderColor = ContextCompat.getColor(this, allSuccess ? R.color.feedback_panel_border_success : R.color.feedback_panel_border_warning);
            Drawable background = includedFeedbackPanel.getBackground();
            if (background instanceof GradientDrawable) {
                ((GradientDrawable) background).setStroke(dpToPx(2), panelBorderColor);
            } else {
                GradientDrawable mutatedDrawable = (GradientDrawable) ContextCompat.getDrawable(this, R.drawable.feedback_panel_background).mutate();
                if (mutatedDrawable != null) {
                    mutatedDrawable.setStroke(dpToPx(2), panelBorderColor);
                    includedFeedbackPanel.setBackground(mutatedDrawable);
                }
            }

            updateFeedbackItemUI(taskResponseTitleTextView, textFeedbackTaskResponse, iconTaskResponse, taskResponseFeedback, taskResponseIconType);
            updateFeedbackItemUI(coherenceTitleTextView, textFeedbackCoherenceCohesion, iconCoherenceCohesion, coherenceCohesionFeedback, coherenceCohesionIconType);
            updateFeedbackItemUI(grammarTitleTextView, textFeedbackGrammarVocabulary, iconGrammarVocabulary, grammarVocabularyFeedback, grammarVocabularyIconType);
            updateFeedbackItemUI(lengthTitleTextView, textFeedbackLength, iconLength, lengthFeedback, lengthIconType);
        });
    }

    private void updateFeedbackItemUI(TextView titleView, TextView detailView, ImageView iconView, String detailText, int iconType) {
        int titleColorRes = iconType == 1 ? R.color.feedback_text_item_success : R.color.feedback_text_item_warning;
        int iconRes = iconType == 1 ? R.drawable.success : R.drawable.warning_icon;
        int iconTintRes = iconType == 1 ? R.color.icon_success_tint : R.color.icon_warning_tint;

        if (titleView != null) {
            titleView.setTextColor(ContextCompat.getColor(this, titleColorRes));
        }
        if (detailView != null) {
            detailView.setText(detailText);
        }
        if (iconView != null) {
            iconView.setImageResource(iconRes);
            iconView.setColorFilter(ContextCompat.getColor(this, iconTintRes));
        }
    }

    @Override
    public void displayExerciseTitle(String title) {
        runOnUiThread(() -> { if (titleTextView != null) titleTextView.setText(title); });
    }

    @Override
    public void displayQuestionPrompt(String prompt) {
        runOnUiThread(() -> { if (questionTextView != null) questionTextView.setText(prompt != null ? prompt : "Prompt not available."); });
    }

    @Override
    public void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(InternalWritingTopic.this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void showFailToast(String message) {
        runOnUiThread(() -> Toast.makeText(InternalWritingTopic.this, "FAIL: " + message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void showConfirmationDialog(String title, String message, Runnable onConfirm) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title).setMessage(message)
                .setPositiveButton("Submit", (dialog, which) -> onConfirm.run())
                .setNegativeButton("Cancel", null)
                .setCancelable(false).show());
    }

    @Override
    public void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle) {
        Intent nextIntent = new Intent(InternalWritingTopic.this, InternalWritingTopic.class);
        nextIntent.putExtra("LEVEL_NAME", levelName);
        nextIntent.putExtra("TOPIC_TITLE", topicTitle);
        nextIntent.putExtra("EXERCISE_TITLE", nextExerciseTitle);
        startActivity(nextIntent);
        finish();
    }

    @Override
    public void finishActivity() {
        runOnUiThread(this::finish);
    }

    @Override
    public void setSubmitButtonState(String text, boolean enabled) {
        runOnUiThread(() -> {
            if (submitButton != null) {
                submitButton.setText(text);
                submitButton.setEnabled(enabled);
                submitButton.setAlpha(enabled ? 1.0f : 0.5f);
            }
        });
    }

    @Override
    public void clearAnswerInput() {
        runOnUiThread(() -> { if (answerEditText != null) answerEditText.setText(""); });
    }

    @Override
    public void showLoading(String message) {
        runOnUiThread(() -> {
            if (loadingDialog == null || !loadingDialog.isShowing()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this).setMessage(message).setCancelable(false);
                loadingDialog = builder.create();
                loadingDialog.show();
            } else {
                loadingDialog.setMessage(message);
            }
        });
    }

    @Override
    public void hideLoading() {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
        });
    }

    @Override
    public void setFeedbackPanelVisibility(boolean visible) {
        runOnUiThread(() -> {
            if (includedFeedbackPanel != null) {
                Log.d(TAG, "Setting feedback panel visibility to: " + visible);
                includedFeedbackPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void focusOnFeedbackPanel() {
        runOnUiThread(() -> {
            if (includedFeedbackPanel != null && includedFeedbackPanel.getVisibility() == View.VISIBLE) {
                includedFeedbackPanel.post(() -> {
                    if (mainScrollView != null) {
                        Rect panelRect = new Rect();
                        includedFeedbackPanel.getHitRect(panelRect);
                        mainScrollView.smoothScrollTo(0, includedFeedbackPanel.getTop());
                        Log.d(TAG, "Scrolled to feedback panel at Y: " + includedFeedbackPanel.getTop());
                    } else {
                        Rect rect = new Rect(0, 0, includedFeedbackPanel.getWidth(), includedFeedbackPanel.getHeight());
                        includedFeedbackPanel.requestRectangleOnScreen(rect, false);
                        Log.d(TAG, "Requested rectangle on screen for feedback panel.");
                    }
                });
            }
        });
    }

    @Override
    public void setFeedbackTriggerVisibility(boolean visible) {
        runOnUiThread(() -> {
            if (feedbackTriggerButton != null) {
                Log.d(TAG, "Setting feedback trigger visibility to: " + visible);
                feedbackTriggerButton.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        });
    }


    @Override
    public void setAnswerInputVisibility(boolean visible) {
        runOnUiThread(() -> { if (answerEditText != null) answerEditText.setVisibility(View.VISIBLE); });
    }

    @Override
    public void setAnswerEditTextEnabled(boolean enabled) {
        runOnUiThread(() -> {
            if (answerEditText != null) {
                answerEditText.setEnabled(enabled);
                if (!enabled) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null && getCurrentFocus() == answerEditText) {
                        imm.hideSoftInputFromWindow(answerEditText.getWindowToken(), 0);
                    }
                }
                Log.d(TAG, "AnswerEditText enabled: " + enabled);
            }
        });
    }

    @Override
    public void requestFocusOnAnswerInput() {
        runOnUiThread(() -> {
            if (answerEditText != null && answerEditText.isEnabled()) {
                answerEditText.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(answerEditText, InputMethodManager.SHOW_IMPLICIT);
                }
                Log.d(TAG, "Requested focus and keyboard for AnswerEditText");
            }
        });
    }


    @Override
    public void setUIElementsVisibility(boolean visible) {
        runOnUiThread(() -> {
            int visibilitySetting = visible ? View.VISIBLE : View.INVISIBLE;
            if (titleTextView != null) titleTextView.setVisibility(View.VISIBLE);
            if (questionTextView != null) questionTextView.setVisibility(visibilitySetting);
            if (submitButton != null) submitButton.setVisibility(visibilitySetting);
            if (answerEditText != null) answerEditText.setVisibility(View.VISIBLE);

            if (!visible) {
                if (includedFeedbackPanel != null) includedFeedbackPanel.setVisibility(View.GONE);
                if (feedbackTriggerButton != null) feedbackTriggerButton.setVisibility(View.GONE);
                if (btnSeeRevisedVersion != null) btnSeeRevisedVersion.setVisibility(View.GONE);
            }

            if (questionTextView != null && visible) {
                CharSequence currentText = questionTextView.getText();
                boolean isContentNotAvailable = currentText == null ||
                        currentText.toString().isEmpty() ||
                        currentText.toString().equals("Prompt not available.") ||
                        currentText.toString().equals("Content not available for this exercise.");

                if (isContentNotAvailable) {
                    questionTextView.setText("Content not available for this exercise.");
                    if (submitButton != null) {
                        submitButton.setEnabled(false);
                        submitButton.setAlpha(0.5f);
                    }
                    if(answerEditText != null) {
                        answerEditText.setEnabled(false);
                    }
                }
            }
        });
    }

    @Override
    public void setSeeRevisedVersionButtonVisibility(boolean visible) {
        runOnUiThread(() -> {
            if (btnSeeRevisedVersion != null) {
                Log.d(TAG, "Setting SeeRevisedVersionButton visibility to: " + visible);
                btnSeeRevisedVersion.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        });
    }

    // --- Triển khai các phương thức mới cho inline error highlighting ---
    @Override
    public void clearInlineErrorHighlighting() {
        runOnUiThread(() -> {
            if (answerEditText == null) return;
            Editable editable = answerEditText.getText();
            if (editable == null) return;

            // Xóa các span đã thêm trước đó
            for (Object span : currentErrorSpans) {
                editable.removeSpan(span);
            }
            currentErrorSpans.clear();
            Log.d(TAG, "Cleared inline error highlights.");
        });
    }

    @Override
    public void applyInlineErrorHighlighting(List<ErrorDetail> errors) {
        runOnUiThread(() -> {
            if (answerEditText == null || errors == null) return;
            Editable editable = answerEditText.getText();
            if (editable == null) return;

            // Xóa lỗi cũ trước khi áp dụng lỗi mới (đã được gọi từ controller, nhưng để chắc chắn)
            clearInlineErrorHighlighting();

            Log.d(TAG, "Applying " + errors.size() + " inline error highlights.");
            for (ErrorDetail error : errors) {
                if (error.startIndex < error.endIndex && error.endIndex <= editable.length()) {
                    ForegroundColorSpan colorSpan = new ForegroundColorSpan(Color.RED);
                    UnderlineSpan underlineSpan = new UnderlineSpan();

                    editable.setSpan(colorSpan, error.startIndex, error.endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    editable.setSpan(underlineSpan, error.startIndex, error.endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    currentErrorSpans.add(colorSpan);
                    currentErrorSpans.add(underlineSpan);
                    Log.d(TAG, "Applied highlight: " + error.errorText + " from " + error.startIndex + " to " + error.endIndex);

                } else {
                    Log.w(TAG, "Invalid error indices: " + error.errorText +
                            " [" + error.startIndex + "," + error.endIndex + "] for text length " + editable.length());
                }
            }
            // Quan trọng: Không gọi answerEditText.setText(editable) ở đây trừ khi
            // bạn muốn reset toàn bộ trạng thái của EditText (ví dụ: cursor, selection).
            // Việc thay đổi trực tiếp `editable` thường là đủ để UI cập nhật.
        });
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (textChangeRunnable != null) { // Hủy debounce khi pause
            textChangeHandler.removeCallbacks(textChangeRunnable);
        }
        if (controller != null) controller.onPause();
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (controller == null) {
            Log.w(TAG, "Controller was null in onResume, re-initializing.");
            controller = new WritingController(this, getIntent());
            controller.initialize();
        } else {
            controller.onResume();
        }
        if (mainScrollView != null) {
            mainScrollView.post(() -> {
                mainScrollView.fullScroll(View.FOCUS_UP);
                Log.d(TAG, "ScrollView forced to top in onResume");
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (textChangeRunnable != null) { // Hủy debounce khi destroy
            textChangeHandler.removeCallbacks(textChangeRunnable);
        }
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
        loadingDialog = null;
        if (controller != null) {
            controller.onDestroy();
            controller = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (controller != null && controller.handleBackPressed()) {
            return;
        }

        boolean hasUnsavedText = answerEditText != null && answerEditText.isEnabled() && !answerEditText.getText().toString().trim().isEmpty();
        boolean isInFeedbackState = controller != null && controller.getCurrentButtonState() == WritingController.STATE_RETRY_WRITING;
        boolean isAllCriteriaSuccess = controller != null && controller.areAllCriteriaSuccess();
        boolean isEditingAfterFeedback = controller != null && controller.isUserEditingAfterFeedback();

        String message = "";
        boolean shouldShowDialog = false;

        if (isInFeedbackState) {
            shouldShowDialog = true;
            if (isAllCriteriaSuccess) {
                message = "Are you sure you want to go back?";
            } else {
                message = "Your feedback progress will be dismissed. Are you sure you want to go back?";
            }
        } else if (isEditingAfterFeedback && hasUnsavedText) {
            String currentText = answerEditText.getText().toString().trim();
            String previousText = controller.getSubmittedTextForCurrentFeedback().trim();
            if (!currentText.equals(previousText)) {
                shouldShowDialog = true;
                message = "Your changes will be lost. Are you sure you want to exit?";
            } else {
                super.onBackPressed();
                return;
            }
        }
        else if (hasUnsavedText) {
            shouldShowDialog = true;
            message = "Your current writing will be lost. Are you sure you want to exit?";
        }

        if (shouldShowDialog) {
            new AlertDialog.Builder(this)
                    .setTitle("Exit Exercise")
                    .setMessage(message)
                    .setPositiveButton("Exit", (dialog, which) -> super.onBackPressed())
                    .setNegativeButton("Stay", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}
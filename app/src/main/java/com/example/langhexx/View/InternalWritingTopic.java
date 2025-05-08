//package com.example.langhexx.View; // Hoặc package đúng của bạn
//
//import android.content.Intent;
//import android.graphics.Rect; // MỚI
//import android.os.Bundle;
//import android.text.Editable; // MỚI
//import android.text.TextWatcher; // MỚI
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.EditText;
//import android.widget.ImageView;
//import android.widget.ScrollView; // MỚI (hoặc androidx.core.widget.NestedScrollView)
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.app.AlertDialog;
//import androidx.core.content.ContextCompat;
//
//import com.example.langhexx.Controller.WritingController;
//import com.example.langhexx.R;
//
//public class InternalWritingTopic extends AppCompatActivity implements WritingController.ViewInterface {
//
//    private static final String TAG = "InternalWritingTopicV";
//
//    // --- UI Elements ---
//    private ImageView backButton;
//    private TextView titleTextView;
//    private TextView questionTextView;
//    private EditText answerEditText;
//    private Button submitButton;
//    private TextView feedbackTriggerButton;
//
//    // --- Feedback Panel UI Elements ---
//    private View includedFeedbackPanel;
//    private ImageView iconTaskResponse, iconCoherenceCohesion, iconGrammarVocabulary, iconLength;
//    private TextView textFeedbackTaskResponse, textFeedbackCoherenceCohesion, textFeedbackGrammarVocabulary, textFeedbackLength;
//    private Button btnSeeRevisedVersion;
//
//    private AlertDialog loadingDialog;
//    private ScrollView mainScrollView; // MỚI: Để cuộn
//
//    // --- Controller ---
//    private WritingController controller;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_internal_writing_topic);
//        Log.d(TAG, "onCreate");
//        addControls(); // Gọi trước controller init
//        // Khởi tạo controller sau khi addControls để các view đã sẵn sàng
//        controller = new WritingController(this, getIntent());
//        controller.initialize();
//        addEvents();
//    }
//
//    private void addControls() {
//        Log.d(TAG, "addControls");
//        // Giả sử bạn có một ScrollView trong XML với id là main_scroll_view
//        // Nếu không, bạn cần lấy tham chiếu đến ScrollView bao quanh nội dung
//        mainScrollView = findViewById(R.id.main_scroll_view); // THAY R.id.main_scroll_view bằng ID thực tế của ScrollView
//
//        backButton = findViewById(R.id.back_button);
//        titleTextView = findViewById(R.id.title_textview);
//        questionTextView = findViewById(R.id.question_textview);
//        answerEditText = findViewById(R.id.answer_edittext);
//        submitButton = findViewById(R.id.submit_button);
//        feedbackTriggerButton = findViewById(R.id.feedback_trigger_button);
//
//        includedFeedbackPanel = findViewById(R.id.included_feedback_panel_internal);
//
//        iconTaskResponse = includedFeedbackPanel.findViewById(R.id.icon_task_response);
//        textFeedbackTaskResponse = includedFeedbackPanel.findViewById(R.id.text_feedback_task_response);
//        iconCoherenceCohesion = includedFeedbackPanel.findViewById(R.id.icon_coherence_cohesion);
//        textFeedbackCoherenceCohesion = includedFeedbackPanel.findViewById(R.id.text_feedback_coherence_cohesion);
//        iconGrammarVocabulary = includedFeedbackPanel.findViewById(R.id.icon_grammar_vocabulary);
//        textFeedbackGrammarVocabulary = includedFeedbackPanel.findViewById(R.id.text_feedback_grammar_vocabulary);
//        iconLength = includedFeedbackPanel.findViewById(R.id.icon_length);
//        textFeedbackLength = includedFeedbackPanel.findViewById(R.id.text_feedback_length);
//        btnSeeRevisedVersion = includedFeedbackPanel.findViewById(R.id.btn_see_revised_version);
//
//        if (includedFeedbackPanel != null) {
//            includedFeedbackPanel.setVisibility(View.GONE);
//        }
//        if (feedbackTriggerButton != null) {
//            feedbackTriggerButton.setVisibility(View.GONE);
//        }
//    }
//
//    private void addEvents() {
//        Log.d(TAG, "addEvents");
//        if (backButton != null) {
//            backButton.setOnClickListener(v -> onBackPressed());
//        }
//
//        if (submitButton != null) {
//            submitButton.setOnClickListener(v -> {
//                String userAnswer = answerEditText.getText().toString().trim();
//                controller.onSubmitButtonClicked(userAnswer);
//            });
//        }
//
//        if (feedbackTriggerButton != null) {
//            feedbackTriggerButton.setOnClickListener(v -> {
//                if (includedFeedbackPanel != null) {
//                    if (includedFeedbackPanel.getVisibility() == View.VISIBLE) {
//                        // includedFeedbackPanel.setVisibility(View.GONE);
//                    } else {
//                        includedFeedbackPanel.setVisibility(View.VISIBLE);
//                        focusOnFeedbackPanel(); // Cuộn tới khi trigger được nhấn (nếu bạn giữ lại trigger này)
//                    }
//                }
//            });
//        }
//
//        if (btnSeeRevisedVersion != null) {
//            btnSeeRevisedVersion.setOnClickListener(v -> {
//                Toast.makeText(this, "See revised version - To be implemented", Toast.LENGTH_SHORT).show();
//            });
//        }
//
//        // MỚI: Lắng nghe sự kiện thay đổi text trong EditText
//        if (answerEditText != null) {
//            answerEditText.addTextChangedListener(new TextWatcher() {
//                @Override
//                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//                    // No action needed
//                }
//
//                @Override
//                public void onTextChanged(CharSequence s, int start, int before, int count) {
//                    // No action needed directly, or you could call controller here if preferred
//                }
//
//                @Override
//                public void afterTextChanged(Editable s) {
//                    if (controller != null) {
//                        // Thông báo cho controller rằng text đã thay đổi
//                        // Controller sẽ quyết định có cần đổi trạng thái nút hay không
//                        controller.onAnswerTextChanged();
//                    }
//                }
//            });
//        }
//    }
//
//    @Override
//    public void displayExerciseTitle(String title) {
//        Log.d(TAG, "displayExerciseTitle: " + title);
//        runOnUiThread(() -> {
//            if (titleTextView != null) titleTextView.setText(title);
//        });
//    }
//
//    @Override
//    public void displayQuestionPrompt(String prompt) {
//        Log.d(TAG, "displayQuestionPrompt: " + (prompt != null ? prompt.substring(0, Math.min(prompt.length(), 50))+"..." : "empty"));
//        runOnUiThread(() -> {
//            if (questionTextView != null) {
//                questionTextView.setText(prompt != null ? prompt : "Writing prompt not available.");
//            }
//        });
//    }
//
//    @Override
//    public void showToast(String message) {
//        Log.d(TAG, "showToast: " + message);
//        runOnUiThread(() -> Toast.makeText(InternalWritingTopic.this, message, Toast.LENGTH_LONG).show());
//    }
//
//    @Override
//    public void showFailToast(String message) {
//        Log.d(TAG, "showFailToast: " + message);
//        runOnUiThread(() -> Toast.makeText(InternalWritingTopic.this, "FAIL: " + message, Toast.LENGTH_LONG).show());
//    }
//
//    @Override
//    public void showConfirmationDialog(String title, String message, Runnable onConfirm) {
//        Log.d(TAG, "showConfirmationDialog for Writing");
//        runOnUiThread(() -> new AlertDialog.Builder(this)
//                .setTitle(title)
//                .setMessage(message)
//                .setPositiveButton("Submit", (dialog, which) -> onConfirm.run())
//                .setNegativeButton("Cancel", (dialog, which) -> Log.d(TAG, "Submission cancelled."))
//                .setCancelable(false)
//                .show());
//    }
//
//    @Override
//    public void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle) {
//        Log.i(TAG, "navigateToNextExercise: " + nextExerciseTitle);
//        Intent nextIntent = new Intent(InternalWritingTopic.this, InternalWritingTopic.class);
//        nextIntent.putExtra("LEVEL_NAME", levelName);
//        nextIntent.putExtra("TOPIC_TITLE", topicTitle);
//        nextIntent.putExtra("EXERCISE_TITLE", nextExerciseTitle);
//        startActivity(nextIntent);
//        finish();
//    }
//
//    @Override
//    public void finishActivity() {
//        Log.i(TAG, "finishActivity");
//        finish();
//    }
//
//    @Override
//    public void setSubmitButtonState(String text, boolean enabled) {
//        Log.d(TAG, "setSubmitButtonState: Text=" + text + ", Enabled=" + enabled);
//        runOnUiThread(() -> {
//            if (submitButton != null) {
//                submitButton.setText(text);
//                submitButton.setEnabled(enabled);
//                submitButton.setAlpha(enabled ? 1.0f : 0.5f);
//            }
//        });
//    }
//
//    @Override
//    public void clearAnswerInput() {
//        runOnUiThread(() -> {
//            if (answerEditText != null) {
//                answerEditText.setText("");
//            }
//        });
//    }
//
//    @Override
//    public void showLoading(String message) {
//        runOnUiThread(() -> {
//            if (loadingDialog == null || !loadingDialog.isShowing()) {
//                AlertDialog.Builder builder = new AlertDialog.Builder(this)
//                        .setMessage(message)
//                        .setCancelable(false);
//                loadingDialog = builder.create();
//                loadingDialog.show();
//            } else {
//                loadingDialog.setMessage(message);
//            }
//        });
//    }
//
//    @Override
//    public void hideLoading() {
//        runOnUiThread(() -> {
//            if (loadingDialog != null && loadingDialog.isShowing()) {
//                loadingDialog.dismiss();
//            }
//        });
//    }
//
//    @Override
//    public void displayStructuredAIFeedback(
//            String taskResponseFeedback, int taskResponseIconType,
//            String coherenceCohesionFeedback, int coherenceCohesionIconType,
//            String grammarVocabularyFeedback, int grammarVocabularyIconType,
//            String lengthFeedback, int lengthIconType) {
//        Log.d(TAG, "displayStructuredAIFeedback called - Populating feedback data.");
//        runOnUiThread(() -> {
//            if (textFeedbackTaskResponse != null) textFeedbackTaskResponse.setText(taskResponseFeedback);
//            if (iconTaskResponse != null) {
//                iconTaskResponse.setImageResource(taskResponseIconType == 1 ? R.drawable.success : R.drawable.warning_icon);
//                iconTaskResponse.setColorFilter(ContextCompat.getColor(this, taskResponseIconType == 1 ? R.color.icon_success_tint : R.color.icon_warning_tint));
//            }
//
//            if (textFeedbackCoherenceCohesion != null) textFeedbackCoherenceCohesion.setText(coherenceCohesionFeedback);
//            if (iconCoherenceCohesion != null) {
//                iconCoherenceCohesion.setImageResource(coherenceCohesionIconType == 1 ? R.drawable.success : R.drawable.warning_icon);
//                iconCoherenceCohesion.setColorFilter(ContextCompat.getColor(this, coherenceCohesionIconType == 1 ? R.color.icon_success_tint : R.color.icon_warning_tint));
//            }
//
//            if (textFeedbackGrammarVocabulary != null) textFeedbackGrammarVocabulary.setText(grammarVocabularyFeedback);
//            if (iconGrammarVocabulary != null) {
//                iconGrammarVocabulary.setImageResource(grammarVocabularyIconType == 1 ? R.drawable.success : R.drawable.warning_icon);
//                iconGrammarVocabulary.setColorFilter(ContextCompat.getColor(this, grammarVocabularyIconType == 1 ? R.color.icon_success_tint : R.color.icon_warning_tint));
//            }
//
//            if (textFeedbackLength != null) textFeedbackLength.setText(lengthFeedback);
//            if (iconLength != null) {
//                iconLength.setImageResource(lengthIconType == 1 ? R.drawable.success : R.drawable.warning_icon);
//                iconLength.setColorFilter(ContextCompat.getColor(this, lengthIconType == 1 ? R.color.icon_success_tint : R.color.icon_warning_tint));
//            }
//        });
//    }
//
//    @Override
//    public void setFeedbackPanelVisibility(boolean visible) {
//        Log.d(TAG, "setFeedbackPanelVisibility: " + visible);
//        runOnUiThread(() -> {
//            if (includedFeedbackPanel != null) {
//                includedFeedbackPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
//                // Nếu panel được hiển thị, chúng ta sẽ gọi focusOnFeedbackPanel từ controller
//                // sau khi dữ liệu feedback đã được đổ vào, không phải ở đây ngay.
//            }
//        });
//    }
//
//    // MỚI: Triển khai phương thức cuộn/focus vào panel feedback
//    @Override
//    public void focusOnFeedbackPanel() {
//        runOnUiThread(() -> {
//            if (includedFeedbackPanel != null && includedFeedbackPanel.getVisibility() == View.VISIBLE) {
//                // Sử dụng post để đảm bảo view đã được layout xong trước khi cuộn
//                includedFeedbackPanel.post(() -> {
//                    if (mainScrollView != null) {
//                        // Cuộn ScrollView đến vị trí của panel feedback
//                        // Tính toán vị trí top của panel feedback so với ScrollView
//                        Rect panelRect = new Rect();
//                        includedFeedbackPanel.getHitRect(panelRect);
//                        mainScrollView.requestChildRectangleOnScreen(includedFeedbackPanel, panelRect, false);
//                        // Hoặc đơn giản hơn nếu panel là con trực tiếp và bạn chỉ muốn nó ở trên cùng:
//                        // mainScrollView.smoothScrollTo(0, includedFeedbackPanel.getTop());
//                        Log.d(TAG, "Scrolling to feedback panel.");
//                    } else {
//                        // Fallback nếu không có ScrollView hoặc không tìm thấy
//                        // requestRectangleOnScreen cố gắng đưa view vào màn hình
//                        Rect rect = new Rect(0, 0, includedFeedbackPanel.getWidth(), includedFeedbackPanel.getHeight());
//                        includedFeedbackPanel.requestRectangleOnScreen(rect, false);
//                        Log.d(TAG, "Requesting rectangle for feedback panel (no ScrollView).");
//                    }
//                    // Đảm bảo panel có focus để TalkBack hoặc các accessibility services chú ý
//                    includedFeedbackPanel.requestFocus();
//                });
//            }
//        });
//    }
//
//
//    @Override
//    public void setFeedbackTriggerVisibility(boolean visible) {
//        Log.d(TAG, "setFeedbackTriggerVisibility: " + visible);
//        runOnUiThread(() -> {
//            if (feedbackTriggerButton != null) {
//                feedbackTriggerButton.setVisibility(visible ? View.VISIBLE : View.GONE);
//            }
//        });
//    }
//
//
//    @Override
//    public void setAnswerInputVisibility(boolean visible) {
//        runOnUiThread(() -> {
//            if (answerEditText != null) {
//                // Giữ nguyên logic cũ của bạn, nhưng đảm bảo nó không bị ẩn đi một cách không mong muốn
//                answerEditText.setVisibility(View.VISIBLE); // Theo logic cũ là luôn VISIBLE
//                Log.d(TAG, "setAnswerInputVisibility: forced VISIBLE (original request for " + visible + ")");
//            }
//        });
//    }
//
//    @Override
//    public void setUIElementsVisibility(boolean visible) {
//        Log.d(TAG, "setUIElementsVisibility: " + visible);
//        runOnUiThread(() -> {
//            int visibilitySetting = visible ? View.VISIBLE : View.INVISIBLE; // Hoặc View.GONE nếu muốn ẩn hẳn
//
//            if (titleTextView != null) titleTextView.setVisibility(View.VISIBLE); // Tiêu đề luôn hiển thị?
//            if (questionTextView != null) questionTextView.setVisibility(visibilitySetting);
//            if (submitButton != null) submitButton.setVisibility(visibilitySetting);
//
//            // answerEditText nên được kiểm soát bởi setAnswerInputVisibility riêng
//            // nhưng nếu logic của bạn là nó đi theo UI chung thì giữ ở đây
//            if (answerEditText != null) {
//                answerEditText.setVisibility(View.VISIBLE); // Theo logic cũ của bạn
//            }
//
//            if (!visible) { // Khi các UI chính bị ẩn
//                if (includedFeedbackPanel != null) includedFeedbackPanel.setVisibility(View.GONE);
//                if (feedbackTriggerButton != null) feedbackTriggerButton.setVisibility(View.GONE);
//            }
//
//            // Kiểm tra và hiển thị thông báo nếu không có nội dung
//            if (questionTextView != null && visible) {
//                CharSequence currentText = questionTextView.getText();
//                if (currentText == null || currentText.toString().isEmpty() ||
//                        currentText.toString().equals("Writing prompt not available.") ||
//                        currentText.toString().equals("Content not available for this exercise.")) {
//                    questionTextView.setText("Content not available for this exercise.");
//                    if (feedbackTriggerButton != null) feedbackTriggerButton.setVisibility(View.GONE);
//                }
//            }
//        });
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        Log.d(TAG, "onPause.");
//        if (controller != null) {
//            controller.onPause();
//        }
//        if (loadingDialog != null && loadingDialog.isShowing()) {
//            loadingDialog.dismiss();
//        }
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        Log.d(TAG, "onResume.");
//        if (controller != null) {
//            controller.onResume(); // Controller sẽ xử lý việc cập nhật UI dựa trên trạng thái của nó
//        } else {
//            Log.w(TAG, "Controller is null in onResume, re-initializing.");
//            controller = new WritingController(this, getIntent());
//            controller.initialize();
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        Log.i(TAG, "onDestroy.");
//        if (loadingDialog != null && loadingDialog.isShowing()) {
//            loadingDialog.dismiss();
//        }
//        loadingDialog = null;
//        if (controller != null) {
//            controller.onDestroy();
//        }
//    }
//
//    @Override
//    public void onBackPressed() {
//        if (controller != null && controller.handleBackPressed()) {
//            return;
//        }
//        // Logic onBackPressed của bạn khá phức tạp, giữ nguyên nếu nó đang hoạt động đúng
//        // Chỉ cần đảm bảo controller.getCurrentButtonState() được sử dụng đúng
//        boolean isFeedbackShowing = includedFeedbackPanel != null && includedFeedbackPanel.getVisibility() == View.VISIBLE;
//        boolean hasUnsavedText = answerEditText != null && answerEditText.getVisibility() == View.VISIBLE && !answerEditText.getText().toString().trim().isEmpty();
//
//        if (hasUnsavedText) {
//            String message = "Your current writing will be lost. Are you sure you want to exit?";
//            if (controller != null && controller.getCurrentButtonState() == WritingController.STATE_RETRY_WRITING && isFeedbackShowing) {
//                message = "Your writing and feedback will be dismissed. Are you sure you want to exit?";
//            } else if (controller != null && controller.getCurrentButtonState() == WritingController.STATE_SUBMIT_WRITING && isFeedbackShowing) {
//                // Trường hợp này không nên xảy ra nếu logic mới hoạt động đúng (feedback ẩn khi state là SUBMIT do edit)
//                // Nhưng nếu có, vẫn hỏi xác nhận.
//                message = "Your feedback will be dismissed and writing lost. Are you sure you want to exit?";
//            }
//
//
//            new AlertDialog.Builder(this)
//                    .setTitle("Exit Exercise")
//                    .setMessage(message)
//                    .setPositiveButton("Exit", (dialog, which) -> {
//                        Log.d(TAG, "Exiting after confirmation.");
//                        super.onBackPressed();
//                    })
//                    .setNegativeButton("Stay", null)
//                    .show();
//        } else {
//            Log.d(TAG, "Exiting without unsaved text or visible feedback requiring specific prompt.");
//            super.onBackPressed();
//        }
//        Log.d(TAG, "onBackPressed final call.");
//    }
//}
package com.example.langhexx.View; // Hoặc package đúng của bạn

import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
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
import com.example.langhexx.R;

public class InternalWritingTopic extends AppCompatActivity implements WritingController.ViewInterface {

    private static final String TAG = "InternalWritingTopicV";

    // --- UI Elements ---
    private ImageView backButton;
    private TextView titleTextView; // Tiêu đề chính của màn hình (VD: "Writing")
    private TextView questionTextView;
    private EditText answerEditText;
    private Button submitButton;
    private TextView feedbackTriggerButton; // Nút "Review" (nếu bạn còn dùng)

    // --- Feedback Panel UI Elements ---
    private View includedFeedbackPanel; // LinearLayout gốc của layout_feedback_panel

    // Icons trong panel
    private ImageView iconTaskResponse, iconCoherenceCohesion, iconGrammarVocabulary, iconLength;

    // TextViews cho TIÊU ĐỀ của mỗi mục feedback trong panel
    private TextView taskResponseTitleTextView, coherenceTitleTextView, grammarTitleTextView, lengthTitleTextView;

    // TextViews cho NỘI DUNG feedback chi tiết trong panel
    private TextView textFeedbackTaskResponse, textFeedbackCoherenceCohesion, textFeedbackGrammarVocabulary, textFeedbackLength;
    private Button btnSeeRevisedVersion;

    private AlertDialog loadingDialog;
    private ScrollView mainScrollView;

    // --- Controller ---
    private WritingController controller;

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

        backButton = findViewById(R.id.back_button);
        titleTextView = findViewById(R.id.title_textview); // Tiêu đề chính của màn hình
        questionTextView = findViewById(R.id.question_textview);
        answerEditText = findViewById(R.id.answer_edittext);
        submitButton = findViewById(R.id.submit_button);
        feedbackTriggerButton = findViewById(R.id.feedback_trigger_button);

        includedFeedbackPanel = findViewById(R.id.included_feedback_panel_internal);

        // Icons
        iconTaskResponse = includedFeedbackPanel.findViewById(R.id.icon_task_response);
        iconCoherenceCohesion = includedFeedbackPanel.findViewById(R.id.icon_coherence_cohesion);
        iconGrammarVocabulary = includedFeedbackPanel.findViewById(R.id.icon_grammar_vocabulary);
        iconLength = includedFeedbackPanel.findViewById(R.id.icon_length);

        // TextViews cho TIÊU ĐỀ của mỗi mục feedback
        taskResponseTitleTextView = includedFeedbackPanel.findViewById(R.id.taskResponseTxt);
        coherenceTitleTextView = includedFeedbackPanel.findViewById(R.id.coherenceTxt);
        grammarTitleTextView = includedFeedbackPanel.findViewById(R.id.grammarTxt);
        lengthTitleTextView = includedFeedbackPanel.findViewById(R.id.lengthTxt);

        // TextViews cho NỘI DUNG feedback chi tiết
        textFeedbackTaskResponse = includedFeedbackPanel.findViewById(R.id.text_feedback_task_response);
        textFeedbackCoherenceCohesion = includedFeedbackPanel.findViewById(R.id.text_feedback_coherence_cohesion);
        textFeedbackGrammarVocabulary = includedFeedbackPanel.findViewById(R.id.text_feedback_grammar_vocabulary);
        textFeedbackLength = includedFeedbackPanel.findViewById(R.id.text_feedback_length);

        btnSeeRevisedVersion = includedFeedbackPanel.findViewById(R.id.btn_see_revised_version);

        if (includedFeedbackPanel != null) {
            includedFeedbackPanel.setVisibility(View.GONE);
        }
        if (feedbackTriggerButton != null) {
            feedbackTriggerButton.setVisibility(View.GONE);
        }
    }

    private void addEvents() {
        Log.d(TAG, "addEvents");
        if (backButton != null) {
            backButton.setOnClickListener(v -> onBackPressed());
        }

        if (submitButton != null) {
            submitButton.setOnClickListener(v -> {
                String userAnswer = answerEditText.getText().toString().trim();
                controller.onSubmitButtonClicked(userAnswer);
            });
        }

        if (feedbackTriggerButton != null) {
            feedbackTriggerButton.setOnClickListener(v -> {
                if (includedFeedbackPanel != null) {
                    if (includedFeedbackPanel.getVisibility() == View.VISIBLE) {
                        // Có thể muốn ẩn đi khi nhấn lại, hoặc không làm gì cả
                        // includedFeedbackPanel.setVisibility(View.GONE);
                    } else {
                        includedFeedbackPanel.setVisibility(View.VISIBLE);
                        focusOnFeedbackPanel();
                    }
                }
            });
        }

        if (btnSeeRevisedVersion != null) {
            btnSeeRevisedVersion.setOnClickListener(v -> {
                Toast.makeText(this, "See revised version - To be implemented", Toast.LENGTH_SHORT).show();
                // controller.onSeeRevisedVersionClicked(); // Nếu cần xử lý logic trong controller
            });
        }

        if (answerEditText != null) {
            answerEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (controller != null) {
                        controller.onAnswerTextChanged();
                    }
                }
            });
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    @Override
    public void displayStructuredAIFeedback(
            String taskResponseFeedback, int taskResponseIconType,
            String coherenceCohesionFeedback, int coherenceCohesionIconType,
            String grammarVocabularyFeedback, int grammarVocabularyIconType,
            String lengthFeedback, int lengthIconType) {
        Log.d(TAG, "displayStructuredAIFeedback called - Populating feedback data.");
        runOnUiThread(() -> {
            if (includedFeedbackPanel == null) {
                Log.e(TAG, "includedFeedbackPanel is null in displayStructuredAIFeedback");
                return;
            }

            boolean allSuccess = taskResponseIconType == 1 &&
                    coherenceCohesionIconType == 1 &&
                    grammarVocabularyIconType == 1 &&
                    lengthIconType == 1;

            int panelBorderColorRes = allSuccess ? R.color.feedback_panel_border_success : R.color.feedback_panel_border_warning;
            int panelBorderColor = ContextCompat.getColor(this, panelBorderColorRes);

            Drawable background = includedFeedbackPanel.getBackground();
            if (background instanceof GradientDrawable) {
                ((GradientDrawable) background).setStroke(dpToPx(2), panelBorderColor);
            } else {
                GradientDrawable mutatedDrawable = (GradientDrawable) ContextCompat.getDrawable(this, R.drawable.feedback_panel_background).mutate();
                if (mutatedDrawable != null) {
                    mutatedDrawable.setStroke(dpToPx(2), panelBorderColor);
                    includedFeedbackPanel.setBackground(mutatedDrawable);
                }
                Log.w(TAG, "Feedback panel background was not a GradientDrawable initially. Applied new one.");
            }

            // --- Task Response ---
            int taskResponseTitleColor = ContextCompat.getColor(this, taskResponseIconType == 1 ? R.color.feedback_text_item_success : R.color.feedback_text_item_warning);
            if (taskResponseTitleTextView != null) {
                taskResponseTitleTextView.setTextColor(taskResponseTitleColor);
            }
            if (textFeedbackTaskResponse != null) {
                textFeedbackTaskResponse.setText(taskResponseFeedback);
                // textFeedbackTaskResponse giữ màu từ XML (@color/feedback_text_color)
            }
            if (iconTaskResponse != null) {
                iconTaskResponse.setImageResource(taskResponseIconType == 1 ? R.drawable.success : R.drawable.warning_icon);
                iconTaskResponse.setColorFilter(ContextCompat.getColor(this, taskResponseIconType == 1 ? R.color.icon_success_tint : R.color.icon_warning_tint));
            }

            // --- Coherence & Cohesion ---
            int coherenceTitleColor = ContextCompat.getColor(this, coherenceCohesionIconType == 1 ? R.color.feedback_text_item_success : R.color.feedback_text_item_warning);
            if (coherenceTitleTextView != null) {
                coherenceTitleTextView.setTextColor(coherenceTitleColor);
            }
            if (textFeedbackCoherenceCohesion != null) {
                textFeedbackCoherenceCohesion.setText(coherenceCohesionFeedback);
                // textFeedbackCoherenceCohesion giữ màu từ XML
            }
            if (iconCoherenceCohesion != null) {
                iconCoherenceCohesion.setImageResource(coherenceCohesionIconType == 1 ? R.drawable.success : R.drawable.warning_icon);
                iconCoherenceCohesion.setColorFilter(ContextCompat.getColor(this, coherenceCohesionIconType == 1 ? R.color.icon_success_tint : R.color.icon_warning_tint));
            }

            // --- Grammar & Vocabulary ---
            int grammarTitleColor = ContextCompat.getColor(this, grammarVocabularyIconType == 1 ? R.color.feedback_text_item_success : R.color.feedback_text_item_warning);
            if (grammarTitleTextView != null) {
                grammarTitleTextView.setTextColor(grammarTitleColor);
            }
            if (textFeedbackGrammarVocabulary != null) {
                textFeedbackGrammarVocabulary.setText(grammarVocabularyFeedback);
                // textFeedbackGrammarVocabulary giữ màu từ XML
            }
            if (iconGrammarVocabulary != null) {
                iconGrammarVocabulary.setImageResource(grammarVocabularyIconType == 1 ? R.drawable.success : R.drawable.warning_icon);
                iconGrammarVocabulary.setColorFilter(ContextCompat.getColor(this, grammarVocabularyIconType == 1 ? R.color.icon_success_tint : R.color.icon_warning_tint));
            }

            // --- Length ---
            int lengthTitleColor = ContextCompat.getColor(this, lengthIconType == 1 ? R.color.feedback_text_item_success : R.color.feedback_text_item_warning);
            if (lengthTitleTextView != null) {
                lengthTitleTextView.setTextColor(lengthTitleColor);
            }
            if (textFeedbackLength != null) {
                textFeedbackLength.setText(lengthFeedback);
                // textFeedbackLength giữ màu từ XML
            }
            if (iconLength != null) {
                iconLength.setImageResource(lengthIconType == 1 ? R.drawable.success : R.drawable.warning_icon);
                iconLength.setColorFilter(ContextCompat.getColor(this, lengthIconType == 1 ? R.color.icon_success_tint : R.color.icon_warning_tint));
            }
        });
    }

    @Override
    public void displayExerciseTitle(String title) {
        Log.d(TAG, "displayExerciseTitle: " + title);
        runOnUiThread(() -> {
            if (titleTextView != null) titleTextView.setText(title); // titleTextView là tiêu đề chính của Activity
        });
    }

    @Override
    public void displayQuestionPrompt(String prompt) {
        Log.d(TAG, "displayQuestionPrompt: " + (prompt != null ? prompt.substring(0, Math.min(prompt.length(), 50))+"..." : "empty"));
        runOnUiThread(() -> {
            if (questionTextView != null) {
                questionTextView.setText(prompt != null ? prompt : "Writing prompt not available.");
            }
        });
    }

    @Override
    public void showToast(String message) {
        Log.d(TAG, "showToast: " + message);
        runOnUiThread(() -> Toast.makeText(InternalWritingTopic.this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void showFailToast(String message) {
        Log.d(TAG, "showFailToast: " + message);
        runOnUiThread(() -> Toast.makeText(InternalWritingTopic.this, "FAIL: " + message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void showConfirmationDialog(String title, String message, Runnable onConfirm) {
        Log.d(TAG, "showConfirmationDialog for Writing");
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Submit", (dialog, which) -> onConfirm.run())
                .setNegativeButton("Cancel", (dialog, which) -> Log.d(TAG, "Submission cancelled."))
                .setCancelable(false)
                .show());
    }

    @Override
    public void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle) {
        Log.i(TAG, "navigateToNextExercise: " + nextExerciseTitle);
        Intent nextIntent = new Intent(InternalWritingTopic.this, InternalWritingTopic.class);
        nextIntent.putExtra("LEVEL_NAME", levelName);
        nextIntent.putExtra("TOPIC_TITLE", topicTitle);
        nextIntent.putExtra("EXERCISE_TITLE", nextExerciseTitle);
        startActivity(nextIntent);
        finish();
    }

    @Override
    public void finishActivity() {
        Log.i(TAG, "finishActivity");
        finish();
    }

    @Override
    public void setSubmitButtonState(String text, boolean enabled) {
        Log.d(TAG, "setSubmitButtonState: Text=" + text + ", Enabled=" + enabled);
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
        runOnUiThread(() -> {
            if (answerEditText != null) {
                answerEditText.setText("");
            }
        });
    }

    @Override
    public void showLoading(String message) {
        runOnUiThread(() -> {
            if (loadingDialog == null || !loadingDialog.isShowing()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                        .setMessage(message)
                        .setCancelable(false);
                loadingDialog = builder.create();
                loadingDialog.show();
            } else {
                loadingDialog.setMessage(message); // Cập nhật message nếu dialog đã hiển thị
            }
        });
    }

    @Override
    public void hideLoading() {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
    }

    @Override
    public void setFeedbackPanelVisibility(boolean visible) {
        Log.d(TAG, "setFeedbackPanelVisibility: " + visible);
        runOnUiThread(() -> {
            if (includedFeedbackPanel != null) {
                includedFeedbackPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void focusOnFeedbackPanel() {
        runOnUiThread(() -> {
            if (includedFeedbackPanel != null && includedFeedbackPanel.getVisibility() == View.VISIBLE) {
                includedFeedbackPanel.post(() -> { // Dùng post để đảm bảo view đã layout xong
                    if (mainScrollView != null) {
                        Rect panelRect = new Rect();
                        includedFeedbackPanel.getHitRect(panelRect); // Lấy vị trí của panel
                        mainScrollView.requestChildRectangleOnScreen(includedFeedbackPanel, panelRect, false);
                        Log.d(TAG, "Scrolling to feedback panel.");
                    } else {
                        Rect rect = new Rect(0, 0, includedFeedbackPanel.getWidth(), includedFeedbackPanel.getHeight());
                        includedFeedbackPanel.requestRectangleOnScreen(rect, false);
                        Log.d(TAG, "Requesting rectangle for feedback panel (no ScrollView).");
                    }
                    includedFeedbackPanel.requestFocus(); // Yêu cầu focus cho accessibility
                });
            }
        });
    }

    @Override
    public void setFeedbackTriggerVisibility(boolean visible) {
        Log.d(TAG, "setFeedbackTriggerVisibility: " + visible);
        runOnUiThread(() -> {
            if (feedbackTriggerButton != null) {
                feedbackTriggerButton.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void setAnswerInputVisibility(boolean visible) {
        runOnUiThread(() -> {
            if (answerEditText != null) {
                answerEditText.setVisibility(View.VISIBLE); // Giữ theo logic cũ
                Log.d(TAG, "setAnswerInputVisibility: forced VISIBLE (original request for " + visible + ")");
            }
        });
    }

    @Override
    public void setUIElementsVisibility(boolean visible) {
        Log.d(TAG, "setUIElementsVisibility: " + visible);
        runOnUiThread(() -> {
            int visibilitySetting = visible ? View.VISIBLE : View.INVISIBLE;

            if (titleTextView != null) titleTextView.setVisibility(View.VISIBLE); // Tiêu đề chính luôn hiện
            if (questionTextView != null) questionTextView.setVisibility(visibilitySetting);
            if (submitButton != null) submitButton.setVisibility(visibilitySetting);

            if (answerEditText != null) {
                answerEditText.setVisibility(View.VISIBLE); // Giữ theo logic cũ
            }

            if (!visible) { // Khi các UI chính bị ẩn (ví dụ khi đang loading)
                if (includedFeedbackPanel != null) includedFeedbackPanel.setVisibility(View.GONE);
                if (feedbackTriggerButton != null) feedbackTriggerButton.setVisibility(View.GONE);
            }

            // Kiểm tra và hiển thị thông báo nếu không có nội dung (khi UI chính được phép hiển thị)
            if (questionTextView != null && visible) {
                CharSequence currentText = questionTextView.getText();
                if (currentText == null || currentText.toString().isEmpty() ||
                        currentText.toString().equals("Writing prompt not available.") ||
                        currentText.toString().equals("Content not available for this exercise.")) {
                    questionTextView.setText("Content not available for this exercise.");
                    // Nếu không có nội dung, có thể ẩn luôn nút submit hoặc trigger feedback
                    if (submitButton != null) submitButton.setEnabled(false);
                    if (feedbackTriggerButton != null) feedbackTriggerButton.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause.");
        if (controller != null) {
            controller.onPause();
        }
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss(); // Tránh WindowLeaked
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume.");
        if (controller != null) {
            controller.onResume();
        } else {
            Log.w(TAG, "Controller is null in onResume, re-initializing.");
            // Cân nhắc việc khởi tạo lại controller ở đây nếu cần thiết
            // Hoặc đảm bảo nó không bao giờ null sau onCreate
            controller = new WritingController(this, getIntent());
            controller.initialize();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy.");
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
        loadingDialog = null; // Giải phóng tham chiếu
        if (controller != null) {
            controller.onDestroy();
            controller = null; // Giải phóng tham chiếu
        }
    }

    @Override
    public void onBackPressed() {
        if (controller != null && controller.handleBackPressed()) {
            // Nếu controller đã xử lý back press (ví dụ: đóng một dialog tùy chỉnh)
            return;
        }

        boolean isFeedbackShowing = includedFeedbackPanel != null && includedFeedbackPanel.getVisibility() == View.VISIBLE;
        boolean hasUnsavedText = answerEditText != null && answerEditText.getVisibility() == View.VISIBLE && !answerEditText.getText().toString().trim().isEmpty();

        if (hasUnsavedText) {
            String message = "Your current writing will be lost. Are you sure you want to exit?";
            // Tùy chỉnh message dựa trên trạng thái hiện tại (ví dụ, nếu feedback đang hiển thị)
            if (controller != null && controller.getCurrentButtonState() == WritingController.STATE_RETRY_WRITING && isFeedbackShowing) {
                message = "Your writing and feedback will be dismissed. Are you sure you want to exit?";
            }
            // Bạn có thể thêm các điều kiện khác cho message nếu cần

            new AlertDialog.Builder(this)
                    .setTitle("Exit Exercise")
                    .setMessage(message)
                    .setPositiveButton("Exit", (dialog, which) -> {
                        Log.d(TAG, "Exiting after confirmation.");
                        super.onBackPressed(); // Gọi hành động back mặc định của Activity
                    })
                    .setNegativeButton("Stay", null) // Không làm gì cả
                    .show();
        } else {
            // Nếu không có text chưa lưu, hoặc feedback không hiển thị theo cách cần xác nhận đặc biệt
            Log.d(TAG, "Exiting without unsaved text or specific prompt needed.");
            super.onBackPressed();
        }
        Log.d(TAG, "onBackPressed final call processing.");
    }
}
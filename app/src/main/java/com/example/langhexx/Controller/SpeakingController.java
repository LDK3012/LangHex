package com.example.langhexx.Controller;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.langhexx.Model.SpeakingContract;
import com.example.langhexx.Model.SpeakingModel;

import java.util.List;

public class SpeakingController implements SpeakingContract.Controller, SpeakingContract.QuestionListener, SpeakingContract.EvaluationListener {

    private static final String TAG = "SpeakingController";

    private SpeakingContract.Model model;
    private SpeakingContract.View view;
    private Handler handler = new Handler(Looper.getMainLooper());

    private String levelName;
    private String topicTitle;
    private boolean ttsReady = false;

    public SpeakingController(SpeakingContract.View view, Context context, String levelName, String topicTitle) {
        this.view = view;
        this.model = new SpeakingModel(context); // Giả sử SpeakingModel có constructor nhận Context
        this.levelName = levelName;
        this.topicTitle = topicTitle;

        if (levelName == null || topicTitle == null || levelName.isEmpty() || topicTitle.isEmpty()) {
            if (view != null) {
                view.showError("Thiếu thông tin Level hoặc Topic.");
                view.setMicButtonEnabled(false);
            } else {
                Log.e(TAG, "View is null in constructor when trying to show error for missing level/topic.");
            }
        }
    }

    @Override
    public void viewDidLoad() {
        Log.d(TAG, "Controller: View did load.");
        if (view != null) {
            view.setMicButtonEnabled(false); // Disable mic initially until data loads
        }
        // Data loading will be triggered by onTtsReady()
    }

    public void onTtsReady() {
        Log.d(TAG,"Controller: TTS is ready.");
        ttsReady = true;
        loadData(); // Now load data if level/topic are valid
    }

    @Override
    public void loadData() {
        if (view == null) {
            Log.e(TAG, "Controller: View is null, cannot load data.");
            return;
        }
        if (ttsReady && levelName != null && !levelName.isEmpty() && topicTitle != null && !topicTitle.isEmpty()) {
            Log.d(TAG, "Controller: Requesting model to load data for " + levelName + ", " + topicTitle);
            model.loadQuestions(levelName, topicTitle, this);
        } else if (!ttsReady) {
            Log.w(TAG,"Controller: Waiting for TTS to be ready before loading data.");
            // Optionally, show a message to the user via view.showToast or similar
        } else {
            Log.e(TAG,"Controller: Cannot load data due to missing level/topic information.");
            // This case should have been handled in constructor, but double check
            view.showError("Không thể tải dữ liệu do thiếu thông tin Level hoặc Topic.");
            view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void onMicButtonClicked() {
        Log.d(TAG, "Controller: Mic button clicked.");
        if (view == null) {
            Log.e(TAG, "View is null in onMicButtonClicked");
            return;
        }
        Context context = view.getContext();
        if (context == null) {
            Log.e(TAG, "Context is null in onMicButtonClicked");
            view.showToast("Lỗi: Không thể truy cập Context.");
            return;
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            view.requestAudioPermission();
        } else {
            if (model != null && model.getCurrentQuestion() != null) {
                view.startListening(); // Yêu cầu View bắt đầu lắng nghe nội bộ
            } else {
                view.showToast("Chưa có câu hỏi để trả lời hoặc model chưa sẵn sàng.");
            }
        }
    }

    @Override
    public void onCloseButtonClicked() {
        Log.d(TAG, "Controller: Close button clicked.");
        if (view != null) {
            view.finishActivity();
        }
    }

    @Override
    public void onQuestionSpeakerClicked(String question) {
        Log.d(TAG, "Controller: Question speaker clicked.");
        if (view == null) return;
        if (ttsReady) {
            view.stopListening(); // Dừng lắng nghe nếu đang diễn ra
            view.speakText(question, "question_manual_" + (model != null ? model.getCurrentQuestionIndex() : "unknown"));
        } else {
            view.showToast("Chức năng nói chưa sẵn sàng.");
        }
    }

    @Override
    public void onResponseSpeakerClicked(String response) {
        Log.d(TAG, "Controller: Response speaker clicked.");
        if (view == null) return;
        if (ttsReady) {
            view.stopListening(); // Dừng lắng nghe nếu đang diễn ra
            view.speakText(response, "response_manual_" + (model != null ? model.getCurrentQuestionIndex() : "unknown"));
        } else {
            view.showToast("Chức năng nói chưa sẵn sàng.");
        }
    }

    @Override
    public void onWarningIconClicked(String message) {
        Log.d(TAG, "Controller: Warning icon clicked.");
        if (view != null) {
            view.showFeedbackDialog(message);
        }
    }

    @Override
    public void onSpeechResult(String spokenText) {
        Log.d(TAG, "Controller: Received speech result: " + spokenText);
        if (view == null || model == null) {
            Log.e(TAG, "View or Model is null in onSpeechResult");
            if (view != null) view.setMicButtonEnabled(true);
            return;
        }
        String currentQuestion = model.getCurrentQuestion();
        if (currentQuestion != null) {
            view.setMicButtonEnabled(false); // Vô hiệu hóa mic trong khi đánh giá
            model.evaluateAnswer(currentQuestion, spokenText, this);
        } else {
            Log.e(TAG,"Controller: Cannot evaluate, current question is null.");
            view.showToast("Lỗi: Không tìm thấy câu hỏi hiện tại để đánh giá.");
            view.setMicButtonEnabled(true);
        }
    }

    @Override
    public void onSpeechError(String errorReason) {
        Log.w(TAG, "Controller: Received speech error: " + errorReason);
        if (view == null) return;
        if (errorReason != null) {
            view.showToast(errorReason);
        }
        view.setMicButtonEnabled(true); // Cho phép thử lại
    }

    @Override
    public void onPermissionResult(boolean granted) {
        Log.i(TAG,"Controller: Received permission result. Granted: " + granted);
        if (view == null) return;
        if (granted) {
            view.showToast("Đã cấp quyền ghi âm. Bạn có thể nhấn nút micro.");
            view.setMicButtonEnabled(true); // Kích hoạt nút micro sau khi có quyền
        } else {
            view.showToast("Cần quyền ghi âm để nhận dạng giọng nói.");
            view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Controller: onDestroy called.");
        if (model != null) {
            model.cleanup();
        }
        if(handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        view = null; // Giảm tham chiếu
        model = null;
    }

    // --- SpeakingContract.QuestionListener Implementation ---
    @Override
    public void onQuestionsLoaded(List<String> questions) {
        Log.i(TAG, "Controller: Questions loaded. Count: " + (questions != null ? questions.size() : "null"));
        if (view == null || model == null) return;

        String firstQuestion = model.getCurrentQuestion();
        if (firstQuestion != null) {
            view.displayQuestion(firstQuestion);
            view.setMicButtonEnabled(true); // Kích hoạt mic khi câu hỏi đã sẵn sàng
        } else {
            Log.w(TAG, "Controller: No questions available or first question is null.");
            view.showError(questions != null && questions.isEmpty() ? "Không có câu hỏi nào trong bài tập này." : "Không thể hiển thị câu hỏi đầu tiên.");
            view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void onQuestionLoadError(String error) {
        Log.e(TAG, "Controller: Question load error: " + error);
        if (view == null) return;
        view.showError(error);
        view.setMicButtonEnabled(false);
    }

    // --- SpeakingContract.EvaluationListener Implementation ---
    @Override
    public void onEvaluationSuccess(String userAnswer, boolean isCorrect, String feedbackVi, String suggestionEn) {
        Log.i(TAG, "Controller: Evaluation success. Correct: " + isCorrect);
        if (view == null || model == null) return;

        view.displayUserAnswer(userAnswer, isCorrect);
        view.playSound(isCorrect);

        if (isCorrect) {
            view.showWarningIcon(false, null);
            view.showCustomToast(true, "Chính xác!");

            model.advanceQuestionIndex();
            String nextQuestion = model.getCurrentQuestion();
            if (nextQuestion != null) {
                handler.postDelayed(() -> {
                    if(view != null) {
                        view.displayQuestion(nextQuestion);
                        view.setMicButtonEnabled(true); // Bật mic cho câu hỏi tiếp theo
                    }
                }, 2000);
            } else { // Hoàn thành tất cả câu hỏi
                handler.postDelayed(() -> {
                    if (view != null) {
                        view.showCompletionMessage();
                        view.setMicButtonEnabled(false); // Vô hiệu hóa mic khi hoàn thành
                    }
                }, 1500);
            }
        } else { // Trả lời sai
            String combinedFeedback = feedbackVi != null ? feedbackVi : "Câu trả lời chưa chính xác.";
            if (suggestionEn != null && !suggestionEn.trim().isEmpty()) {
                combinedFeedback += "\n\n" + "Gợi ý (tiếng Anh):\n" + suggestionEn;
            }
            view.showWarningIcon(true, combinedFeedback);
            view.showCustomToast(false, "Chưa đúng. Xem gợi ý và thử lại.");
            view.setMicButtonEnabled(true); // Cho phép thử lại
        }
    }

    @Override
    public void onEvaluationError(String userAnswer, String errorType) {
        Log.e(TAG, "Controller: Evaluation error: " + errorType + " for answer: " + userAnswer);
        if (view == null) return;

        view.displayUserAnswer(userAnswer, false); // Hiển thị câu trả lời (sai)
        view.showWarningIcon(false, null); // Ẩn icon warning
        view.showToast("Lỗi đánh giá: " + errorType + ". Vui lòng thử lại.");
        view.setMicButtonEnabled(true); // Cho phép thử lại
    }
}
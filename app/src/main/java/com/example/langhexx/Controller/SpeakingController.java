//package com.example.langhexx.Controller; // Tạo package Controller nếu chưa có
//
//import android.content.Context;
//import android.os.Handler;
//import android.os.Looper;
//import android.util.Log;
//import androidx.core.content.ContextCompat;
//import android.Manifest;
//import android.content.pm.PackageManager;
//
//import com.example.langhexx.Model.SpeakingModel;
//import com.example.langhexx.Model.SpeakingContract;
//
//import java.util.List;
//
//public class SpeakingController implements SpeakingContract.Controller, SpeakingContract.QuestionListener, SpeakingContract.EvaluationListener {
//
//    private static final String TAG = "SpeakingController";
//
//    private SpeakingContract.Model model;
//    private SpeakingContract.View view; // Tham chiếu đến View qua Interface
//    private Handler handler = new Handler(Looper.getMainLooper());
//
//    private String levelName;
//    private String topicTitle;
//    private boolean ttsReady = false; // Cờ báo TTS đã sẵn sàng chưa
//
//    // Constructor nhận View và Context (để khởi tạo Model)
//    public SpeakingController(SpeakingContract.View view, Context context, String levelName, String topicTitle) {
//        this.view = view;
//        this.model = new SpeakingModel(context); // Khởi tạo Model
//        this.levelName = levelName;
//        this.topicTitle = topicTitle;
//
//        if (levelName == null || topicTitle == null || levelName.isEmpty() || topicTitle.isEmpty()) {
//            view.showError("Thiếu thông tin Level hoặc Topic.");
//            view.setMicButtonEnabled(false);
//        }
//    }
//
//    // --- SpeakingContract.Controller Implementation ---
//
//    @Override
//    public void viewDidLoad() {
//        Log.d(TAG, "Controller: View did load.");
//        view.setMicButtonEnabled(false); // Disable mic initially
//        // Data loading will be triggered by onTtsReady()
//    }
//
//    // Thêm phương thức này để View báo khi TTS sẵn sàng
//    public void onTtsReady() {
//        Log.d(TAG,"Controller: TTS is ready.");
//        ttsReady = true;
//        // Now load data if level/topic are valid
//        loadData();
//    }
//
//    @Override
//    public void loadData() {
//        // Chỉ load nếu TTS sẵn sàng và có level/topic
//        if (ttsReady && levelName != null && topicTitle != null) {
//            Log.d(TAG, "Controller: Requesting model to load data for " + levelName + ", " + topicTitle);
//            model.loadQuestions(levelName, topicTitle, this); // Controller là listener của Model
//        } else if (!ttsReady) {
//            Log.w(TAG,"Controller: Waiting for TTS to be ready before loading data.");
//        } else {
//            // levelName hoặc topicTitle null/empty (đã được xử lý trong constructor)
//            Log.e(TAG,"Controller: Cannot load data due to missing level/topic.");
//        }
//    }
//
//    @Override
//    public void onMicButtonClicked() {
//        Log.d(TAG, "Controller: Mic button clicked.");
//        Context context = view.getContext(); // Lấy context từ View
//        if (context == null) return; // Không thể kiểm tra quyền nếu context null
//
//        // Check permission first
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
//            view.requestAudioPermission(); // View handles permission request UI
//        } else {
//            // Permission granted, start recognition
//            if (model.getCurrentQuestion() != null) {
//                view.startSpeechRecognitionIntent(); // View handles starting intent
//            } else {
//                view.showToast("Chưa có câu hỏi để trả lời.");
//            }
//        }
//    }
//
//    @Override
//    public void onCloseButtonClicked() {
//        Log.d(TAG, "Controller: Close button clicked.");
//        view.finishActivity(); // View handles finishing
//    }
//
//    @Override
//    public void onQuestionSpeakerClicked(String question) {
//        Log.d(TAG, "Controller: Question speaker clicked.");
//        if (ttsReady) {
//            view.speakText(question, "question_manual_" + model.getCurrentQuestionIndex());
//        } else {
//            view.showToast("Chức năng nói chưa sẵn sàng.");
//        }
//    }
//
//    @Override
//    public void onResponseSpeakerClicked(String response) {
//        Log.d(TAG, "Controller: Response speaker clicked.");
//        if (ttsReady) {
//            view.speakText(response, "response_manual_" + model.getCurrentQuestionIndex());
//        } else {
//            view.showToast("Chức năng nói chưa sẵn sàng.");
//        }
//    }
//
//    @Override
//    public void onWarningIconClicked(String message) {
//        Log.d(TAG, "Controller: Warning icon clicked.");
//        view.showFeedbackDialog(message); // View handles showing dialog
//    }
//
//    @Override
//    public void onSpeechResult(String spokenText) {
//        Log.d(TAG, "Controller: Received speech result: " + spokenText);
//        String currentQuestion = model.getCurrentQuestion();
//        if (currentQuestion != null) {
//            view.setMicButtonEnabled(false); // Disable mic while evaluating
//            model.evaluateAnswer(currentQuestion, spokenText, this); // Ask model to evaluate
//        } else {
//            Log.e(TAG,"Controller: Cannot evaluate, current question is null.");
//            view.showToast("Lỗi: Không tìm thấy câu hỏi hiện tại để đánh giá.");
//            view.setMicButtonEnabled(true); // Re-enable mic if error
//        }
//    }
//
//    @Override
//    public void onSpeechError(String errorReason) {
//        Log.w(TAG, "Controller: Received speech error: " + errorReason);
//        if (errorReason != null) { // Only show toast if not cancelled by user
//            view.showToast(errorReason);
//        }
//        // Re-enable mic unless evaluation is in progress (which shouldn't be the case on error)
//        view.setMicButtonEnabled(true);
//    }
//
//    @Override
//    public void onPermissionResult(boolean granted) {
//        Log.i(TAG,"Controller: Received permission result. Granted: " + granted);
//        if (granted) {
//            view.showToast("Đã cấp quyền ghi âm. Bạn có thể nhấn nút micro.");
//        } else {
//            view.showToast("Cần quyền ghi âm để nhận dạng giọng nói.");
//            view.setMicButtonEnabled(false); // Disable mic if permission denied
//        }
//    }
//
//    @Override
//    public void onDestroy() {
//        Log.d(TAG, "Controller: onDestroy called. Cleaning up model and handler.");
//        if (model != null) {
//            model.cleanup(); // Tell model to cancel requests etc.
//        }
//        if(handler != null) {
//            handler.removeCallbacksAndMessages(null); // Remove pending tasks
//        }
//        // Giảm tham chiếu để GC có thể thu dọn nếu cần
//        view = null;
//        model = null;
//    }
//
//    // --- SpeakingContract.QuestionListener Implementation (Model -> Controller) ---
//
//    @Override
//    public void onQuestionsLoaded(List<String> questions) {
//        Log.i(TAG, "Controller: Questions loaded successfully. Count: " + questions.size());
//        if (view == null) return; // View might be destroyed
//
//        String firstQuestion = model.getCurrentQuestion();
//        if (firstQuestion != null) {
//            view.displayQuestion(firstQuestion); // View displays the question
//            // Mic button is enabled within displayQuestion via setMicButtonEnabled(true)
//        } else {
//            view.showError("Không thể hiển thị câu hỏi đầu tiên.");
//            view.setMicButtonEnabled(false);
//        }
//    }
//
//    @Override
//    public void onQuestionLoadError(String error) {
//        Log.e(TAG, "Controller: Question load error: " + error);
//        if (view == null) return;
//        view.showError(error); // View displays error
//        view.setMicButtonEnabled(false);
//    }
//
//    // --- SpeakingContract.EvaluationListener Implementation (Model -> Controller) ---
//
//    @Override
//    public void onEvaluationSuccess(String userAnswer, boolean isCorrect, String feedbackVi, String suggestionEn) {
//        Log.i(TAG, "Controller: Evaluation success. Correct: " + isCorrect);
//        if (view == null) return; // View might be destroyed
//
//        // Update View
//        view.displayUserAnswer(userAnswer, isCorrect);
//        view.playSound(isCorrect);
//
//        if (isCorrect) {
//            view.showWarningIcon(false, null);
//            view.showCustomToast(true, "Chính xác!");
//
//            // Advance Logic
//            model.advanceQuestionIndex();
//            String nextQuestion = model.getCurrentQuestion();
//            if (nextQuestion != null) {
//                // Schedule display of next question
//                handler.postDelayed(() -> {
//                    if(view != null) view.displayQuestion(nextQuestion);
//                }, 2000); // Delay for correct answer
//            } else {
//                // All questions completed
//                handler.postDelayed(() -> {
//                    if (view != null) view.showCompletionMessage();
//                }, 1500);
//            }
//        } else {
//            // Incorrect Answer
//            String combinedFeedback = feedbackVi != null ? feedbackVi : "Câu trả lời chưa chính xác.";
//            if (suggestionEn != null && !suggestionEn.trim().isEmpty()) {
//                combinedFeedback += "\n\n" + "Gợi ý (tiếng Anh):\n" + suggestionEn;
//            }
//            view.showWarningIcon(true, combinedFeedback);
//            view.showCustomToast(false, "Chưa đúng. Xem gợi ý và thử lại.");
//            view.setMicButtonEnabled(true); // Re-enable mic for retry
//        }
//    }
//
//    @Override
//    public void onEvaluationError(String userAnswer, String errorType) {
//        Log.e(TAG, "Controller: Evaluation error: " + errorType);
//        if (view == null) return; // View might be destroyed
//
//        // Update View for Error State
//        view.displayUserAnswer(userAnswer, false); // Show user answer
//
//        // Optionally change text color to gray or default and add error text
//        // This logic might need adjustment based on how displayUserAnswer is implemented
//        // Example: Introduce a displayEvaluationFailure(userAnswer, errorType) in View contract?
//        // For now, rely on Toast and enabling mic:
//        view.showWarningIcon(false, null); // Hide warning
//        view.showToast("Lỗi đánh giá: " + errorType + ". Vui lòng thử lại.");
//        view.setMicButtonEnabled(true); // Re-enable mic
//    }
//}

package com.example.langhexx.Controller; // Tạo package Controller nếu chưa có

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;

import com.example.langhexx.Model.SpeakingModel;
import com.example.langhexx.Model.SpeakingContract;

import java.util.List;

public class SpeakingController implements SpeakingContract.Controller, SpeakingContract.QuestionListener, SpeakingContract.EvaluationListener {

    private static final String TAG = "SpeakingController";

    private SpeakingContract.Model model;
    private SpeakingContract.View view; // Tham chiếu đến View qua Interface
    private Handler handler = new Handler(Looper.getMainLooper());

    private String levelName;
    private String topicTitle;
    private boolean ttsReady = false; // Cờ báo TTS đã sẵn sàng chưa

    // Constructor nhận View và Context (để khởi tạo Model)
    public SpeakingController(SpeakingContract.View view, Context context, String levelName, String topicTitle) {
        this.view = view;
        this.model = new SpeakingModel(context); // Khởi tạo Model
        this.levelName = levelName;
        this.topicTitle = topicTitle;

        if (levelName == null || topicTitle == null || levelName.isEmpty() || topicTitle.isEmpty()) {
            // Đảm bảo view không null trước khi gọi
            if (view != null) {
                view.showError("Thiếu thông tin Level hoặc Topic.");
                view.setMicButtonEnabled(false);
            } else {
                Log.e(TAG, "View is null in constructor when trying to show error.");
            }
        }
    }

    // --- SpeakingContract.Controller Implementation ---

    @Override
    public void viewDidLoad() {
        Log.d(TAG, "Controller: View did load.");
        if (view != null) {
            view.setMicButtonEnabled(false); // Disable mic initially
        }
        // Data loading will be triggered by onTtsReady()
    }

    // Thêm phương thức này để View báo khi TTS sẵn sàng
    public void onTtsReady() {
        Log.d(TAG,"Controller: TTS is ready.");
        ttsReady = true;
        // Now load data if level/topic are valid
        loadData();
    }

    @Override
    public void loadData() {
        // Chỉ load nếu TTS sẵn sàng và có level/topic
        if (ttsReady && levelName != null && topicTitle != null) {
            Log.d(TAG, "Controller: Requesting model to load data for " + levelName + ", " + topicTitle);
            model.loadQuestions(levelName, topicTitle, this); // Controller là listener của Model
        } else if (!ttsReady) {
            Log.w(TAG,"Controller: Waiting for TTS to be ready before loading data.");
        } else {
            // levelName hoặc topicTitle null/empty (đã được xử lý trong constructor)
            Log.e(TAG,"Controller: Cannot load data due to missing level/topic.");
        }
    }

    @Override
    public void onMicButtonClicked() {
        Log.d(TAG, "Controller: Mic button clicked.");
        if (view == null) return; // Kiểm tra view null
        Context context = view.getContext(); // Lấy context từ View
        if (context == null) return; // Không thể kiểm tra quyền nếu context null

        // Check permission first
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            view.requestAudioPermission(); // View handles permission request UI
        } else {
            // Permission granted, start recognition
            if (model.getCurrentQuestion() != null) {
                view.startSpeechRecognitionIntent(); // View handles starting intent
            } else {
                // Chỉ hiển thị toast nếu view còn tồn tại
                if (view != null) {
                    view.showToast("Chưa có câu hỏi để trả lời.");
                }
            }
        }
    }

    @Override
    public void onCloseButtonClicked() {
        Log.d(TAG, "Controller: Close button clicked.");
        if (view != null) { // Kiểm tra view null
            view.finishActivity(); // View handles finishing
        }
    }

    @Override
    public void onQuestionSpeakerClicked(String question) {
        Log.d(TAG, "Controller: Question speaker clicked.");
        if (view == null) return; // Kiểm tra view null
        if (ttsReady) {
            view.speakText(question, "question_manual_" + model.getCurrentQuestionIndex());
        } else {
            view.showToast("Chức năng nói chưa sẵn sàng.");
        }
    }

    @Override
    public void onResponseSpeakerClicked(String response) {
        Log.d(TAG, "Controller: Response speaker clicked.");
        if (view == null) return; // Kiểm tra view null
        if (ttsReady) {
            view.speakText(response, "response_manual_" + model.getCurrentQuestionIndex());
        } else {
            view.showToast("Chức năng nói chưa sẵn sàng.");
        }
    }

    @Override
    public void onWarningIconClicked(String message) {
        Log.d(TAG, "Controller: Warning icon clicked.");
        if (view != null) { // Kiểm tra view null
            view.showFeedbackDialog(message); // View handles showing dialog
        }
    }

    @Override
    public void onSpeechResult(String spokenText) {
        Log.d(TAG, "Controller: Received speech result: " + spokenText);
        if (view == null || model == null) return; // Kiểm tra view và model null
        String currentQuestion = model.getCurrentQuestion();
        if (currentQuestion != null) {
            view.setMicButtonEnabled(false); // Disable mic while evaluating
            model.evaluateAnswer(currentQuestion, spokenText, this); // Ask model to evaluate
        } else {
            Log.e(TAG,"Controller: Cannot evaluate, current question is null.");
            // Chỉ hiển thị toast và bật mic nếu view còn tồn tại
            if (view != null) {
                view.showToast("Lỗi: Không tìm thấy câu hỏi hiện tại để đánh giá.");
                view.setMicButtonEnabled(true); // Re-enable mic if error
            }
        }
    }

    @Override
    public void onSpeechError(String errorReason) {
        Log.w(TAG, "Controller: Received speech error: " + errorReason);
        if (view == null) return; // Kiểm tra view null
        if (errorReason != null) { // Only show toast if not cancelled by user
            view.showToast(errorReason);
        }
        // Re-enable mic unless evaluation is in progress (which shouldn't be the case on error)
        view.setMicButtonEnabled(true);
    }

    @Override
    public void onPermissionResult(boolean granted) {
        Log.i(TAG,"Controller: Received permission result. Granted: " + granted);
        if (view == null) return; // Kiểm tra view null
        if (granted) {
            view.showToast("Đã cấp quyền ghi âm. Bạn có thể nhấn nút micro.");
        } else {
            view.showToast("Cần quyền ghi âm để nhận dạng giọng nói.");
            view.setMicButtonEnabled(false); // Disable mic if permission denied
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Controller: onDestroy called. Cleaning up model and handler.");
        if (model != null) {
            model.cleanup(); // Tell model to cancel requests etc.
        }
        if(handler != null) {
            handler.removeCallbacksAndMessages(null); // Remove pending tasks
        }
        // Giảm tham chiếu để GC có thể thu dọn nếu cần
        view = null;
        model = null;
    }

    // --- SpeakingContract.QuestionListener Implementation (Model -> Controller) ---

    @Override
    public void onQuestionsLoaded(List<String> questions) {
        Log.i(TAG, "Controller: Questions loaded successfully. Count: " + questions.size());
        if (view == null || model == null) return; // View or model might be destroyed

        String firstQuestion = model.getCurrentQuestion();
        if (firstQuestion != null) {
            view.displayQuestion(firstQuestion); // View displays the question
            // Mic button is enabled within displayQuestion via setMicButtonEnabled(true)
        } else {
            // Xử lý trường hợp danh sách câu hỏi rỗng ngay từ đầu
            if (questions.isEmpty()) {
                Log.w(TAG, "Controller: Loaded question list is empty.");
                view.showError("Không có câu hỏi nào trong bài tập này.");
            } else {
                Log.e(TAG, "Controller: Model returned null for first question despite non-empty list.");
                view.showError("Không thể hiển thị câu hỏi đầu tiên.");
            }
            view.setMicButtonEnabled(false);
        }
    }

    @Override
    public void onQuestionLoadError(String error) {
        Log.e(TAG, "Controller: Question load error: " + error);
        if (view == null) return;
        view.showError(error); // View displays error
        view.setMicButtonEnabled(false);
    }

    // --- SpeakingContract.EvaluationListener Implementation (Model -> Controller) ---

    @Override
    public void onEvaluationSuccess(String userAnswer, boolean isCorrect, String feedbackVi, String suggestionEn) {
        Log.i(TAG, "Controller: Evaluation success. Correct: " + isCorrect);
        if (view == null || model == null) return; // View or model might be destroyed

        // Update View
        view.displayUserAnswer(userAnswer, isCorrect);
        view.playSound(isCorrect);

        if (isCorrect) {
            view.showWarningIcon(false, null); // Ẩn icon cảnh báo nếu có
            view.showCustomToast(true, "Chính xác!"); // Hiển thị toast thành công

            // Advance Logic
            model.advanceQuestionIndex(); // Tăng chỉ số câu hỏi trong Model
            String nextQuestion = model.getCurrentQuestion(); // Lấy câu hỏi tiếp theo từ Model

            if (nextQuestion != null) {
                // Còn câu hỏi tiếp theo
                handler.postDelayed(() -> {
                    if(view != null) view.displayQuestion(nextQuestion); // Hiển thị câu hỏi mới sau 1 khoảng trễ
                }, 2000); // Delay for correct answer
            } else {
                // *** ĐÃ HOÀN THÀNH TẤT CẢ CÂU HỎI (VÀ CÂU CUỐI LÀ ĐÚNG) ***
                handler.postDelayed(() -> {
                    if (view != null) {
                        view.showCompletionMessage(); // Hiển thị thông báo hoàn thành
                        view.setMicButtonEnabled(false); // Vô hiệu hóa micro
                        Log.i(TAG, "All questions completed correctly!");
                    }
                }, 1500); // Delay để người dùng thấy kết quả câu cuối
            }
        } else {
            // Incorrect Answer
            String combinedFeedback = feedbackVi != null ? feedbackVi : "Câu trả lời chưa chính xác.";
            if (suggestionEn != null && !suggestionEn.trim().isEmpty()) {
                combinedFeedback += "\n\n" + "Gợi ý (tiếng Anh):\n" + suggestionEn;
            }
            view.showWarningIcon(true, combinedFeedback); // Hiển thị icon cảnh báo/gợi ý
            view.showCustomToast(false, "Chưa đúng. Xem gợi ý và thử lại."); // Hiển thị toast thất bại
            view.setMicButtonEnabled(true); // Bật lại micro để thử lại
        }
    }

    @Override
    public void onEvaluationError(String userAnswer, String errorType) {
        Log.e(TAG, "Controller: Evaluation error: " + errorType);
        if (view == null) return; // View might be destroyed

        // Update View for Error State
        view.displayUserAnswer(userAnswer, false); // Hiển thị lại câu trả lời của user (đánh dấu sai)

        view.showWarningIcon(false, null); // Ẩn icon warning nếu có
        view.showToast("Lỗi đánh giá: " + errorType + ". Vui lòng thử lại.");
        view.setMicButtonEnabled(true); // Bật lại micro
    }
}
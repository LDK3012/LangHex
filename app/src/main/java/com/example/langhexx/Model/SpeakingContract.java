package com.example.langhexx.Model;
import android.content.Context;

import java.util.List ;
public interface SpeakingContract {

    interface Model {
        void loadQuestions(String levelName, String topicTitle, QuestionListener listener);
        void evaluateAnswer(String question, String userAnswer, EvaluationListener listener);
        String getCurrentQuestion();
        int getCurrentQuestionIndex();
        int getQuestionCount();
        void advanceQuestionIndex();
        void cleanup(); // Để hủy các request nếu cần
    }

    interface View {
        void displayQuestion(String question);
        void displayUserAnswer(String userAnswer, boolean isCorrect);
        void displayEvaluationFeedback(String feedbackVi, String suggestionEn); // Chỉ hiển thị phần feedback
        void showFeedbackDialog(String message); // Hiển thị dialog chi tiết
        void showCompletionMessage();
        void showError(String message); // Hiển thị lỗi chung
        void showToast(String message);
        void showCustomToast(boolean success, String message);
        void setMicButtonEnabled(boolean enabled);
        void playSound(boolean isCorrect);
        void speakText(String text, String utteranceId);
        void showWarningIcon(boolean show, String feedbackMessage); // Kết hợp hiển thị và lưu message
        void hideResponseElements(); // Ẩn các thành phần của câu trả lời cũ khi hiển thị câu hỏi mới
        void updateUiForNewQuestion(String question); // Cập nhật view mới cho câu hỏi
        void scrollDown(); // Cuộn xuống
        Context getContext(); // Controller có thể cần Context từ View
        void requestAudioPermission();
        void startSpeechRecognitionIntent();
        void finishActivity(); // Để đóng activity
    }

    interface Controller {
        void viewDidLoad(); // Được gọi khi View (Activity) được tạo
        void loadData(); // Yêu cầu tải dữ liệu ban đầu
        void onMicButtonClicked();
        void onCloseButtonClicked();
        void onQuestionSpeakerClicked(String question);
        void onResponseSpeakerClicked(String response);
        void onWarningIconClicked(String message);
        void onSpeechResult(String spokenText);
        void onSpeechError(String errorReason);
        void onPermissionResult(boolean granted);
        void onDestroy(); // Được gọi khi View bị hủy
    }

    // Listener interfaces for Model -> Controller communication
    interface QuestionListener {
        void onQuestionsLoaded(List<String> questions);
        void onQuestionLoadError(String error);
    }

    interface EvaluationListener {
        void onEvaluationSuccess(String userAnswer, boolean isCorrect, String feedbackVi, String suggestionEn);
        void onEvaluationError(String userAnswer, String errorType);
    }

}

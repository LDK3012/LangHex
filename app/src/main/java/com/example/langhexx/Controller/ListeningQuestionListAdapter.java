package com.example.langhexx.Controller;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.example.langhexx.Model.ListeningQuestion;
import com.example.langhexx.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ListeningQuestionListAdapter extends BaseAdapter {

    private static final String TAG = "ListenQuestionAdapter";
    private Context context;
    private List<ListeningQuestion> questions;
    private LayoutInflater inflater;

    // --- State Variables ---
    private Map<Integer, Integer> selectedAnswers = new HashMap<>();
    private Map<Integer, Integer> submittedAnswers = new HashMap<>();
    private Map<Integer, Boolean> correctnessMap = new HashMap<>();
    private boolean resultsShown = false;
    private boolean answersDisabled = false;

    public ListeningQuestionListAdapter(Context context, List<ListeningQuestion> questions) {
        this.context = context;
        this.questions = questions;
        this.inflater = LayoutInflater.from(context);
        for (int i = 0; i < questions.size(); i++) {
            selectedAnswers.put(i, -1);
        }
    }

    /**
     * Cập nhật danh sách câu hỏi và reset trạng thái của adapter.
     * @param newQuestions Danh sách câu hỏi mới.
     */
    public void updateData(List<ListeningQuestion> newQuestions) {
        this.questions = newQuestions;
        this.selectedAnswers.clear();
        this.submittedAnswers.clear();
        this.correctnessMap.clear();
        this.resultsShown = false;
        this.answersDisabled = false;
        for (int i = 0; i < questions.size(); i++) {
            selectedAnswers.put(i, -1);
        }
        notifyDataSetChanged();
    }

    /**
     * Nhận kết quả từ Activity và cập nhật trạng thái để hiển thị icon đúng/sai,
     * đồng thời vô hiệu hóa các lựa chọn.
     * @param userAnswers Map chứa lựa chọn của người dùng (QuestionIndex -> SelectedRadioButtonId).
     * @param correctness Map chứa kết quả đúng/sai (QuestionIndex -> Boolean).
     */
    public void showResults(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctness) {
        this.submittedAnswers = new HashMap<>(userAnswers);
        this.correctnessMap = new HashMap<>(correctness);
        this.resultsShown = true;
        this.answersDisabled = true;
        Log.d(TAG, "Showing results. User answers count: " + submittedAnswers.size() + ", Correctness count: " + correctnessMap.size());
        notifyDataSetChanged();
    }

    /**
     * Trả về Map chứa các lựa chọn hiện tại của người dùng (trước khi submit).
     * @return Map<QuestionIndex, SelectedRadioButtonId>.
     */
    public Map<Integer, Integer> getSelectedAnswers() {
        return new HashMap<>(selectedAnswers);
    }

    /**
     * (Tùy chọn) Cho phép vô hiệu hóa các lựa chọn từ bên ngoài.
     * @param disabled True để vô hiệu hóa, False để kích hoạt.
     */
    public void setAnswersDisabled(boolean disabled) {
        this.answersDisabled = disabled;
    }

    @Override
    public int getCount() {
        return questions.size();
    }

    @Override
    public ListeningQuestion getItem(int position) {
        return questions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_question, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.tvQuestionNumber = convertView.findViewById(R.id.tvQuestionNumber);
            viewHolder.tvQuestionText = convertView.findViewById(R.id.tvQuestionText);
            viewHolder.rgOptions = convertView.findViewById(R.id.rgOptions);
            viewHolder.rbOptionA = convertView.findViewById(R.id.rbOptionA);
            viewHolder.rbOptionB = convertView.findViewById(R.id.rbOptionB);
            viewHolder.rbOptionC = convertView.findViewById(R.id.rbOptionC);
            viewHolder.rbOptionD = convertView.findViewById(R.id.rbOptionD);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        ListeningQuestion question = getItem(position);
        if (viewHolder.tvQuestionNumber != null) {
            String questionNumberStr = (position + 1) + ".";
            viewHolder.tvQuestionNumber.setText(questionNumberStr);
        }

        // --- Set Nội dung câu hỏi ---
        if (viewHolder.tvQuestionText != null) {
            viewHolder.tvQuestionText.setText(question.getQuestionText());
        }

        // --- Reset trạng thái view tái sử dụng ---
        viewHolder.rgOptions.setOnCheckedChangeListener(null); // Gỡ listener cũ
        viewHolder.rgOptions.clearCheck();                   // Xóa lựa chọn cũ

        // --- Cấu hình các RadioButton (lựa chọn A, B, C, D...) ---
        Map<String, String> options = question.getOptions();
        configureRadioButton(viewHolder.rbOptionA, "A", options.get("A"), position, viewHolder.rgOptions);
        configureRadioButton(viewHolder.rbOptionB, "B", options.get("B"), position, viewHolder.rgOptions);
        configureRadioButton(viewHolder.rbOptionC, "C", options.get("C"), position, viewHolder.rgOptions);
        configureRadioButton(viewHolder.rbOptionD, "D", options.get("D"), position, viewHolder.rgOptions);

        // --- Gắn lại listener cho RadioGroup ---
        viewHolder.rgOptions.setOnCheckedChangeListener((group, checkedId) -> {
            if (!answersDisabled && !resultsShown) {
                Log.v(TAG, "Position " + position + " selection changed to ID: " + checkedId);
                selectedAnswers.put(position, checkedId); // Lưu lựa chọn hiện tại
            } else {
                Integer submittedId = submittedAnswers.get(position);
                if (submittedId != null && submittedId != -1 && checkedId != submittedId) {
                    Log.v(TAG, "Result shown. Reverting selection for position " + position + " back to " + submittedId);
                    group.check(submittedId); // Chọn lại câu đã submit
                }
            }
        });

        Integer currentSelectionId = selectedAnswers.get(position);
        Integer submittedId = submittedAnswers.get(position);

        if (resultsShown) {
            Boolean isCorrect = correctnessMap.get(position);
            if (submittedId != null && submittedId != -1) {
                RadioButton submittedRadioButton = convertView.findViewById(submittedId);
                if (submittedRadioButton != null) {
                    submittedRadioButton.setChecked(true);
                    if (isCorrect != null) {
                        int iconRes = isCorrect ? R.drawable.ic_correct_green : R.drawable.ic_incorrect_red;
                        submittedRadioButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconRes, 0);
                    } else {
                        Log.w(TAG, "Correctness info missing for submitted answer at position " + position);
                    }
                } else {
                    Log.w(TAG, "Submitted RadioButton ID " + submittedId + " not found in layout for position " + position);
                }
            } else {
                Log.w(TAG, "No submitted answer recorded for position " + position + ", cannot show icon.");
            }
            setRadioGroupEnabled(viewHolder.rgOptions, false);

        } else {
            if (currentSelectionId != null && currentSelectionId != -1) {
                viewHolder.rgOptions.check(currentSelectionId);
            } else {
                viewHolder.rgOptions.clearCheck();
            }
            setRadioGroupEnabled(viewHolder.rgOptions, !answersDisabled);
        }

        return convertView;
    }

    /**
     * Helper method để cấu hình một RadioButton: reset icon, màu chữ, đặt text và visibility.
     */
    private void configureRadioButton(RadioButton rb, String key, String text, int position, RadioGroup rg) {
        if (rb == null) return;
        rb.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0); // Xóa icon cũ
        rb.setTextColor(ContextCompat.getColor(context, android.R.color.primary_text_light));
        if (text != null && !text.isEmpty()) {
            rb.setText(text);
            rb.setVisibility(View.VISIBLE);
        } else {
            rb.setText("");
            rb.setVisibility(View.GONE);
        }
    }

    /**
     * Helper method để kích hoạt hoặc vô hiệu hóa tất cả RadioButton trong một RadioGroup.
     */
    private void setRadioGroupEnabled(RadioGroup group, boolean enabled) {
        if (group == null) return; // Kiểm tra null an toàn
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);
            if (view instanceof RadioButton) {
                view.setEnabled(enabled);
                view.setAlpha(enabled ? 1.0f : 0.6f);
            }
        }
    }

    /**
     * ViewHolder pattern để lưu trữ các tham chiếu view, tối ưu hiệu năng ListView.
     */
    static class ViewHolder {
        TextView tvQuestionNumber; // Hiển thị số thứ tự câu hỏi
        TextView tvQuestionText;   // Hiển thị nội dung câu hỏi
        RadioGroup rgOptions;      // Nhóm các lựa chọn
        RadioButton rbOptionA;     // Lựa chọn A
        RadioButton rbOptionB;     // Lựa chọn B
        RadioButton rbOptionC;     // Lựa chọn C
        RadioButton rbOptionD;     // Lựa chọn D
    }

    /**
     * Reset trạng thái của bài kiểm tra về ban đầu (dùng cho chức năng Retry).
     * Xóa các lựa chọn đã lưu, kết quả, và kích hoạt lại các RadioButton.
     */
    public void resetQuizState() {
        Log.d(TAG, "Resetting quiz state in adapter.");
        this.selectedAnswers.clear();
        this.submittedAnswers.clear();
        this.correctnessMap.clear();
        this.resultsShown = false;
        this.answersDisabled = false;
        int count = getCount();
        for (int i = 0; i < count; i++) {
            selectedAnswers.put(i, -1);
        }
        notifyDataSetChanged();
        Log.d(TAG,"Adapter state reset completed. Requesting redraw.");
    }

    public boolean areAllAnswersCorrect() {
        if (correctnessMap == null || correctnessMap.isEmpty()) {
            Log.d("Adapter", "Cannot check correctness, map is null or empty.");
            return false;
        }
        for (Boolean isCorrect : correctnessMap.values()) {
            if (!isCorrect) {
                Log.d("Adapter", "Found incorrect answer.");
                return false;
            }
        }
        Log.d("Adapter", "All answers are correct.");
        return true;
    }
}

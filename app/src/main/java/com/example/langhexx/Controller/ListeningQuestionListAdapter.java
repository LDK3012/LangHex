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
    // Map<QuestionIndex, SelectedRadioButtonId> - Theo dõi lựa chọn hiện tại của người dùng
    private Map<Integer, Integer> selectedAnswers = new HashMap<>();
    // Map<QuestionIndex, UserSelectedRadioButtonId> - Lưu lựa chọn của người dùng tại thời điểm submit
    private Map<Integer, Integer> submittedAnswers = new HashMap<>();
    // Map<QuestionIndex, IsCorrectBoolean> - Lưu kết quả đúng/sai sau khi submit
    private Map<Integer, Boolean> correctnessMap = new HashMap<>();
    // Cờ cho biết có đang hiển thị kết quả hay không
    private boolean resultsShown = false;
    // Cờ cho biết các lựa chọn có bị vô hiệu hóa hay không (thường đồng bộ với resultsShown)
    private boolean answersDisabled = false;

    public ListeningQuestionListAdapter(Context context, List<ListeningQuestion> questions) {
        this.context = context;
        this.questions = questions;
        this.inflater = LayoutInflater.from(context);
        // Khởi tạo map lựa chọn ban đầu là chưa chọn (-1)
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
        // Reset hoàn toàn trạng thái khi có dữ liệu mới
        this.selectedAnswers.clear();
        this.submittedAnswers.clear();
        this.correctnessMap.clear();
        this.resultsShown = false;
        this.answersDisabled = false;
        // Khởi tạo lại map lựa chọn ban đầu
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
        this.submittedAnswers = new HashMap<>(userAnswers); // Lưu lại lựa chọn đã submit
        this.correctnessMap = new HashMap<>(correctness); // Lưu lại kết quả đúng/sai
        this.resultsShown = true;  // Đặt cờ hiển thị kết quả
        this.answersDisabled = true; // Vô hiệu hóa tương tác khi xem kết quả
        Log.d(TAG, "Showing results. User answers count: " + submittedAnswers.size() + ", Correctness count: " + correctnessMap.size());
        notifyDataSetChanged();
    }

    /**
     * Trả về Map chứa các lựa chọn hiện tại của người dùng (trước khi submit).
     * @return Map<QuestionIndex, SelectedRadioButtonId>.
     */
    public Map<Integer, Integer> getSelectedAnswers() {
        // Trả về bản sao để tránh thay đổi ngoài ý muốn
        return new HashMap<>(selectedAnswers);
    }

    /**
     * (Tùy chọn) Cho phép vô hiệu hóa các lựa chọn từ bên ngoài.
     * @param disabled True để vô hiệu hóa, False để kích hoạt.
     */
    public void setAnswersDisabled(boolean disabled) {
        this.answersDisabled = disabled;
        // Cân nhắc gọi notifyDataSetChanged() nếu phương thức này được dùng độc lập
        // notifyDataSetChanged();
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
        // Gọi helper để cấu hình từng RadioButton (reset icon, đặt text, visibility)
        configureRadioButton(viewHolder.rbOptionA, "A", options.get("A"), position, viewHolder.rgOptions);
        configureRadioButton(viewHolder.rbOptionB, "B", options.get("B"), position, viewHolder.rgOptions);
        configureRadioButton(viewHolder.rbOptionC, "C", options.get("C"), position, viewHolder.rgOptions);
        configureRadioButton(viewHolder.rbOptionD, "D", options.get("D"), position, viewHolder.rgOptions);
        // (Gọi configure cho E, F... nếu có)

        // --- Gắn lại listener cho RadioGroup ---
        viewHolder.rgOptions.setOnCheckedChangeListener((group, checkedId) -> {
            // Chỉ lưu lựa chọn nếu không đang hiển thị kết quả và không bị vô hiệu hóa
            if (!answersDisabled && !resultsShown) {
                Log.v(TAG, "Position " + position + " selection changed to ID: " + checkedId);
                selectedAnswers.put(position, checkedId); // Lưu lựa chọn hiện tại
            } else {
                // Nếu đang xem kết quả, ngăn người dùng thay đổi lựa chọn
                // bằng cách chọn lại câu trả lời đã submit
                Integer submittedId = submittedAnswers.get(position);
                if (submittedId != null && submittedId != -1 && checkedId != submittedId) {
                    Log.v(TAG, "Result shown. Reverting selection for position " + position + " back to " + submittedId);
                    group.check(submittedId); // Chọn lại câu đã submit
                }
            }
        });

        // --- Khôi phục trạng thái check HOẶC Hiển thị kết quả ---
        Integer currentSelectionId = selectedAnswers.get(position); // Lựa chọn hiện tại (có thể là -1)
        Integer submittedId = submittedAnswers.get(position);       // Lựa chọn đã submit (có thể là null)

        if (resultsShown) {
            // --- HIỂN THỊ KẾT QUẢ (SAU KHI SUBMIT) ---
            Boolean isCorrect = correctnessMap.get(position); // Lấy kết quả đúng/sai

            // Chỉ hiển thị icon nếu người dùng có submit câu trả lời cho câu này
            if (submittedId != null && submittedId != -1) {
                RadioButton submittedRadioButton = convertView.findViewById(submittedId);
                if (submittedRadioButton != null) {
                    submittedRadioButton.setChecked(true); // Đảm bảo nó được check
                    // Kiểm tra xem có thông tin đúng/sai không
                    if (isCorrect != null) {
                        // Chọn icon tương ứng (cần tạo drawable ic_correct_green và ic_incorrect_red)
                        int iconRes = isCorrect ? R.drawable.ic_correct_green : R.drawable.ic_incorrect_red;
                        // Đặt icon vào cuối RadioButton
                        submittedRadioButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconRes, 0);
                    } else {
                        // Log lỗi nếu thiếu thông tin đúng/sai
                        Log.w(TAG, "Correctness info missing for submitted answer at position " + position);
                    }
                } else {
                    // Log lỗi nếu không tìm thấy RadioButton tương ứng với ID đã submit
                    Log.w(TAG, "Submitted RadioButton ID " + submittedId + " not found in layout for position " + position);
                }
            } else {
                // Log nếu người dùng không submit câu trả lời cho câu này (dù trường hợp này ít xảy ra nếu đã check allAnswered)
                Log.w(TAG, "No submitted answer recorded for position " + position + ", cannot show icon.");
                // Optional: Có thể làm nổi bật câu trả lời đúng ở đây nếu muốn
            }
            // Luôn vô hiệu hóa các lựa chọn khi đang hiển thị kết quả
            setRadioGroupEnabled(viewHolder.rgOptions, false);

        } else {
            // --- TRẠNG THÁI BÌNH THƯỜNG (TRƯỚC KHI SUBMIT HOẶC SAU KHI RETRY) ---
            // Khôi phục lựa chọn của người dùng nếu có (khi view được tái sử dụng)
            if (currentSelectionId != null && currentSelectionId != -1) {
                viewHolder.rgOptions.check(currentSelectionId);
            } else {
                viewHolder.rgOptions.clearCheck(); // Đảm bảo không có gì được check nếu là -1
            }
            // Kích hoạt hoặc vô hiệu hóa các lựa chọn dựa trên cờ answersDisabled
            // (answersDisabled sẽ là false sau khi reset)
            setRadioGroupEnabled(viewHolder.rgOptions, !answersDisabled);
        }

        return convertView; // Trả về view đã được cấu hình
    }

    /**
     * Helper method để cấu hình một RadioButton: reset icon, màu chữ, đặt text và visibility.
     */
    private void configureRadioButton(RadioButton rb, String key, String text, int position, RadioGroup rg) {
        if (rb == null) return; // Kiểm tra null an toàn
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
        // Xóa dữ liệu trạng thái
        this.selectedAnswers.clear();
        this.submittedAnswers.clear();
        this.correctnessMap.clear();

        // Đặt lại các cờ
        this.resultsShown = false;
        this.answersDisabled = false;

        // Khởi tạo lại selectedAnswers về trạng thái chưa chọn (-1) cho tất cả câu hỏi
        int count = getCount(); // Lấy số lượng câu hỏi hiện tại
        for (int i = 0; i < count; i++) {
            selectedAnswers.put(i, -1);
        }

        // Yêu cầu ListView vẽ lại giao diện với trạng thái đã reset
        notifyDataSetChanged();
        Log.d(TAG,"Adapter state reset completed. Requesting redraw.");
    }

    public boolean areAllAnswersCorrect() {
        if (correctnessMap == null || correctnessMap.isEmpty()) {
            Log.d("Adapter", "Cannot check correctness, map is null or empty.");
            return false; // Chưa submit hoặc không có câu hỏi
        }
        for (Boolean isCorrect : correctnessMap.values()) {
            if (!isCorrect) {
                Log.d("Adapter", "Found incorrect answer.");
                return false; // Tìm thấy ít nhất 1 câu sai
            }
        }
        Log.d("Adapter", "All answers are correct.");
        return true; // Tất cả đều đúng
    }
}

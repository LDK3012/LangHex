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
import com.example.langhexx.Model.ReadingQuestion; // Use ReadingQuestion
import com.example.langhexx.R;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReadingQuestionListAdapter extends BaseAdapter {

    private static final String TAG = "ReadQuestionAdapter";
    private Context context;
    private List<ReadingQuestion> questions; // Use ReadingQuestion
    private LayoutInflater inflater;

    // Listener for answer selection changes
    private OnAnswerSelectedListener answerSelectedListener;

    public interface OnAnswerSelectedListener {
        void onAnswerSelected(int questionIndex, int selectedOptionId);
    }

    // State variables
    private Map<Integer, Integer> selectedAnswers = new HashMap<>(); // Current UI selections
    private Map<Integer, Integer> submittedAnswers = new HashMap<>(); // Answers at the time of submission
    private Map<Integer, Boolean> correctnessMap = new HashMap<>();
    private boolean resultsShown = false;
    private boolean answersDisabled = false; // Control if interaction is allowed

    // Constructor updated to accept listener
    public ReadingQuestionListAdapter(Context context, List<ReadingQuestion> questions, OnAnswerSelectedListener listener) {
        this.context = context;
        this.questions = new ArrayList<>(questions); // Use copy
        this.inflater = LayoutInflater.from(context);
        this.answerSelectedListener = listener; // Store listener
        initializeSelections(); // Initialize based on potentially saved state in model
    }

    // Initialize selections based on initialSelectedOptionId from the model
    private void initializeSelections() {
        this.selectedAnswers.clear();
        for (int i = 0; i < this.questions.size(); i++) {
            ReadingQuestion q = this.questions.get(i);
            if (q != null && q.getInitialSelectedOptionId() != -1) {
                this.selectedAnswers.put(i, q.getInitialSelectedOptionId());
                Log.v(TAG, "Initializing selection for Q" + i + " from model: " + q.getInitialSelectedOptionId());
            } else {
                this.selectedAnswers.put(i, -1); // Default to no selection
            }
        }
    }

    // Update data and reset state, re-initializing selections from the new model data
    public void updateData(List<ReadingQuestion> newQuestions) {
        Log.d(TAG, "updateData called.");
        this.questions.clear();
        if (newQuestions != null) {
            this.questions.addAll(newQuestions);
        }
        resetInternalState(); // Reset maps and flags
        initializeSelections(); // Re-initialize selections based on potentially updated models
        notifyDataSetChanged();
        Log.d(TAG, "Data updated. Count: " + getCount());
    }

    // Resets internal state maps and flags
    private void resetInternalState() {
        this.selectedAnswers.clear();
        this.submittedAnswers.clear();
        this.correctnessMap.clear();
        this.resultsShown = false;
        this.answersDisabled = false;
    }

    // Show results provided by the controller
    public void showResults(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctness) {
        Log.d(TAG, "showResults called.");
        this.submittedAnswers = new HashMap<>(userAnswers); // Store the submitted answers
        this.correctnessMap = new HashMap<>(correctness);
        this.selectedAnswers = new HashMap<>(userAnswers); // Align current view state with submitted
        this.resultsShown = true;
        this.answersDisabled = true; // Disable interaction after results
        Log.d(TAG, "Showing results. Submitted answers count: " + submittedAnswers.size() + ", Correctness count: " + correctnessMap.size());
        notifyDataSetChanged();
    }

    // Get the current selections made by the user (before submission)
    public Map<Integer, Integer> getSelectedAnswers() {
        return new HashMap<>(selectedAnswers);
    }

    // Allow external control over disabling answers (might not be needed if only disabled on showResults)
    public void setAnswersDisabled(boolean disabled) {
        if (this.answersDisabled != disabled) {
            this.answersDisabled = disabled;
            notifyDataSetChanged();
        }
    }

    // Reset state for retrying the quiz
    public void resetQuizState() {
        Log.d(TAG, "Resetting quiz state in adapter.");
        resetInternalState(); // Clear results, flags
        initializeSelections(); // Reload initial/saved state from model
        notifyDataSetChanged();
        Log.d(TAG, "Adapter state reset completed.");
    }

    // Check if all submitted answers were correct
    public boolean areAllAnswersCorrect() {
        if (!resultsShown || correctnessMap == null || correctnessMap.isEmpty() || correctnessMap.size() != getCount()) {
            Log.d(TAG, "Cannot check correctness: Results not shown, map invalid/incomplete, or size mismatch.");
            return false;
        }
        for (int i = 0; i < getCount(); i++) {
            Boolean isCorrect = correctnessMap.get(i);
            if (isCorrect == null || !isCorrect) {
                Log.d(TAG, "Found incorrect or missing correctness info at index " + i);
                return false;
            }
        }
        Log.d(TAG, "All answers verified as correct.");
        return true;
    }


    @Override
    public int getCount() {
        return questions.size();
    }

    @Override
    public ReadingQuestion getItem(int position) {
        if (position >= 0 && position < questions.size()) {
            return questions.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;

        if (convertView == null) {
            // Use your specific layout for reading questions
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

        ReadingQuestion question = getItem(position);
        if (question == null) {
            Log.e(TAG, "Question at position " + position + " is null in getView!");
            return convertView != null ? convertView : new View(context); // Return existing or empty view
        }

        viewHolder.tvQuestionNumber.setText((position + 1) + ".");
        viewHolder.tvQuestionText.setText(question.getQuestionText());

        viewHolder.rgOptions.setOnCheckedChangeListener(null); // Detach listener

        Map<String, String> options = question.getOptions();
        if (options == null) options = new HashMap<>(); // Safety check

        configureRadioButton(viewHolder.rbOptionA, "A", options.get("A"));
        configureRadioButton(viewHolder.rbOptionB, "B", options.get("B"));
        configureRadioButton(viewHolder.rbOptionC, "C", options.get("C"));
        configureRadioButton(viewHolder.rbOptionD, "D", options.get("D"));

        // Determine which RadioButton should be checked
        int checkedIdToSet = -1;
        if (resultsShown && submittedAnswers.containsKey(position)) {
            checkedIdToSet = submittedAnswers.getOrDefault(position, -1);
        } else if (selectedAnswers.containsKey(position)) {
            // Use current selection if results not shown
            checkedIdToSet = selectedAnswers.getOrDefault(position, -1);
        } else {
            // If somehow not in selectedAnswers map (e.g., after reset/update issue), check model
            checkedIdToSet = question.getInitialSelectedOptionId();
        }


        // Set the checked state visually
        if (checkedIdToSet != -1) {
            try {
                viewHolder.rgOptions.check(checkedIdToSet);
                Log.v(TAG, "getView Q" + position + ": Setting check to ID " + checkedIdToSet + " (resultsShown=" + resultsShown + ")");
            } catch (Exception e) { // Catch potential errors during check
                Log.e(TAG, "getView Q" + position + ": Error checking ID " + checkedIdToSet, e);
                viewHolder.rgOptions.clearCheck(); // Clear if error
            }
        } else {
            viewHolder.rgOptions.clearCheck();
            Log.v(TAG, "getView Q" + position + ": Clearing check (resultsShown=" + resultsShown + ")");
        }

        // Re-attach listener after setting initial state
        viewHolder.rgOptions.setOnCheckedChangeListener((group, checkedId) -> {
            if (!answersDisabled) { // Only process if interaction is allowed
                Log.v(TAG, "Position " + position + " selection changed via UI to ID: " + checkedId);
                selectedAnswers.put(position, checkedId); // Update internal map
                if (answerSelectedListener != null) {
                    // Notify controller to save
                    answerSelectedListener.onAnswerSelected(position, checkedId);
                }
            } else {
                // If disabled, try to revert the change visually
                int revertId = submittedAnswers.getOrDefault(position, -1);
                Log.d(TAG, "Interaction disabled for Q" + position + ". Reverting check to: " + revertId);
                group.post(() -> { // Use post to avoid issues during layout
                    if (group.getCheckedRadioButtonId() != revertId) {
                        if (revertId != -1) {
                            try { group.check(revertId); } catch (Exception e) { Log.e(TAG, "Error reverting check", e); group.clearCheck(); }
                        } else {
                            group.clearCheck();
                        }
                    }
                });
            }
        });

        // Set enabled state for the group
        boolean enableInteraction = !answersDisabled;
        setRadioGroupEnabled(viewHolder.rgOptions, enableInteraction);

        // Show correctness icons if results are displayed
        clearRadioButtonIcons(viewHolder); // Clear previous icons first
        if (resultsShown) {
            Boolean isCorrect = correctnessMap.get(position);
            int submittedId = submittedAnswers.getOrDefault(position, -1);

            // Nếu user chọn đúng: chỉ hiện icon xanh ở đáp án đã chọn
            if (isCorrect != null && isCorrect && submittedId != -1) {
                RadioButton submittedRadioButton = convertView.findViewById(submittedId);
                if (submittedRadioButton != null) {
                    submittedRadioButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_correct_green, 0);
                }
            }

            // Nếu user chọn sai
            if (isCorrect != null && !isCorrect) {
                // 1. Hiện icon đỏ ở đáp án user chọn sai
                if (submittedId != -1) {
                    RadioButton submittedRadioButton = convertView.findViewById(submittedId);
                    if (submittedRadioButton != null) {
                        submittedRadioButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_incorrect_red, 0);
                    }
                }
                // 2. Hiện icon xanh (V) ở đáp án đúng
                String correctKey = question.getCorrectAnswer();
                int correctRadioButtonId = -1;
                if ("A".equals(correctKey)) correctRadioButtonId = R.id.rbOptionA;
                if ("B".equals(correctKey)) correctRadioButtonId = R.id.rbOptionB;
                if ("C".equals(correctKey)) correctRadioButtonId = R.id.rbOptionC;
                if ("D".equals(correctKey)) correctRadioButtonId = R.id.rbOptionD;

                if (correctRadioButtonId != -1) {
                    RadioButton correctRadioButton = convertView.findViewById(correctRadioButtonId);
                    if (correctRadioButton != null) {
                        correctRadioButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_correct_green, 0);
                    }
                }
            }
        }
        return convertView;
    }

    // Helper to configure individual RadioButtons
    private void configureRadioButton(RadioButton rb, String key, String text) {
        if (rb == null) return;
        rb.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        rb.setTextColor(ContextCompat.getColor(context, android.R.color.primary_text_light));
        rb.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent));

        if (text != null && !text.isEmpty()) {
            rb.setText(text);
            rb.setVisibility(View.VISIBLE);
        } else {
            rb.setText("");
            rb.setVisibility(View.GONE);
        }
    }

    // Helper to clear icons from all radio buttons in the holder
    private void clearRadioButtonIcons(ViewHolder holder) {
        if (holder.rbOptionA != null) holder.rbOptionA.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        if (holder.rbOptionB != null) holder.rbOptionB.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        if (holder.rbOptionC != null) holder.rbOptionC.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        if (holder.rbOptionD != null) holder.rbOptionD.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }

    // Helper to enable/disable all RadioButtons in a group
    private void setRadioGroupEnabled(RadioGroup group, boolean enabled) {
        if (group == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof RadioButton) {
                child.setEnabled(enabled);
                child.setAlpha(enabled ? 1.0f : 0.6f);
            }
        }
    }

    // ViewHolder pattern
    static class ViewHolder {
        TextView tvQuestionNumber;
        TextView tvQuestionText;
        RadioGroup rgOptions;
        RadioButton rbOptionA;
        RadioButton rbOptionB;
        RadioButton rbOptionC;
        RadioButton rbOptionD;
    }
}
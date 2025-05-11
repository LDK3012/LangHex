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
import com.example.langhexx.Model.ReadingQuestion;
import com.example.langhexx.R;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class ReadingQuestionListAdapter extends BaseAdapter {

    private static final String TAG = "ReadQuestionAdapter";
    private Context context;
    private List<ReadingQuestion> questions;
    private LayoutInflater inflater;
    private Map<Integer, Integer> selectedAnswers = new HashMap<>();
    private Map<Integer, Integer> submittedAnswers = new HashMap<>();
    private Map<Integer, Boolean> correctnessMap = new HashMap<>();
    private boolean resultsShown = false;
    private boolean answersDisabled = false;

    public ReadingQuestionListAdapter(Context context, List<ReadingQuestion> questions) {
        this.context = context;
        this.questions = questions;
        this.inflater = LayoutInflater.from(context);
        initializeSelectedAnswers();
    }

    private void initializeSelectedAnswers() {
        selectedAnswers.clear();
        for (int i = 0; i < questions.size(); i++) {
            selectedAnswers.put(i, -1);
        }
    }

    public void updateData(List<ReadingQuestion> newQuestions) {
        this.questions = newQuestions;
        this.selectedAnswers.clear();
        this.submittedAnswers.clear();
        this.correctnessMap.clear();
        this.resultsShown = false;
        this.answersDisabled = false;
        initializeSelectedAnswers();
        notifyDataSetChanged();
    }

    public void showResults(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctness) {
        this.submittedAnswers = new HashMap<>(userAnswers);
        this.correctnessMap = new HashMap<>(correctness);
        this.resultsShown = true;
        this.answersDisabled = true;
        Log.d(TAG, "Showing results. User answers count: " + submittedAnswers.size() + ", Correctness count: " + correctnessMap.size());
        notifyDataSetChanged();
    }

    /**
     * Returns the current user selections BEFORE submitting.
     * Key: Question Index (0-based)
     * Value: Selected RadioButton ID (e.g., R.id.rbOptionB) or -1 if none selected.
     */
    public Map<Integer, Integer> getSelectedAnswers() {
        return new HashMap<>(selectedAnswers);
    }

    public void setAnswersDisabled(boolean disabled) {
        this.answersDisabled = disabled;
    }

    /**
     * Resets the adapter state for retrying the quiz.
     */
    public void resetQuizState() {
        Log.d(TAG, "Resetting quiz state in adapter.");
        this.selectedAnswers.clear();
        this.submittedAnswers.clear();
        this.correctnessMap.clear();
        this.resultsShown = false;
        this.answersDisabled = false;
        initializeSelectedAnswers();
        notifyDataSetChanged();
        Log.d(TAG, "Adapter state reset completed. Requesting redraw.");
    }

    /**
     * Checks if all submitted answers were correct.
     * Call this only *after* showResults has been called.
     * @return True if all answers in the correctnessMap are true, false otherwise.
     */
    public boolean areAllAnswersCorrect() {
        if (!resultsShown || correctnessMap == null || correctnessMap.isEmpty() || correctnessMap.size() != questions.size()) {
            Log.d(TAG, "Cannot check correctness - results not shown, map empty, or map size mismatch.");
            return false;
        }
        for (Boolean isCorrect : correctnessMap.values()) {
            if (isCorrect == null || !isCorrect) { // Handle null just in case
                Log.d(TAG, "Found incorrect or null answer in correctness map.");
                return false; // Found at least one wrong or missing answer
            }
        }
        Log.d(TAG, "All answers in correctness map are true.");
        return true; // All entries in the map are true
    }


    @Override
    public int getCount() {
        return questions.size();
    }

    @Override
    public ReadingQuestion getItem(int position) { // Use ReadingQuestion
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

        ReadingQuestion question = getItem(position);

        // --- Set Question Number ---
        viewHolder.tvQuestionNumber.setText((position + 1) + ".");

        // --- Set Question Text ---
        viewHolder.tvQuestionText.setText(question.getQuestionText());

        // --- Reset and Configure RadioGroup/RadioButtons ---
        viewHolder.rgOptions.setOnCheckedChangeListener(null); // Detach listener
        viewHolder.rgOptions.clearCheck();                  // Clear previous check

        Map<String, String> options = question.getOptions();
        configureRadioButton(viewHolder.rbOptionA, "A", options.get("A"), position);
        configureRadioButton(viewHolder.rbOptionB, "B", options.get("B"), position);
        configureRadioButton(viewHolder.rbOptionC, "C", options.get("C"), position);
        configureRadioButton(viewHolder.rbOptionD, "D", options.get("D"), position);


        // --- Re-attach Listener ---
        viewHolder.rgOptions.setOnCheckedChangeListener((group, checkedId) -> {
            // Only update selection if not showing results
            if (!resultsShown && !answersDisabled) {
                Log.v(TAG, "Position " + position + " selection changed to ID: " + checkedId);
                selectedAnswers.put(position, checkedId);
            } else if (resultsShown) {
                // Prevent changing selection when results are shown by re-checking submitted answer
                Integer submittedId = submittedAnswers.get(position);
                if (submittedId != null && submittedId != -1 && checkedId != submittedId) {
                    Log.v(TAG,"Result shown. Reverting selection for position " + position + " back to " + submittedId);
                    group.check(submittedId); // Force check back to the submitted answer
                } else if (submittedId != null && submittedId != -1 && checkedId == submittedId){
                    // If they click the already submitted answer, do nothing extra
                } else {
                    // If no answer was submitted for this question, clear check if they click something
                    group.clearCheck();
                }
            }
        });


        // --- Restore State or Show Results ---
        Integer currentSelectionId = selectedAnswers.get(position);
        Integer submittedId = submittedAnswers.get(position); // Might be null

        if (resultsShown) {
            // --- SHOW RESULTS ---
            Boolean isCorrect = correctnessMap.get(position); // Might be null

            // Disable all options in the group
            setRadioGroupEnabled(viewHolder.rgOptions, false);

            // Check the submitted answer and show icon
            if (submittedId != null && submittedId != -1) {
                RadioButton submittedRadioButton = convertView.findViewById(submittedId);
                if (submittedRadioButton != null) {
                    submittedRadioButton.setChecked(true); // Make sure it's checked visually

                    if (isCorrect != null) {
                        int iconRes = isCorrect ? R.drawable.ic_correct_green : R.drawable.ic_incorrect_red;
                        submittedRadioButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconRes, 0);
                    } else {
                        Log.w(TAG, "Correctness info missing for submitted answer at position " + position);
                        highlightCorrectAnswer(viewHolder, question.getCorrectAnswer());
                    }
                } else {
                    Log.w(TAG, "Submitted RadioButton ID " + submittedId + " not found for position " + position);
                    highlightCorrectAnswer(viewHolder, question.getCorrectAnswer());
                }
            } else {
                Log.w(TAG, "No submitted answer for position " + position + ", cannot show icon.");
                highlightCorrectAnswer(viewHolder, question.getCorrectAnswer());
            }

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
    private void configureRadioButton(RadioButton rb, String key, String text, int position) {
        if (rb == null) return;
        rb.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0); // Clear icons
        rb.setTextColor(ContextCompat.getColor(context, android.R.color.primary_text_light)); // Reset text color

        if (text != null && !text.isEmpty()) {
            rb.setText(text);
            rb.setVisibility(View.VISIBLE);
            rb.setEnabled(!answersDisabled);
            rb.setAlpha(1.0f);
        } else {
            rb.setText("");
            rb.setVisibility(View.GONE);
        }
    }

    private void highlightCorrectAnswer(ViewHolder vh, String correctKey) {
        if (correctKey == null) return;
        RadioButton correctRb = null;
        switch (correctKey) {
            case "A": correctRb = vh.rbOptionA; break;
            case "B": correctRb = vh.rbOptionB; break;
            case "C": correctRb = vh.rbOptionC; break;
            case "D": correctRb = vh.rbOptionD; break;
            // Add more cases if needed
        }
        if (correctRb != null && correctRb.getVisibility() == View.VISIBLE) {
            //
        }
    }

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
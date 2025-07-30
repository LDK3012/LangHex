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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListeningQuestionListAdapter extends BaseAdapter {

    private static final String TAG = "ListenQuestionAdapter";
    private Context context;
    private List<ListeningQuestion> questions;
    private LayoutInflater inflater;

    private OnAnswerSelectedListener answerSelectedListener;

    public interface OnAnswerSelectedListener {
        void onAnswerSelected(int questionIndex, int selectedOptionId);
    }

    private Map<Integer, Integer> selectedAnswers = new HashMap<>();
    private Map<Integer, Integer> submittedAnswers = new HashMap<>();
    private Map<Integer, Boolean> correctnessMap = new HashMap<>();
    private boolean resultsShown = false;
    private boolean answersDisabled = false; // Controls if user can change answers


    public ListeningQuestionListAdapter(Context context, List<ListeningQuestion> questions, OnAnswerSelectedListener listener) {
        this.context = context;
        this.questions = new ArrayList<>(questions); // Use copy
        this.inflater = LayoutInflater.from(context);
        this.answerSelectedListener = listener; // Store the listener
        initializeSelections(); // Initialize selections based on initial data
    }

    private void initializeSelections() {
        this.selectedAnswers.clear();
        for (int i = 0; i < this.questions.size(); i++) {
            ListeningQuestion q = this.questions.get(i);
            if (q != null && q.getInitialSelectedOptionId() != -1) {
                this.selectedAnswers.put(i, q.getInitialSelectedOptionId());
                Log.v(TAG, "Initializing selection for Q" + i + " from model: " + q.getInitialSelectedOptionId());
            } else {
                this.selectedAnswers.put(i, -1); // Default to no selection
            }
        }
    }

    public void updateData(List<ListeningQuestion> newQuestions) {
        Log.d(TAG, "updateData called.");
        this.questions.clear();
        if (newQuestions != null) {
            this.questions.addAll(newQuestions);
        }
        resetInternalState(); // Reset all state maps and flags
        initializeSelections(); // Re-initialize selections based on new data
        notifyDataSetChanged();
        Log.d(TAG, "Data updated. Count: " + getCount());
    }

    private void resetInternalState() {
        this.selectedAnswers.clear();
        this.submittedAnswers.clear();
        this.correctnessMap.clear();
        this.resultsShown = false;
        this.answersDisabled = false;
    }

    public void showResults(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctness) {
        Log.d(TAG, "showResults called.");
        // Use the answers submitted by the user at the time of submission
        this.submittedAnswers = new HashMap<>(userAnswers);
        this.correctnessMap = new HashMap<>(correctness);
        // Update selectedAnswers map to reflect the submitted state for display consistency
        this.selectedAnswers = new HashMap<>(userAnswers);
        this.resultsShown = true;
        this.answersDisabled = true; // Disable changes after showing results
        Log.d(TAG, "Showing results. Submitted answers count: " + submittedAnswers.size() + ", Correctness count: " + correctnessMap.size());
        notifyDataSetChanged();
    }

    public Map<Integer, Integer> getSelectedAnswers() {
        // Return the current selections made by the user (before submission)
        return new HashMap<>(selectedAnswers);
    }

    public void setAnswersDisabled(boolean disabled) {
        if (this.answersDisabled != disabled) {
            this.answersDisabled = disabled;
            notifyDataSetChanged(); // Update UI if state changes
        }
    }

    @Override
    public int getCount() {
        return questions.size();
    }

    @Override
    public ListeningQuestion getItem(int position) {
        if (position >= 0 && position < questions.size()) {
            return questions.get(position);
        }
        return null; // Should not happen with valid position
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;

        if (convertView == null) {
            // Inflate using your specific item layout name
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
        if (question == null) {
            // Handle error case, maybe return an empty or error view
            Log.e(TAG, "Question at position " + position + " is null!");
            // For safety, return the potentially old convertView or a new empty view
            return convertView != null ? convertView : new View(context);
        }


        if (viewHolder.tvQuestionNumber != null) {
            String questionNumberStr = (position + 1) + ".";
            viewHolder.tvQuestionNumber.setText(questionNumberStr);
        }

        if (viewHolder.tvQuestionText != null) {
            viewHolder.tvQuestionText.setText(question.getQuestionText());
        }

        viewHolder.rgOptions.setOnCheckedChangeListener(null);

        Map<String, String> options = question.getOptions();
        if (options == null) options = new HashMap<>(); // Avoid NPE

        configureRadioButton(viewHolder.rbOptionA, "A", options.get("A"));
        configureRadioButton(viewHolder.rbOptionB, "B", options.get("B"));
        configureRadioButton(viewHolder.rbOptionC, "C", options.get("C"));
        configureRadioButton(viewHolder.rbOptionD, "D", options.get("D"));

        // Determine the effective checked ID (submitted > current selection)
        int checkedIdToSet = -1;
        if (resultsShown && submittedAnswers.containsKey(position)) {
            checkedIdToSet = submittedAnswers.getOrDefault(position, -1);
        } else if (selectedAnswers.containsKey(position)) {
            checkedIdToSet = selectedAnswers.getOrDefault(position, -1);
        }

        // Apply the check state
        if (checkedIdToSet != -1) {
            try {
                viewHolder.rgOptions.check(checkedIdToSet);
                Log.v(TAG, "getView Q" + position + ": Setting check to ID " + checkedIdToSet + " (resultsShown=" + resultsShown + ")");
            } catch (IllegalStateException e) {
                Log.e(TAG, "getView Q" + position + ": IllegalStateException trying to check ID " + checkedIdToSet, e);
                // Might happen if ID is invalid or view state is inconsistent
                viewHolder.rgOptions.clearCheck();
            } catch (Exception e) {
                Log.e(TAG, "getView Q" + position + ": Exception checking ID " + checkedIdToSet, e);
                viewHolder.rgOptions.clearCheck();
            }
        } else {
            viewHolder.rgOptions.clearCheck();
            Log.v(TAG, "getView Q" + position + ": Clearing check (resultsShown=" + resultsShown + ")");
        }

        // Set listener after applying the check state
        viewHolder.rgOptions.setOnCheckedChangeListener((group, checkedId) -> {
            // Only process if interaction is allowed
            if (!answersDisabled) {
                Log.v(TAG, "Position " + position + " selection changed via UI to ID: " + checkedId);
                selectedAnswers.put(position, checkedId); // Update internal state
                if (answerSelectedListener != null) {
                    // Notify controller to save progress
                    answerSelectedListener.onAnswerSelected(position, checkedId);
                }
            } else {
                // If disabled (e.g., results shown), try to revert the check
                int revertId = submittedAnswers.getOrDefault(position, -1);
                Log.d(TAG, "Interaction disabled for Q" + position + ". Attempting to revert check to: " + revertId);
                // Use post to avoid issues during layout/measure pass
                group.post(() -> {
                    if (group.getCheckedRadioButtonId() != revertId) {
                        if (revertId != -1) {
                            try {
                                group.check(revertId);
                            } catch (Exception e) {
                                Log.e(TAG, "Error reverting check in post for Q"+position, e);
                                group.clearCheck();
                            }
                        } else {
                            group.clearCheck();
                        }
                    }
                });
            }
        });
        boolean enableInteraction = !answersDisabled;
        setRadioGroupEnabled(viewHolder.rgOptions, enableInteraction);

        if (resultsShown) {
            Boolean isCorrect = correctnessMap.get(position);
            int submittedId = submittedAnswers.getOrDefault(position, -1);
            if (isCorrect != null && isCorrect && submittedId != -1) {
                RadioButton submittedRadioButton = convertView.findViewById(submittedId);
                if (submittedRadioButton != null) {
                    submittedRadioButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_correct_green, 0);
                }
            }
            if (isCorrect != null && !isCorrect) {
                if (submittedId != -1) {
                    RadioButton submittedRadioButton = convertView.findViewById(submittedId);
                    if (submittedRadioButton != null) {
                        submittedRadioButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_incorrect_red, 0);
                    }
                }
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

    private void configureRadioButton(RadioButton rb, String key, String text) {
        if (rb == null) return;
        // Reset visual state that might persist from recycled views
        rb.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        rb.setTextColor(ContextCompat.getColor(context, android.R.color.primary_text_light)); // Use appropriate default color
        rb.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent)); // Ensure default background

        if (text != null && !text.isEmpty()) {
            rb.setText(text);
            rb.setVisibility(View.VISIBLE);
        } else {
            rb.setText("");
            rb.setVisibility(View.GONE);
        }
        // Ensure enabled state matches the group's target state
        // rb.setEnabled(!answersDisabled); // This will be handled by setRadioGroupEnabled
    }

    private void clearRadioButtonIcons(ViewHolder holder) {
        if (holder.rbOptionA != null) holder.rbOptionA.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        if (holder.rbOptionB != null) holder.rbOptionB.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        if (holder.rbOptionC != null) holder.rbOptionC.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        if (holder.rbOptionD != null) holder.rbOptionD.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }

    private void setRadioGroupEnabled(RadioGroup group, boolean enabled) {
        if (group == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);
            if (view instanceof RadioButton) {
                view.setEnabled(enabled);
                view.setAlpha(enabled ? 1.0f : 0.6f); // Visual cue for disabled state
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

    public void resetQuizState() {
        Log.d(TAG, "Resetting quiz state in adapter.");
        resetInternalState(); // Clear maps and flags
        initializeSelections(); // Reload initial/saved selections
        notifyDataSetChanged();
        Log.d(TAG,"Adapter state reset completed.");
    }

    public boolean areAllAnswersCorrect() {
        if (!resultsShown || correctnessMap == null || correctnessMap.isEmpty() || correctnessMap.size() != getCount()) {
            Log.d(TAG, "Cannot check correctness: Results not shown or map invalid/incomplete.");
            return false;
        }
        for (int i = 0; i < getCount(); i++) {
            Boolean isCorrect = correctnessMap.get(i);
            // If any question is marked as incorrect, return false
            if (isCorrect == null || !isCorrect) {
                Log.d(TAG, "Found incorrect or missing correctness info at index " + i);
                return false;
            }
        }
        Log.d(TAG, "All answers verified as correct.");
        return true; // Only return true if all questions are present and marked correct
    }
}
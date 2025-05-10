//package com.example.langhexx.View;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.ListView;
//import android.widget.RadioButton;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AlertDialog;
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.example.langhexx.Controller.ReadingQuestionListAdapter;
//import com.example.langhexx.Model.CustomToast;
//import com.example.langhexx.Model.ReadingQuestion;
//import com.example.langhexx.R; // Adjust R if needed
//
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Locale;
//import java.util.Map;
//
//public class InternalReadingTopic extends AppCompatActivity {
//
//    private static final String TAG = "InternalReadTopic";
//
//    // --- UI Elements from your XML ---
//    private ListView lvQuestions;
//    private Button btnSubmit;
//    private TextView tvExerciseDisplayTitle; // For txtTitle ID
//    private TextView tvPassageDisplay;      // For txtParagraph ID
//
//    // --- Data Members ---
//    private List<ReadingQuestion> questionsList;
//    private ReadingQuestionListAdapter questionListAdapter;
//    private String levelName;
//    private String topicTitle;
//    private String exerciseTitle;
//    private String passageText;
//    private ArrayList<String> allExerciseTitles;
//
//    // --- Button States (Reintroduced Retry) ---
//    private static final int STATE_SUBMIT = 0;
//    private static final int STATE_RETRY = 1; // *** THÊM LẠI STATE_RETRY ***
//    private static final int STATE_NEXT = 2;
//    private static final int STATE_FINISHED = -1;
//    private int currentButtonState = STATE_SUBMIT;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_internal_reading_topic);
//
//        Intent intent = getIntent();
//        if (intent != null) {
//            levelName = intent.getStringExtra("LEVEL_NAME");
//            topicTitle = intent.getStringExtra("TOPIC_TITLE");
//            exerciseTitle = intent.getStringExtra("EXERCISE_TITLE");
//        } else {
//            handleMissingIntentData("Error: Missing exercise identifiers.");
//            return;
//        }
//        if (levelName == null || topicTitle == null || exerciseTitle == null) {
//            handleMissingIntentData("Error: Invalid exercise identifiers.");
//            return;
//        }
//
//        addControls();
//
//        if (tvExerciseDisplayTitle != null && exerciseTitle != null) {
//            tvExerciseDisplayTitle.setText(exerciseTitle);
//        }
//
//        setupInitialUI();
//        setupListView();
//        loadExerciseDataFromFirebase();
//        loadAllExerciseTitlesFromFirebase();
//        addEvents();
//    }
//
//    private void handleMissingIntentData(String errorMessage) {
//        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
//        Log.e(TAG, errorMessage + " level=" + levelName + ", topic=" + topicTitle + ", exercise=" + exerciseTitle);
//        finish();
//    }
//
//
//    private void addControls() {
//        lvQuestions = findViewById(R.id.lvQuestions);
//        btnSubmit = findViewById(R.id.btnSubmit);
//        tvExerciseDisplayTitle = findViewById(R.id.txtTitle);
//        tvPassageDisplay = findViewById(R.id.txtParagraph);
//        resetContentUI();
//    }
//
//    private void setupInitialUI() {
//        if (tvExerciseDisplayTitle != null) tvExerciseDisplayTitle.setVisibility(View.INVISIBLE);
//        if (tvPassageDisplay != null) tvPassageDisplay.setVisibility(View.INVISIBLE);
//        if (lvQuestions != null) lvQuestions.setVisibility(View.INVISIBLE);
//        if (btnSubmit != null) btnSubmit.setVisibility(View.INVISIBLE);
//        TextView instructionText = findViewById(R.id.textView16);
//        if(instructionText != null) instructionText.setVisibility(View.INVISIBLE);
//    }
//
//    private void setupListView() {
//        questionsList = new ArrayList<>();
//        questionListAdapter = new ReadingQuestionListAdapter(this, questionsList);
//        lvQuestions.setAdapter(questionListAdapter);
//    }
//
//    private void loadExerciseDataFromFirebase() {
//        DatabaseReference exerciseRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
//                .getReference("Lessons").child("Levels").child(levelName)
//                .child("Reading").child("Topics").child(topicTitle).child("Exercises").child(exerciseTitle);
//
//        Log.i(TAG, "Loading data from: " + exerciseRef.toString());
//
//        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (!snapshot.exists()) {
//                    Log.e(TAG, "Exercise data not found.");
//                    Toast.makeText(InternalReadingTopic.this, "Exercise data not found.", Toast.LENGTH_SHORT).show();
//                    finish();
//                    return;
//                }
//
//                // Load script
//                passageText = snapshot.child("script").getValue(String.class);
//                boolean hasContent = passageText != null && !passageText.isEmpty();
//                if (hasContent) {
//                    if (tvPassageDisplay != null) {
//                        tvPassageDisplay.setText(passageText);
//                        tvPassageDisplay.setVisibility(View.VISIBLE);
//                        Log.d(TAG, "Script loaded.");
//                    }
//                } else {
//                    Log.w(TAG, "'script' field missing.");
//                    Toast.makeText(InternalReadingTopic.this, "Content (script) not found.", Toast.LENGTH_SHORT).show();
//                    if (tvPassageDisplay != null) {
//                        tvPassageDisplay.setText("Content not available.");
//                        tvPassageDisplay.setVisibility(View.VISIBLE);
//                    }
//                }
//                if (tvExerciseDisplayTitle != null) tvExerciseDisplayTitle.setVisibility(View.VISIBLE);
//
//                // Load questions
//                DataSnapshot questionsSnapshot = snapshot.child("questions");
//                if (!questionsSnapshot.exists()) {
//                    Log.w(TAG, "No 'questions' node found.");
//                    if (questionsList.isEmpty()) Toast.makeText(InternalReadingTopic.this, "No questions found.", Toast.LENGTH_SHORT).show();
//                    TextView instructionText = findViewById(R.id.textView16);
//                    if(instructionText != null) instructionText.setVisibility(View.GONE);
//                    if(btnSubmit != null) btnSubmit.setVisibility(View.GONE);
//                } else {
//                    List<ReadingQuestion> loadedQuestions = new ArrayList<>();
//                    for (DataSnapshot questionSnap : questionsSnapshot.getChildren()) {
//                        try {
//                            ReadingQuestion question = parseReadingQuestionSnapshot(questionSnap);
//                            if (question != null) loadedQuestions.add(question);
//                        } catch (Exception e) {
//                            Log.e(TAG, "Error parsing question: " + questionSnap.getKey(), e);
//                        }
//                    }
//                    questionsList.clear();
//                    questionsList.addAll(loadedQuestions);
//                    questionListAdapter.updateData(questionsList);
//                    Log.i(TAG, "Loaded " + questionsList.size() + " questions.");
//
//                    if (!questionsList.isEmpty()) {
//                        if (lvQuestions != null) lvQuestions.setVisibility(View.VISIBLE);
//                        TextView instructionText = findViewById(R.id.textView16);
//                        if(instructionText != null) instructionText.setVisibility(View.VISIBLE);
//                        if (btnSubmit != null) btnSubmit.setVisibility(View.VISIBLE);
//                    } else {
//                        TextView instructionText = findViewById(R.id.textView16);
//                        if(instructionText != null) instructionText.setVisibility(View.GONE);
//                        if (btnSubmit != null) btnSubmit.setVisibility(View.GONE);
//                    }
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e(TAG, "Firebase loading cancelled/failed: " + error.getMessage(), error.toException());
//                Toast.makeText(InternalReadingTopic.this, "Error loading data: " + error.getMessage(), Toast.LENGTH_LONG).show();
//                finish();
//            }
//        });
//    }
//
//    private ReadingQuestion parseReadingQuestionSnapshot(DataSnapshot questionSnap) {
//        ReadingQuestion question = new ReadingQuestion();
//        String questionIdFromDB = questionSnap.child("id").getValue(String.class);
//        question.setId(questionIdFromDB != null ? questionIdFromDB : questionSnap.getKey());
//        String text = questionSnap.child("questionText").getValue(String.class);
//        String answer = questionSnap.child("correctAnswer").getValue(String.class);
//        Map<String, String> stringOptionsMap = new HashMap<>();
//        DataSnapshot optionsSnapshot = questionSnap.child("options");
//        if (optionsSnapshot.exists() && optionsSnapshot.getValue() instanceof Map) {
//            try {
//                @SuppressWarnings("unchecked")
//                Map<String, Object> optionsMapObject = (Map<String, Object>) optionsSnapshot.getValue();
//                if (optionsMapObject != null) {
//                    for (Map.Entry<String, Object> entry : optionsMapObject.entrySet()) {
//                        stringOptionsMap.put(entry.getKey(), String.valueOf(entry.getValue()));
//                    }
//                }
//            } catch (ClassCastException e) {
//                Log.e(TAG, "Error casting options: " + questionSnap.getKey(), e);
//            }
//        } else {
//            Log.w(TAG, "'options' node missing/invalid: " + questionSnap.getKey());
//        }
//        question.setOptions(stringOptionsMap);
//        if (text != null && !text.isEmpty() && answer != null && !answer.isEmpty() && !stringOptionsMap.isEmpty()) {
//            question.setQuestionText(text);
//            question.setCorrectAnswer(answer);
//            return question;
//        }
//        Log.w(TAG, "Skipping invalid question data: Key=" + questionSnap.getKey());
//        return null;
//    }
//
//    private void addEvents() {
//        if (btnSubmit != null) {
//            btnSubmit.setOnClickListener(v -> {
//                Log.d(TAG, "Submit button clicked. Current state: " + currentButtonState);
//                switch (currentButtonState) {
//                    case STATE_SUBMIT:
//                        checkAnswersAndShowConfirmationIfNeeded();
//                        break;
//                    // *** THÊM LẠI CASE RETRY ***
//                    case STATE_RETRY:
//                        retryExercise(); // Gọi hàm làm lại bài
//                        break;
//                    case STATE_NEXT:
//                        goToNextExercise();
//                        break;
//                    case STATE_FINISHED:
//                        Log.i(TAG,"'Finish ! Back Now' button clicked. Finishing activity.");
//                        finish();
//                        break;
//                    default:
//                        Log.w(TAG, "Unknown button state clicked: " + currentButtonState);
//                        break;
//                }
//            });
//        }
//    }
//
//    private void checkAnswersAndShowConfirmationIfNeeded() {
//        Log.d(TAG, "Checking answers...");
//        if (questionListAdapter == null || questionsList == null) {
//            Log.e(TAG, "Cannot check answers: Adapter or list null.");
//            Toast.makeText(this, "Error preparing submission.", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        Map<Integer, Integer> userAnswers = questionListAdapter.getSelectedAnswers();
//        int totalQuestions = questionsList.size();
//        boolean allAnswered = true;
//        int firstUnanswered = -1;
//        if (totalQuestions == 0) {
//            Log.w(TAG, "Check: No questions to answer.");
//            return;
//        }
//        for (int i = 0; i < totalQuestions; i++) {
//            if (userAnswers.getOrDefault(i, -1) == -1) {
//                allAnswered = false;
//                firstUnanswered = i;
//                break;
//            }
//        }
//        if (allAnswered) {
//            Log.d(TAG, "Check: All answered. Showing confirmation.");
//            showSubmissionConfirmationDialog();
//        } else {
//            Log.d(TAG, "Check: Not all answered.");
//            CustomToast.showFail(this, "Please answer all questions!", R.drawable.fail_icon);
//            if (lvQuestions != null && firstUnanswered != -1) {
//                int finalFirstUnanswered = firstUnanswered;
//                lvQuestions.post(() -> lvQuestions.smoothScrollToPosition(finalFirstUnanswered));
//                Log.d(TAG,"Check: Scrolling to index: " + firstUnanswered);
//            }
//        }
//    }
//
//    private void showSubmissionConfirmationDialog() {
//        new AlertDialog.Builder(this)
//                .setTitle("Xác nhận nộp bài")
//                .setMessage("Bạn có chắc chắn muốn nộp bài không?")
//                .setPositiveButton("Submit", (dialog, which) -> {
//                    Log.d(TAG, "Submission confirmed.");
//                    proceedWithSubmission();
//                })
//                .setNegativeButton("Cancel", (dialog, which) -> Log.d(TAG, "Submission cancelled."))
//                .setCancelable(false)
//                .show();
//    }
//
//    private void proceedWithSubmission() {
//        Log.i(TAG, "Proceeding with submission...");
//        if (questionListAdapter == null || questionsList == null || questionsList.isEmpty()) {
//            Log.e(TAG, "Cannot submit: Adapter or list invalid.");
//            Toast.makeText(this, "Error processing submission.", Toast.LENGTH_SHORT).show();
//            resetSubmitButtonToSubmitState();
//            return;
//        }
//
//        Map<Integer, Integer> userAnswers = questionListAdapter.getSelectedAnswers();
//        int totalQuestions = questionsList.size();
//        Log.d(TAG, "Scoring " + totalQuestions + " questions.");
//        int correctCount = 0;
//        Map<Integer, Boolean> correctnessMap = new HashMap<>();
//
//        for (int i = 0; i < totalQuestions; i++) {
//            if (i >= questionsList.size()) continue;
//            ReadingQuestion question = questionsList.get(i);
//            int selectedRadioButtonId = userAnswers.get(i);
//            String selectedAnswerKey = getSelectedAnswerKey(selectedRadioButtonId);
//            boolean isCorrect = question.getCorrectAnswer() != null &&
//                    !selectedAnswerKey.isEmpty() &&
//                    selectedAnswerKey.equalsIgnoreCase(question.getCorrectAnswer());
//            if (isCorrect) correctCount++;
//            correctnessMap.put(i, isCorrect);
//            Log.v(TAG, "Q" + (i + 1) + ": Selected='" + selectedAnswerKey + "', Correct='" + question.getCorrectAnswer() + "', Result=" + isCorrect);
//        }
//
//        String resultMessage = String.format(Locale.getDefault(), "Result: %d / %d correct!", correctCount, totalQuestions);
//        Toast.makeText(this, resultMessage, Toast.LENGTH_LONG).show();
//        Log.i(TAG, "Final Score: " + correctCount + "/" + totalQuestions);
//
//        questionListAdapter.showResults(userAnswers, correctnessMap);
//
//        // --- *** SỬA LOGIC CẬP NHẬT NÚT *** ---
//        boolean allCorrect = (correctCount == totalQuestions);
//        boolean hasNext = hasNextExercise();
//        Log.d(TAG,"Submit check: AllCorrect=" + allCorrect + ", HasNext=" + hasNext);
//
//        if (allCorrect) {
//            // Nếu làm đúng hết
//            if (hasNext) {
//                // Đúng và còn bài tiếp theo -> NEXT
//                Log.i(TAG,"All answers correct. Has next exercise. Setting button to NEXT.");
//                setSubmitButtonState(STATE_NEXT, "Next");
//            } else {
//                // Đúng và là bài cuối -> FINISHED
//                Log.i(TAG,"All answers correct. Last exercise. Setting button to FINISH ! BACK NOW.");
//                setSubmitButtonState(STATE_FINISHED, "Finish ! Back Now");
//            }
//        } else {
//            // Nếu làm sai (dù chỉ 1 câu) -> LUÔN LUÔN RETRY
//            Log.i(TAG,"Some answers incorrect. Setting button to RETRY.");
//            setSubmitButtonState(STATE_RETRY, "Retry"); // *** Đặt trạng thái RETRY ***
//        }
//        // --- *** KẾT THÚC SỬA LOGIC *** ---
//
//        Log.d(TAG, "Submission process finished. Button state: " + currentButtonState + " ("+(btnSubmit != null ? btnSubmit.getText() : "null")+")");
//    }
//
//    private String getSelectedAnswerKey(int selectedRadioButtonId) {
//        if (selectedRadioButtonId == -1) return "";
//        View selectedRbView = findViewById(selectedRadioButtonId);
//        if (selectedRbView instanceof RadioButton) {
//            Object tagObj = ((RadioButton) selectedRbView).getTag();
//            if (tagObj instanceof String) return (String) tagObj;
//            Log.w(TAG, "RB ID " + selectedRadioButtonId + " no String tag! Fallback ID.");
//            if (selectedRadioButtonId == R.id.rbOptionA) return "A";
//            if (selectedRadioButtonId == R.id.rbOptionB) return "B";
//            if (selectedRadioButtonId == R.id.rbOptionC) return "C";
//            if (selectedRadioButtonId == R.id.rbOptionD) return "D";
//            Log.e(TAG, "Cannot determine key: " + selectedRadioButtonId);
//        } else Log.e(TAG, "View for ID "+ selectedRadioButtonId +" not RB!");
//        return "";
//    }
//
//    private boolean hasNextExercise() {
//        if (allExerciseTitles != null && !allExerciseTitles.isEmpty() && exerciseTitle != null) {
//            int currentIndex = allExerciseTitles.indexOf(exerciseTitle);
//            return currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1;
//        }
//        return false;
//    }
//
//    private void setSubmitButtonState(int state, String text) {
//        currentButtonState = state;
//        if (btnSubmit != null) {
//            btnSubmit.setText(text);
//            btnSubmit.setEnabled(true); // Luôn bật trừ khi có logic khác vô hiệu hóa
//            btnSubmit.setAlpha(1.0f);
//            Log.d(TAG, "Button state set: " + state + " (" + text + ")");
//        }
//    }
//
//    private void resetSubmitButtonToSubmitState() {
//        setSubmitButtonState(STATE_SUBMIT, "Submit");
//    }
//
//    // *** THÊM LẠI PHƯƠNG THỨC RETRY ***
//    private void retryExercise() {
//        Log.i(TAG, "Retry button clicked.");
//
//        // 1. Yêu cầu Adapter reset trạng thái câu hỏi
//        if (questionListAdapter != null) {
//            questionListAdapter.resetQuizState(); // Gọi hàm reset của adapter
//        } else {
//            Log.e(TAG, "Adapter is null, cannot reset state for retry.");
//            Toast.makeText(this, "Error resetting quiz.", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        // 2. Reset trạng thái và text của nút Submit về ban đầu
//        resetSubmitButtonToSubmitState();
//
//        // 3. Cuộn ListView lên đầu (tùy chọn)
//        if (lvQuestions != null) {
//            lvQuestions.post(() -> lvQuestions.smoothScrollToPosition(0));
//        }
//
//        Log.d(TAG,"Exercise state reset for retry. Button state: SUBMIT");
//    }
//    // *** KẾT THÚC THÊM LẠI ***
//
//
//    private void goToNextExercise() {
//        Log.i(TAG, "Attempting to go to next exercise.");
//        if (allExerciseTitles == null || allExerciseTitles.isEmpty()) {
//            Log.e(TAG, "Titles list not loaded.");
//            Toast.makeText(this, "Could not load exercise list.", Toast.LENGTH_SHORT).show();
//            if(btnSubmit != null) { btnSubmit.setText("Error"); btnSubmit.setEnabled(false); btnSubmit.setAlpha(0.5f); }
//            return;
//        }
//        int currentIndex = allExerciseTitles.indexOf(exerciseTitle);
//        if (currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1) {
//            String nextExerciseTitle = allExerciseTitles.get(currentIndex + 1);
//            Log.i(TAG, "Next exercise: " + nextExerciseTitle);
//            Intent nextIntent = new Intent(InternalReadingTopic.this, InternalReadingTopic.class);
//            nextIntent.putExtra("LEVEL_NAME", levelName);
//            nextIntent.putExtra("TOPIC_TITLE", topicTitle);
//            nextIntent.putExtra("EXERCISE_TITLE", nextExerciseTitle);
//            startActivity(nextIntent);
//            finish();
//        } else {
//            Log.e(TAG, "goToNext called but no next exercise. Index: " + currentIndex);
//            Toast.makeText(this, "No more exercises.", Toast.LENGTH_SHORT).show();
//            setSubmitButtonState(STATE_FINISHED, "Finished!");
//            if(btnSubmit != null) btnSubmit.setEnabled(false);
//        }
//    }
//
//    // --- Lifecycle Methods ---
//    @Override protected void onPause() { super.onPause(); Log.d(TAG,"onPause."); }
//    @Override protected void onResume() { super.onResume(); Log.d(TAG,"onResume."); }
//    @Override protected void onStop() { super.onStop(); Log.d(TAG,"onStop."); }
//    @Override protected void onDestroy() { super.onDestroy(); Log.i(TAG,"onDestroy."); }
//
//    private void resetContentUI(){
//        Log.d(TAG, "Resetting content UI.");
//        runOnUiThread(()-> {
//            if (tvExerciseDisplayTitle != null) tvExerciseDisplayTitle.setVisibility(View.INVISIBLE);
//            if (tvPassageDisplay != null) tvPassageDisplay.setVisibility(View.INVISIBLE);
//            if (lvQuestions != null) lvQuestions.setVisibility(View.INVISIBLE);
//            if (btnSubmit != null) btnSubmit.setVisibility(View.INVISIBLE);
//            TextView instructionText = findViewById(R.id.textView16);
//            if(instructionText != null) instructionText.setVisibility(View.INVISIBLE);
//        });
//    }
//
//    @Override public void onBackPressed() { super.onBackPressed(); Log.d(TAG,"onBackPressed."); }
//
//    private void loadAllExerciseTitlesFromFirebase() {
//        if (levelName == null || topicTitle == null) {
//            Log.e(TAG, "Cannot load titles: Level/Topic null.");
//            return;
//        }
//        allExerciseTitles = new ArrayList<>();
//        DatabaseReference exercisesRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
//                .getReference("Lessons").child("Levels").child(levelName)
//                .child("Reading").child("Topics").child(topicTitle).child("Exercises");
//
//        Log.d(TAG, "Loading all titles from: " + exercisesRef.toString());
//        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (snapshot.exists()) {
//                    allExerciseTitles.clear();
//                    for (DataSnapshot exSnap : snapshot.getChildren()) {
//                        String title = exSnap.getKey();
//                        if (title != null && !title.isEmpty()) allExerciseTitles.add(title);
//                    }
//                    Log.i(TAG, "Loaded " + allExerciseTitles.size() + " titles for topic: " + topicTitle);
//                    updateSubmitButtonStateAfterTitlesLoaded(); // Gọi hàm cập nhật nếu cần
//                } else Log.w(TAG, "No exercises found under topic: " + topicTitle);
//            }
//            @Override public void onCancelled(@NonNull DatabaseError error) {
//                Log.e(TAG, "Failed to load titles: " + error.getMessage());
//            }
//        });
//    }
//
//    // Cập nhật lại hàm này để xử lý đúng logic Retry
//    private void updateSubmitButtonStateAfterTitlesLoaded() {
//        if (questionListAdapter == null || btnSubmit == null) return;
//
//        // Chỉ cập nhật lại nếu nút *không* phải ở trạng thái Submit ban đầu
//        if (currentButtonState != STATE_SUBMIT) {
//            Log.d(TAG, "Updating button state after titles loaded. Current state: " + currentButtonState);
//            boolean hasNext = hasNextExercise();
//            boolean allCorrect = questionListAdapter.areAllAnswersCorrect(); // Cần hàm này trong adapter
//            Log.d(TAG, "Update Check: HasNext=" + hasNext + ", AllCorrect=" + allCorrect);
//
//            // *** SỬA LOGIC CHO KHỚP VỚI proceedWithSubmission ***
//            if (allCorrect) {
//                // Đúng hết: hoặc NEXT hoặc FINISHED
//                if (hasNext) setSubmitButtonState(STATE_NEXT, "Next");
//                else setSubmitButtonState(STATE_FINISHED, "Finish ! Back Now");
//            } else {
//                // Sai: Luôn là RETRY
//                // Chỉ cập nhật nếu trạng thái hiện tại *không* phải là RETRY (tránh cập nhật không cần thiết)
//                if (currentButtonState != STATE_RETRY) {
//                    Log.i(TAG, "Incorrect answers found after title load. Setting button state to RETRY.");
//                    setSubmitButtonState(STATE_RETRY, "Retry");
//                } else {
//                    Log.d(TAG, "Button state already RETRY, no update needed after title load.");
//                }
//            }
//            // *** KẾT THÚC SỬA LOGIC ***
//        } else {
//            Log.d(TAG, "Button state is SUBMIT, no update needed after title load.");
//        }
//    }
//}
//
//
// //InternalReadingTopic.java (Refactored as View)

package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.langhexx.Controller.ReadingController; // Import Controller
import com.example.langhexx.Controller.ReadingQuestionListAdapter;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.ReadingQuestion;
import com.example.langhexx.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// This Activity now primarily acts as the VIEW
public class InternalReadingTopic extends AppCompatActivity implements ReadingController.ViewInterface { // Implement View Interface

    private static final String TAG = "InternalReadTopicVIEW"; // Changed TAG for clarity

    private ImageView imgClose, imgHome;

    // --- UI Elements ---
    private ListView lvQuestions;
    private Button btnSubmit;
    private TextView tvExerciseDisplayTitle;
    private TextView tvPassageDisplay;
    private TextView instructionText; // Added reference for instruction text

    // --- Adapters and Local Data List (Managed by View for UI binding) ---
    private List<ReadingQuestion> questionsList; // Holds data for the adapter
    private ReadingQuestionListAdapter questionListAdapter;

    // --- Controller ---
    private ReadingController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_reading_topic);
        Log.d(TAG, "onCreate");

        addControls();
        setupListView(); // Setup adapter early

        // Initialize Controller, passing the View (this) and Intent
        controller = new ReadingController(this, getIntent());

        // Controller will now handle loading data and initial setup logic
        controller.initialize(); // Start loading data etc.

        addEvents();
    }

    private void addControls() {
        Log.d(TAG, "addControls");
        lvQuestions = findViewById(R.id.lvQuestions);
        btnSubmit = findViewById(R.id.btnSubmit);
        tvExerciseDisplayTitle = findViewById(R.id.txtTitle);
        tvPassageDisplay = findViewById(R.id.txtParagraph);
        instructionText = findViewById(R.id.textView16); // Find instruction text
        imgClose = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);

        // Initial state (hidden until Controller provides data)
        setUIElementsVisibility(false);
    }

    private void setupListView() {
        Log.d(TAG, "setupListView");
        questionsList = new ArrayList<>(); // Initialize list
        questionListAdapter = new ReadingQuestionListAdapter(this, questionsList);
        lvQuestions.setAdapter(questionListAdapter);
    }

    private void addEvents() {
        Log.d(TAG, "addEvents");
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                // Delegate button click logic to the Controller
                controller.onSubmitButtonClicked();
            });
        }

        imgClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        imgHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(InternalReadingTopic.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });

    }

    // --- Implementation of ReadingController.ViewInterface ---

    @Override
    public void displayExerciseTitle(String title) {
        Log.d(TAG, "displayExerciseTitle: " + title);
        runOnUiThread(() -> {
            if (tvExerciseDisplayTitle != null) {
                tvExerciseDisplayTitle.setText(title);
            }
        });
    }

    @Override
    public void displayPassage(String text) {
        Log.d(TAG, "displayPassage: " + (text != null && !text.isEmpty() ? text.substring(0, Math.min(text.length(), 50))+"..." : "empty"));
        runOnUiThread(() -> {
            if (tvPassageDisplay != null) {
                tvPassageDisplay.setText(text != null ? text : "Content not available.");
            }
        });
    }

    @Override
    public void updateAdapterData(List<ReadingQuestion> newQuestions) {
        Log.d(TAG, "updateAdapterData: Received " + (newQuestions != null ? newQuestions.size() : 0) + " questions.");
        runOnUiThread(() -> {
            if (questionListAdapter != null && newQuestions != null) {
                // The adapter needs direct modification of its underlying list
                // So, we clear the View's list and add new data, then notify adapter
                // OR we update the adapter directly if it supports it (which it does via updateData)
                questionListAdapter.updateData(newQuestions);
                Log.d(TAG, "Adapter notified with " + newQuestions.size() + " questions.");
                // Make UI visible only if there are questions or passage
                setUIElementsVisibility(true); // Controller decides based on loaded data
            } else {
                Log.w(TAG, "Adapter is null or newQuestions is null, cannot update UI.");
                // Optionally hide list if no questions
                if (lvQuestions != null) lvQuestions.setVisibility(View.GONE);
                if (instructionText != null) instructionText.setVisibility(View.GONE);
                if (btnSubmit != null) btnSubmit.setVisibility(View.GONE);

            }
        });
    }


    @Override
    public void showResultsInAdapter(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctnessMap) {
        Log.d(TAG, "showResultsInAdapter");
        runOnUiThread(() -> {
            if (questionListAdapter != null) {
                questionListAdapter.showResults(userAnswers, correctnessMap);
            } else {
                Log.e(TAG, "Cannot show results, adapter is null");
            }
        });
    }

    @Override
    public void resetAdapterState() {
        Log.d(TAG, "resetAdapterState");
        runOnUiThread(() -> {
            if (questionListAdapter != null) {
                questionListAdapter.resetQuizState();
            } else {
                Log.e(TAG, "Cannot reset adapter state, adapter is null");
            }
        });
    }

    @Override
    public void setButtonState(int state, String text) {
        Log.d(TAG, "setButtonState: State=" + state + ", Text=" + text);
        runOnUiThread(() -> {
            if (btnSubmit != null) {
                btnSubmit.setText(text);
                // Basic enable/disable logic based on state (can be refined by controller)
//                boolean enabled = state != ReadingController.STATE_FINISHED_NO_NEXT; // Example disabling logic
                btnSubmit.setEnabled(true);
                btnSubmit.setAlpha(1.0f);
            }
        });
    }


    @Override
    public Map<Integer, Integer> getAdapterSelectedAnswers() {
        if (questionListAdapter != null) {
            return questionListAdapter.getSelectedAnswers();
        }
        Log.e(TAG, "getAdapterSelectedAnswers: Adapter is null");
        return new HashMap<>(); // Return empty map if adapter is null
    }

    @Override
    public boolean areAdapterAnswersAllCorrect() {
        if (questionListAdapter != null) {
            return questionListAdapter.areAllAnswersCorrect();
        }
        Log.e(TAG, "areAdapterAnswersAllCorrect: Adapter is null");
        return false;
    }

    @Override
    public void showToast(String message) {
        Log.d(TAG, "showToast: " + message);
        runOnUiThread(() -> Toast.makeText(InternalReadingTopic.this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void showFailToast(String message) {
        Log.d(TAG, "showFailToast: " + message);
        runOnUiThread(() -> CustomToast.showFail(InternalReadingTopic.this, message, R.drawable.fail_icon));
    }


    @Override
    public void showConfirmationDialog(String title, String message, Runnable onConfirm) {
        Log.d(TAG, "showConfirmationDialog");
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Submit", (dialog, which) -> onConfirm.run()) // Execute Runnable on confirm
                .setNegativeButton("Cancel", (dialog, which) -> Log.d(TAG, "Submission cancelled."))
                .setCancelable(false)
                .show());
    }

    @Override
    public void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle) {
        Log.i(TAG, "navigateToNextExercise: " + nextExerciseTitle);
        Intent nextIntent = new Intent(InternalReadingTopic.this, InternalReadingTopic.class);
        nextIntent.putExtra("LEVEL_NAME", levelName);
        nextIntent.putExtra("TOPIC_TITLE", topicTitle);
        nextIntent.putExtra("EXERCISE_TITLE", nextExerciseTitle);
        startActivity(nextIntent);
        finish(); // Finish current activity after starting the next one
    }

    @Override
    public void finishActivity() {
        Log.i(TAG, "finishActivity");
        finish();
    }

    @Override
    public void scrollToQuestion(int index) {
        Log.d(TAG, "scrollToQuestion: " + index);
        if (lvQuestions != null) {
            lvQuestions.post(() -> lvQuestions.smoothScrollToPosition(index));
        }
    }

    @Override
    public void setUIElementsVisibility(boolean visible) {
        Log.d(TAG, "setUIElementsVisibility: " + visible);
        runOnUiThread(() -> {
            int visibility = visible ? View.VISIBLE : View.INVISIBLE;
            if (tvExerciseDisplayTitle != null) tvExerciseDisplayTitle.setVisibility(visibility);
            if (tvPassageDisplay != null) tvPassageDisplay.setVisibility(visibility);
            // Only show list/instructions/button if there's actual content (Controller determines 'visible')
            if (lvQuestions != null) lvQuestions.setVisibility(visible && questionListAdapter != null && questionListAdapter.getCount() > 0 ? View.VISIBLE : View.GONE);
            if (instructionText != null) instructionText.setVisibility(visible && questionListAdapter != null && questionListAdapter.getCount() > 0 ? View.VISIBLE : View.GONE);
            if (btnSubmit != null) btnSubmit.setVisibility(visible && questionListAdapter != null && questionListAdapter.getCount() > 0 ? View.VISIBLE : View.GONE);

            // Hide passage view specifically if passage text is null/empty, even if 'visible' is true overall
            if (tvPassageDisplay != null && visible) {
                CharSequence currentText = tvPassageDisplay.getText();
                if (currentText == null || currentText.toString().isEmpty() || currentText.toString().equals("Content not available.")) {
                    tvPassageDisplay.setVisibility(View.GONE);
                } else {
                    tvPassageDisplay.setVisibility(View.VISIBLE);
                }
            } else if (tvPassageDisplay != null && !visible){
                tvPassageDisplay.setVisibility(View.INVISIBLE); // Ensure it's hidden if visibility=false
            }

        });
    }

    // --- Lifecycle Methods (Forwarding to Controller if needed, or just logging) ---
    @Override protected void onPause() { super.onPause(); Log.d(TAG,"onPause."); }
    @Override protected void onResume() { super.onResume(); Log.d(TAG,"onResume."); }
    @Override protected void onStop() { super.onStop(); Log.d(TAG,"onStop."); }
    @Override protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG,"onDestroy.");
        if (controller != null) {
            controller.onDestroy(); // Allow controller to clean up if needed
        }
    }
    @Override public void onBackPressed() { super.onBackPressed(); Log.d(TAG,"onBackPressed."); }

}
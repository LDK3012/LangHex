package com.example.langhexx.View; // Thay đổi thành package của bạn

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat; // Import for tinting

// Import Volley and JSON
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.langhexx.R; // Thay đổi thành R của bạn
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class InternalSpeakingTopic extends AppCompatActivity {
    private LinearLayout questionContainer;
    private ArrayList<String> questionList;
    private TextToSpeech textToSpeech;
    private String levelName, topicTitle;
    private int currentQuestionIndex = 0;
    private LayoutInflater inflater;
    private ImageButton btnMicro;
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final int REQUEST_CODE_SPEECH_INPUT = 100;
    private ImageView btnClose ;
    // --- Views for the CURRENT question being processed ---
    private TextView txtCurrentQuestion;
    private TextView txtResponse;
    private ImageView responseSpeaker;
    private ImageView avatarUser;
    private ImageView iconWarning;
    // --- End Views for Current Question ---

    private ProgressBar progressBar; // Shared progress bar (can be specific if needed)
    private RequestQueue requestQueue; // Volley Request Queue
    // --- IMPORTANT: Replace with your actual Gemini API Key ---
    private static final String GEMINI_API_KEY = "AIzaSyDoQKvSTwu_RJMIKl3c456iLFW0oIK16tc"; // <--- THAY THẾ API KEY CỦA BẠN VÀO ĐÂY
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;
    private static final String TAG = "InternalSpeakingTopic"; // For logging
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private View qaView ;

    //
    private SoundPool soundPool;
    private int correctSoundId;
    private int incorrectSoundId;
    private boolean soundsLoaded = false;

    // Speech Recognizer (Consider initializing lazily if preferred)
    // private SpeechRecognizer speechRecognizer;
    // private Intent speechRecognizerIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // EdgeToEdge.enable(this); // Uncomment if you use EdgeToEdge
        setContentView(R.layout.activity_internal_speaking_topic); // Use your layout file name
        // ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> { // Use your root layout ID
        //     Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        //     v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
        //     return insets;
        // });

        addControls();
        addEvent();
        initTextToSpeech(); // TTS initialization triggers question loading
        requestQueue = Volley.newRequestQueue(this); // Initialize Volley queue

        // Set the Mic button listener ONCE
        btnMicro.setOnClickListener(v -> {
            if (questionList == null || questionList.isEmpty()) {
                Toast.makeText(this, "Chưa tải xong câu hỏi.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentQuestionIndex < questionList.size()) { // Only allow speaking if there's a question
                startSpeechRecognition();
            } else {
                Toast.makeText(this, "Đã hoàn thành tất cả câu hỏi.", Toast.LENGTH_SHORT).show();
            }
        });
        // SoundPool
        initializeSoundPool();
    }


    //
    // Phương thức hiển thị Custom Toast
    public void showCustomSuccessToast(Context context, String message, int iconResId) {
        // Lấy LayoutInflater
        LayoutInflater inflater = LayoutInflater.from(context);
        // Inflate layout tùy chỉnh
        View layout = inflater.inflate(R.layout.custom_success_toast_layout, null); // Sử dụng layout đã tạo

        // Tham chiếu đến các thành phần trong layout
        ImageView toastIcon = layout.findViewById(R.id.toast_icon);
        TextView toastText = layout.findViewById(R.id.toast_text);

        // Đặt nội dung và icon
        toastText.setText(message);
        if (iconResId != 0) { // Kiểm tra xem có cung cấp icon không
            toastIcon.setImageResource(iconResId);
            toastIcon.setVisibility(View.VISIBLE);
        } else {
            toastIcon.setVisibility(View.GONE); // Ẩn ImageView nếu không có icon
        }

        // Tạo đối tượng Toast
        Toast toast = new Toast(context.getApplicationContext());

        // Đặt vị trí (Gravity) - Ví dụ: hiển thị ở trên cùng, căn giữa ngang
        // Giống như trong hình ảnh, thông báo xuất hiện từ trên xuống
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 50); // 0 là xOffset, 50 là yOffset (điều chỉnh nếu cần)

        // Đặt thời gian hiển thị (SHORT hoặc LONG)
        toast.setDuration(Toast.LENGTH_LONG);

        // Gán layout tùy chỉnh cho Toast
        toast.setView(layout);

        // Hiển thị Toast
        toast.show();
    }
    public void showCustomFailToast(Context context, String message, int iconResId) {
        // Lấy LayoutInflater
        LayoutInflater inflater = LayoutInflater.from(context);
        // Inflate layout tùy chỉnh
        View layout = inflater.inflate(R.layout.custome_fail_toast_layout, null); // Sử dụng layout đã tạo

        // Tham chiếu đến các thành phần trong layout
        ImageView toastIcon = layout.findViewById(R.id.toast_icon);
        TextView toastText = layout.findViewById(R.id.toast_text);

        // Đặt nội dung và icon
        toastText.setText(message);
        if (iconResId != 0) { // Kiểm tra xem có cung cấp icon không
            toastIcon.setImageResource(iconResId);
            toastIcon.setVisibility(View.VISIBLE);
        } else {
            toastIcon.setVisibility(View.GONE); // Ẩn ImageView nếu không có icon
        }

        // Tạo đối tượng Toast
        Toast toast = new Toast(context.getApplicationContext());

        // Đặt vị trí (Gravity) - Ví dụ: hiển thị ở trên cùng, căn giữa ngang
        // Giống như trong hình ảnh, thông báo xuất hiện từ trên xuống
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 50); // 0 là xOffset, 50 là yOffset (điều chỉnh nếu cần)

        // Đặt thời gian hiển thị (SHORT hoặc LONG)
        toast.setDuration(Toast.LENGTH_LONG);

        // Gán layout tùy chỉnh cho Toast
        toast.setView(layout);

        // Hiển thị Toast
        toast.show();
    }

    public void addEvent(){
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void initializeSoundPool() {
        // Sử dụng AudioAttributes để tương thích với API 21+
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION) // Phù hợp cho phản hồi UI
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(2) // Số luồng âm thanh tối đa có thể phát đồng thời
                .setAudioAttributes(audioAttributes)
                .build();

        // Listener để biết khi nào âm thanh đã load xong
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) { // status 0 là thành công
                Log.d(TAG, "Sound loaded: ID = " + sampleId);
                // Đánh dấu là đã sẵn sàng khi cả hai âm thanh đều được load (cách đơn giản)
                // Bạn có thể cần cơ chế phức tạp hơn nếu có nhiều âm thanh
                if (sampleId == correctSoundId || sampleId == incorrectSoundId) {
                    // Giả định là nếu một trong hai load xong thì tạm coi là sẵn sàng
                    // Để chắc chắn hơn, bạn có thể dùng biến đếm
                    soundsLoaded = true;
                }
            } else {
                Log.e(TAG, "Error loading sound ID " + sampleId + ", status = " + status);
                Toast.makeText(InternalSpeakingTopic.this, "Lỗi tải hiệu ứng âm thanh", Toast.LENGTH_SHORT).show();
            }
        });
        try {
            correctSoundId = soundPool.load(this, R.raw.correct_answer, 1); // priority = 1
            incorrectSoundId = soundPool.load(this, R.raw.wrong_answer, 1); // priority = 1
            Log.d(TAG, "Requesting load sounds - Correct ID: " + correctSoundId + ", Incorrect ID: " + incorrectSoundId);
            // Lưu ý: ID trả về ngay lập tức, nhưng việc load là bất đồng bộ. soundsLoaded sẽ cập nhật sau.
        } catch (Exception e) {
            Log.e(TAG, "Error finding sound files. Make sure they are in res/raw", e);
            Toast.makeText(this, "Không tìm thấy file âm thanh trong res/raw", Toast.LENGTH_LONG).show();
        }
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US); // Use US English for consistency
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TextToSpeech language (US English) not supported.");
                    Toast.makeText(this, "Ngôn ngữ nói (Tiếng Anh Mỹ) không được hỗ trợ!", Toast.LENGTH_SHORT).show();
                    // Handle error - maybe disable speaking features
                } else {
                    Log.d(TAG, "TextToSpeech initialized successfully (US English).");
                    textToSpeech.setPitch(1.1f); // Adjust pitch as desired
                    textToSpeech.setSpeechRate(0.95f); // Adjust speed as desired
                    loadQuestionsFromFirebase(); // Load questions ONLY after TTS is ready
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status: " + status);
                Toast.makeText(this, "Không thể khởi tạo chức năng đọc văn bản!", Toast.LENGTH_SHORT).show();
                // Handle error - maybe disable speaking features
            }
        });
    }

    private void addControls() {
        questionContainer = findViewById(R.id.questionContainer); // Your LinearLayout ID
        inflater = LayoutInflater.from(this);
        btnMicro = findViewById(R.id.btnSpeakingMicro);     // Your microphone button ID
        qaView = inflater.inflate(R.layout.custom_question_list, questionContainer, false);
        progressBar = qaView.findViewById(R.id.progressBar_speaking);//Initially hide progress bar

        // Get data from Intent
        btnClose = findViewById(R.id.btnClose) ;
        levelName = getIntent().getStringExtra("levelName");
        topicTitle = getIntent().getStringExtra("topicTitle");

        if (levelName == null || topicTitle == null || levelName.isEmpty() || topicTitle.isEmpty()) {
            Log.e(TAG, "LevelName or TopicTitle missing from Intent.");
            Toast.makeText(this, "Lỗi: Thiếu thông tin chủ đề.", Toast.LENGTH_LONG).show();
            finish(); // Close activity if essential data is missing
            return;
        }

        questionList = new ArrayList<>();
        // No initial view inflation needed here, done in showNextQuestion
    }

    private void loadQuestionsFromFirebase() {
        if (levelName == null || topicTitle == null) return; // Already handled in addControls, but double-check

        Log.d(TAG, "Loading questions for Level: " + levelName + ", Topic: " + topicTitle);
        progressBar.setVisibility(View.VISIBLE); // Show main progress bar

        // --- IMPORTANT: Update with your Firebase Realtime Database URL ---
        DatabaseReference questionRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/") // <--- THAY URL DATABASE CỦA BẠN
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Speaking")
                .child("Topics")
                .child(topicTitle);

        questionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                questionList.clear();
                if (snapshot.exists() && snapshot.hasChildren()) {
                    for (DataSnapshot questionSnap : snapshot.getChildren()) {
                        String question = questionSnap.getValue(String.class);
                        if (question != null && !question.trim().isEmpty()) {
                            questionList.add(question);
                            Log.d(TAG, "Loaded question: " + question);
                        }
                    }
                    Log.i(TAG, "Total questions loaded: " + questionList.size());
                } else {
                    Log.w(TAG, "No questions found in Firebase for this topic.");
                }


                if (!questionList.isEmpty()) {
                    showNextQuestion(); // Show the first question
                } else {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InternalSpeakingTopic.this, "Không tìm thấy câu hỏi nào cho chủ đề này.", Toast.LENGTH_LONG).show();
                    // Optional: Show a message in the LinearLayout
                    TextView noQuestionsText = new TextView(InternalSpeakingTopic.this);
                    noQuestionsText.setText("Không có câu hỏi cho chủ đề này.");
                    noQuestionsText.setPadding(16, 16, 16, 16);
                    questionContainer.addView(noQuestionsText);
                    btnMicro.setEnabled(false); // Disable mic if no questions
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Firebase data loading cancelled: " + error.getMessage(), error.toException());
                Toast.makeText(InternalSpeakingTopic.this, "Lỗi tải dữ liệu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                btnMicro.setEnabled(false); // Disable mic on error
            }
        });
    }

    @SuppressLint("InflateParams") // OK for adding to LinearLayout programmatically
    private void showNextQuestion() {
        if (questionList == null) return; // Should not happen if logic is correct

        // Check if all questions are completed
        if (currentQuestionIndex >= questionList.size()) {
            progressBar.setVisibility(View.GONE); // Hide any lingering progress bar
            if (questionContainer.findViewWithTag("completion_message") == null) {
                TextView completionText = new TextView(this);
                completionText.setText("Chúc mừng! Bạn đã hoàn thành tất cả câu hỏi!");
                completionText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                completionText.setPadding(16, 32, 16, 32);
                completionText.setTag("completion_message");
                questionContainer.addView(completionText);
                Log.i(TAG, "All questions completed.");
            }
            btnMicro.setEnabled(false); // Disable mic
            return;
        }

        // Hide main progress bar when showing a question
        progressBar.setVisibility(View.GONE);

        // Inflate a new view for the current question-answer pair
        View qaView = inflater.inflate(R.layout.custom_question_list, questionContainer, false); // Use your list item layout

        TextView txtQ = qaView.findViewById(R.id.txtQuestion);
        ImageView speakerQ = qaView.findViewById(R.id.speaker);
        TextView txtRes = qaView.findViewById(R.id.tvResponse);
        ImageView resSpeaker = qaView.findViewById(R.id.responseSpeaker);
        ImageView avatar = qaView.findViewById(R.id.userAvatar);
        ImageView iconWarn = qaView.findViewById(R.id.iconWarning);
        // ProgressBar itemProgressBar = qaView.findViewById(R.id.progressBar_speaking); // If you have one per item

        // Store references to the views of the *current* item
        this.txtCurrentQuestion = txtQ; // Store the current question TextView
        this.txtResponse = txtRes;
        this.responseSpeaker = resSpeaker;
        this.avatarUser = avatar;
        this.iconWarning = iconWarn;
        // this.itemProgressBar = itemProgressBar; // Store item progress bar if you have one

        // Configure the new question view
        String question = questionList.get(currentQuestionIndex);
        txtQ.setText(question);

        // Hide response elements initially
        txtRes.setVisibility(View.GONE);
        resSpeaker.setVisibility(View.GONE);
        avatar.setVisibility(View.GONE);
        iconWarn.setVisibility(View.GONE);
        // if (itemProgressBar != null) itemProgressBar.setVisibility(View.GONE);

        // Add the new question view to the container
        questionContainer.addView(qaView);

        // Scroll down to show the new question (optional, good for long lists)
        handler.post(() -> ((android.widget.ScrollView) findViewById(R.id.scrollViewContent)).fullScroll(View.FOCUS_DOWN)); // Use your ScrollView ID

        // Speaker icon for the question (manual click)
        speakerQ.setOnClickListener(v -> speakText(question, "question_" + currentQuestionIndex));

        // --- Auto-speak the question (except the first one) ---
        if (currentQuestionIndex > 0) {
            handler.postDelayed(() -> {
                Log.d(TAG, "Auto-speaking question " + currentQuestionIndex + ": " + question);
                speakText(question, "question_" + currentQuestionIndex);
            }, 1000); // 1 second delay
        } else {
            Log.d(TAG, "First question (index 0), skipping auto-speak.");
            // Maybe briefly highlight the speaker icon?
        }

        // Ensure microphone is enabled for the new question
        if (!btnMicro.isEnabled() && currentQuestionIndex < questionList.size()) {
            btnMicro.setEnabled(true);
            Log.d(TAG,"Re-enabling mic for question " + currentQuestionIndex);
        }
    }

    // Helper method for TextToSpeech
    private void speakText(String text, String utteranceId) {
        if (textToSpeech != null && !text.isEmpty()) {
            if (textToSpeech.isSpeaking()) {
                // Optional: Stop current speech before starting new, or queue it
                // textToSpeech.stop();
                Log.w(TAG, "TTS speaking, queueing utterance: " + utteranceId);
            }
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId); // QUEUE_FLUSH interrupts current speech
        } else {
            Log.e(TAG, "TTS not ready or text is empty, cannot speak.");
        }
    }

    // --- Speech Recognition ---
    private void startSpeechRecognition() {
        // Check for RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return; // Wait for permission result
        }

        // --- Use Standard Android Recognizer Intent ---
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US"); // Target US English
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false); // Need final result
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1); // Get the single best result
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói câu trả lời của bạn..."); // Prompt for user

        // Timeouts (optional, adjust as needed)
        // Wait 5 seconds for speech to begin
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000);
        // Consider speech possibly complete after 3 seconds of silence
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000);
        // Consider speech definitely complete after 5 seconds of silence
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);


        try {
            Log.d(TAG, "Starting speech recognition activity.");
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Log.e(TAG, "Speech recognition not available or intent error.", e);
            Toast.makeText(this, "Không thể khởi động nhận dạng giọng nói trên thiết bị này.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    String spokenResponse = results.get(0).trim();
                    if (!spokenResponse.isEmpty()) {
                        Log.i(TAG, "Speech recognized: " + spokenResponse);
                        // Capitalize first letter for display
                        String formattedResponse = spokenResponse.substring(0, 1).toUpperCase() + spokenResponse.substring(1);

                        // --- Start Evaluation ---
                        // Show main progress bar while evaluating
                        progressBar.setVisibility(View.VISIBLE);
                        btnMicro.setEnabled(false); // Disable mic during API call

                        String currentQuestionText = questionList.get(currentQuestionIndex);
                        evaluateResponseWithGemini(currentQuestionText, formattedResponse);

                    } else {
                        Log.w(TAG, "Speech recognized but result is empty.");
                        Toast.makeText(this, "Không nhận dạng được giọng nói rõ ràng.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w(TAG, "Speech recognition result list is null or empty.");
                    Toast.makeText(this, "Không nhận dạng được giọng nói.", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Handle different error results
                String errorReason = "Unknown recognition error";
                switch (resultCode) {
                    case RecognizerIntent.RESULT_AUDIO_ERROR:
                        errorReason = "Lỗi âm thanh đầu vào.";
                        break;
                    case RecognizerIntent.RESULT_CLIENT_ERROR:
                        errorReason = "Lỗi phía client.";
                        break;
                    case RecognizerIntent.RESULT_NETWORK_ERROR:
                        errorReason = "Lỗi mạng.";
                        break;
                    case RecognizerIntent.RESULT_NO_MATCH:
                        errorReason = "Không có kết quả phù hợp.";
                        break;
                    case RecognizerIntent.RESULT_SERVER_ERROR:
                        errorReason = "Lỗi từ server nhận dạng.";
                        break;
                    case RESULT_CANCELED:
                        errorReason = "Người dùng đã hủy.";
                        break;
                }
                Log.w(TAG, "Speech recognition failed or cancelled. ResultCode: " + resultCode + ", Reason: " + errorReason);
                if (resultCode != RESULT_CANCELED) { // Don't toast if user cancelled
                    Toast.makeText(this, "Lỗi nhận dạng: " + errorReason, Toast.LENGTH_SHORT).show();
                }
            }
            // Ensure mic is re-enabled if evaluation didn't start
            if (!btnMicro.isEnabled() && progressBar.getVisibility() != View.VISIBLE) {
                btnMicro.setEnabled(true);
            }
        }
    }

    // --- Gemini API Call ---
    private void evaluateResponseWithGemini(String question, String userAnswer) {
//        Log.d(TAG, "Evaluating with Gemini. Question: " + question + ", Answer: " + userAnswer);
//        // progressBar.setVisibility(View.VISIBLE); // Already set in onActivityResult
//        // btnMicro.setEnabled(false); // Already set in onActivityResult
//
//        // --- UPDATED PROMPT ---
//        String promptText = String.format(
//                "Analyze the following user's answer in response to the given question within a language learning context.\n" +
//                        "Question: \"%s\"\n" +
//                        "User's Answer: \"%s\"\n\n" +
//                        "Respond ONLY with a JSON object containing these fields:\n" +
//                        "1. 'is_correct': boolean (true if the answer is generally acceptable, false otherwise).\n" +
//                        "2. 'feedback_vi': string (If 'is_correct' is false, provide a BRIEF explanation *in Vietnamese* identifying the main issue - e.g., grammar error type, vocabulary choice, relevance, logic. If 'is_correct' is true, this can be empty or provide minor suggestions *in Vietnamese*).\n" +
//                        "3. 'suggested_answer_en': string (Provide a well-formed, correct alternative answer *in English* only if the user's answer is incorrect or significantly flawed. Empty string if the user's answer is correct or needs only minor tweaks mentioned in feedback_vi).\n\n" +
//                        "Example of expected JSON if incorrect:\n" +
//                        "{\n" +
//                        "  \"is_correct\": false,\n" +
//                        "  \"feedback_vi\": \"Câu trả lời có lỗi ngữ pháp về thì của động từ.\",\n" +
//                        "  \"suggested_answer_en\": \"I went to the park yesterday.\"\n" +
//                        "}\n\n" +
//                        "Example of expected JSON if correct:\n" +
//                        "{\n" +
//                        "  \"is_correct\": true,\n" +
//                        "  \"feedback_vi\": \"\",\n" +
//                        "  \"suggested_answer_en\": \"\"\n" +
//                        "}",
//                question, userAnswer
//        );
        Log.d(TAG, "Evaluating with Gemini. Question: " + question + ", Answer: " + userAnswer);
        // ... (rest of your setup code)

        // --- UPDATED PROMPT ---
        String promptText = String.format(
                "Analyze the following user's answer in response to the given question within a language learning context.\n" +
                        "Question: \"%s\"\n" +
                        "User's Answer: \"%s\"\n\n" +
                        "Respond ONLY with a JSON object containing these fields:\n" +
                        "1. 'is_correct': boolean (true if the answer is grammatically correct, relevant to the question's topic, and logically coherent. The answer is considered correct even if it answers the core question and provides additional relevant information, instead of just the most direct minimal answer. For example, if the question is 'Do you like ice cream?' and the answer is 'No, I like chocolate', this IS correct. Set to false ONLY if there are significant grammatical errors, the answer is completely irrelevant, or logically nonsensical).\n" +
                        "2. 'feedback_vi': string (If 'is_correct' is false, provide a BRIEF explanation *in Vietnamese* identifying the main issue - e.g., grammar error type, vocabulary choice, relevance, logic. If 'is_correct' is true, this can be empty OR provide minor suggestions/positive feedback *in Vietnamese*, e.g., 'Câu trả lời đúng và tự nhiên!' or 'Câu trả lời đúng rồi, bạn có thể nói đầy đủ hơn là \"No, I don't like ice cream, but I like chocolate.\" nếu muốn.').\n" +
                        "3. 'suggested_answer_en': string (Provide a well-formed, correct alternative answer *in English* ONLY if the user's answer is incorrect ('is_correct': false). Leave empty if the user's answer is correct).\n\n" +
                        "Example of expected JSON if incorrect (grammar error):\n" +
                        "{\n" +
                        "  \"is_correct\": false,\n" +
                        "  \"feedback_vi\": \"Câu trả lời có lỗi ngữ pháp về thì của động từ.\",\n" +
                        "  \"suggested_answer_en\": \"I went to the park yesterday.\"\n" +
                        "}\n\n" +
                        "Example of expected JSON if correct but indirect (like the ice cream example):\n" +
                        "{\n" +
                        "  \"is_correct\": true,\n" +
                        "  \"feedback_vi\": \"Câu trả lời tốt! Bạn đã trả lời đúng trọng tâm và mở rộng thêm thông tin liên quan.\",\n" +
                        "  \"suggested_answer_en\": \"\"\n" +
                        "}\n\n" +
                        "Example of expected JSON if correct and direct:\n" +
                        "{\n" +
                        "  \"is_correct\": true,\n" +
                        "  \"feedback_vi\": \"\",\n" +
                        "  \"suggested_answer_en\": \"\"\n" +
                        "}",
                question, userAnswer
        );
        // --- END UPDATED PROMPT ---

        JSONObject requestBody = new JSONObject();
        try {
            JSONArray contentsArray = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray partsArray = new JSONArray();
            JSONObject part = new JSONObject();

            part.put("text", promptText);
            partsArray.put(part);
            content.put("parts", partsArray);
            contentsArray.put(content);
            requestBody.put("contents", contentsArray);

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("response_mime_type", "application/json");
            requestBody.put("generationConfig", generationConfig);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON request body", e);
            handleEvaluationError("Lỗi tạo yêu cầu JSON", userAnswer);
            return;
        }

        // Add a Log for the request body being sent (optional, for debugging)
        Log.d(TAG, "Gemini Request Body: " + requestBody.toString());

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, GEMINI_API_URL, requestBody,
                response -> {
                    Log.d(TAG, "Gemini API Full Response: " + response.toString());
                    try {
                        JSONArray candidates = response.optJSONArray("candidates"); // Use optJSONArray for safer parsing
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject firstCandidate = candidates.getJSONObject(0);

                            // Check for safety ratings (optional but recommended)
                            JSONArray safetyRatings = firstCandidate.optJSONArray("safetyRatings");
                            if (safetyRatings != null) {
                                for (int i = 0; i < safetyRatings.length(); i++) {
                                    JSONObject rating = safetyRatings.getJSONObject(i);
                                    String category = rating.optString("category", "UNKNOWN");
                                    String probability = rating.optString("probability", "UNKNOWN");
                                    // Block if probability is high/medium for harmful categories
                                    if (!probability.equals("NEGLIGIBLE") && !probability.equals("LOW")) {
                                        Log.w(TAG, "Content blocked by safety filter. Category: " + category + ", Probability: " + probability);
                                        handleEvaluationError("Nội dung không phù hợp (bị chặn bởi bộ lọc an toàn)", userAnswer);
                                        return; // Stop processing
                                    }
                                }
                            }


                            JSONObject content = firstCandidate.optJSONObject("content");
                            if (content != null) {
                                JSONArray parts = content.optJSONArray("parts");
                                if (parts != null && parts.length() > 0) {
                                    String jsonResponseString = parts.getJSONObject(0).optString("text", "");
                                    if (!jsonResponseString.isEmpty()) {
                                        Log.d(TAG, "Gemini JSON Response String (from parts): " + jsonResponseString);
                                        try {
                                            JSONObject evaluationResult = new JSONObject(jsonResponseString);

                                            boolean isCorrect = evaluationResult.optBoolean("is_correct", false);
                                            String feedbackVi = evaluationResult.optString("feedback_vi", "");
                                            String suggestionEn = evaluationResult.optString("suggested_answer_en", "");

                                            handleEvaluationResult(userAnswer, isCorrect, feedbackVi, suggestionEn);
                                        } catch (JSONException jsonEx) {
                                            Log.e(TAG, "Error parsing the JSON string within 'parts'", jsonEx);
                                            Log.e(TAG, "Problematic JSON string: " + jsonResponseString);
                                            handleEvaluationError("Lỗi phân tích cấu trúc phản hồi JSON", userAnswer);
                                        }
                                    } else {
                                        handleEvaluationError("Phần 'text' trong phản hồi API trống", userAnswer);
                                    }
                                } else {
                                    handleEvaluationError("Phần 'parts' trong phản hồi API trống hoặc không hợp lệ", userAnswer);
                                }
                            } else {
                                // Check finishReason if content is missing
                                String finishReason = firstCandidate.optString("finishReason", "UNKNOWN");
                                Log.w(TAG,"Candidate finished with reason: " + finishReason);
                                if (finishReason.equals("SAFETY")) {
                                    handleEvaluationError("Nội dung bị chặn vì lý do an toàn", userAnswer);
                                } else if (finishReason.equals("RECITATION")) {
                                    handleEvaluationError("Phản hồi bị chặn vì trích dẫn", userAnswer);
                                } else {
                                    handleEvaluationError("Phản hồi API không có 'content' hợp lệ (Reason: "+finishReason+")", userAnswer);
                                }
                            }
                        } else {
                            // Check for promptFeedback if no candidates
                            JSONObject promptFeedback = response.optJSONObject("promptFeedback");
                            if (promptFeedback != null) {
                                String blockReason = promptFeedback.optString("blockReason", "không rõ");
                                JSONArray safetyRatings = promptFeedback.optJSONArray("safetyRatings");
                                String safetyDetail = "";
                                if (safetyRatings != null) safetyDetail = " Ratings: " + safetyRatings.toString();

                                Log.e(TAG, "Gemini prompt blocked. Reason: " + blockReason + safetyDetail);
                                handleEvaluationError("Yêu cầu bị chặn (Lý do: " + blockReason + ")", userAnswer);
                            } else {
                                Log.e(TAG,"Gemini response missing 'candidates' and 'promptFeedback'. Response: " + response);
                                handleEvaluationError("Phản hồi API không có 'candidates' hợp lệ", userAnswer);
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing top-level Gemini JSON response", e);
                        handleEvaluationError("Lỗi phân tích tổng thể phản hồi JSON", userAnswer);
                    }
                },
                error -> {
                    // Improved error handling for Volley
                    String errorMsg = "Lỗi mạng hoặc API không xác định";
                    int statusCode = -1;
                    String responseData = "";
                    if (error.networkResponse != null) {
                        statusCode = error.networkResponse.statusCode;
                        if (error.networkResponse.data != null) {
                            try {
                                responseData = new String(error.networkResponse.data, "UTF-8");
                                Log.e(TAG, "Gemini API Error Response (Raw Data): " + responseData);
                                // Try to parse Google API Error format
                                try {
                                    JSONObject errorJson = new JSONObject(responseData);
                                    JSONObject errorDetail = errorJson.optJSONObject("error");
                                    if (errorDetail != null) {
                                        errorMsg = errorDetail.optString("message", responseData); // Get specific message
                                        // You could potentially log errorDetail.optString("status") or "code" as well
                                    } else {
                                        errorMsg = responseData; // Use raw data if not standard format
                                    }
                                } catch (JSONException e) {
                                    errorMsg = responseData; // Use raw data if parsing fails
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error decoding network error response data", e);
                                errorMsg = "Lỗi đọc phản hồi mạng.";
                            }
                        }
                        errorMsg = "Lỗi API (Code: " + statusCode + "): " + errorMsg;
                    } else {
                        // General Volley error (timeout, connection refused, etc.)
                        errorMsg = "Lỗi kết nối hoặc hết thời gian chờ.";
                        Log.e(TAG, "Volley Error (No Network Response): " + error.toString(), error);
                    }
                    Log.e(TAG, "Gemini API Volley Error: " + errorMsg, error); // Log detailed message and exception
                    handleEvaluationError(errorMsg, userAnswer); // Pass detailed error message
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                // API key is in the URL for this Gemini endpoint
                return headers;
            }

            // Optional: Increase timeout? Default is usually 5 seconds.
            // @Override
            // public RetryPolicy getRetryPolicy() {
            //     return new DefaultRetryPolicy(15000, // 15 seconds timeout
            //             DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            //             DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
            // }
        };

        // Add the request to the RequestQueue.
        requestQueue.add(jsonObjectRequest);
    }


    // --- Handle Gemini Result ---
    private void handleEvaluationResult(String userAnswer, boolean isCorrect, String feedbackVi, String suggestionEn) {
        progressBar.setVisibility(View.GONE); // Hide main progress bar

        // Ensure current views are valid
        if (this.txtResponse == null || this.responseSpeaker == null || this.avatarUser == null || this.iconWarning == null) {
            Log.e(TAG, "Current question views are null in handleEvaluationResult. Cannot update UI.");
            // Re-enable mic as a fallback so user isn't stuck
            if (!btnMicro.isEnabled()) btnMicro.setEnabled(true);
            Toast.makeText(this, "Lỗi cập nhật giao diện kết quả.", Toast.LENGTH_SHORT).show();
            return;
        }

        txtResponse.setText(userAnswer);
        txtResponse.setVisibility(View.VISIBLE);
        responseSpeaker.setVisibility(View.VISIBLE);
        avatarUser.setVisibility(View.VISIBLE);

        responseSpeaker.setOnClickListener(v -> speakText(userAnswer, "user_answer_" + currentQuestionIndex));


        if (isCorrect) {
            Log.i(TAG, "Answer Correct");
            txtResponse.setTextColor(ContextCompat.getColor(this, R.color.Lime)); // Use your 'correct' color
            playSound(correctSoundId);
            if (iconWarning != null) { // Thêm kiểm tra null cho an toàn
                iconWarning.setVisibility(View.GONE);
                iconWarning.setOnClickListener(null); // Cũng nên xóa listener cũ nếu có
            }
            scheduleNextQuestion(2000, true); // Shorter delay for correct answer, advance index
        } else {
            Log.w(TAG, "Answer Incorrect. Feedback (vi): " + feedbackVi + ", Suggestion (en): " + suggestionEn);
            txtResponse.setTextColor(ContextCompat.getColor(this, R.color.Red)); // Use your 'incorrect' color
            //
            playSound(incorrectSoundId);
            iconWarning.setVisibility(View.VISIBLE);
            iconWarning.setImageResource(android.R.drawable.ic_dialog_info); // Ensure warning icon look

            // Tint the warning icon (e.g., red)
            Drawable icon = ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_info);
            if (icon != null) {
                icon = DrawableCompat.wrap(icon).mutate();
                DrawableCompat.setTint(icon, ContextCompat.getColor(this, android.R.color.holo_red_dark));
                iconWarning.setImageDrawable(icon);
            }

            // Build the combined dialog message
            StringBuilder dialogMessageBuilder = new StringBuilder();
            if (feedbackVi != null && !feedbackVi.trim().isEmpty()) {
                dialogMessageBuilder.append(feedbackVi); // Specific Vietnamese feedback
            } else {
                dialogMessageBuilder.append("Câu trả lời chưa chính xác."); // Fallback
            }

            if (suggestionEn != null && !suggestionEn.trim().isEmpty()) {
                dialogMessageBuilder.append("\n\n")
                        .append("Gợi ý câu trả lời đúng (tiếng Anh):")
                        .append("\n")
                        .append(suggestionEn); // English suggestion
            }

            String finalDialogMessage = dialogMessageBuilder.toString();
            iconWarning.setTag(finalDialogMessage); // Store combined message

            iconWarning.setOnClickListener(v -> {
                String storedMessage = (String) v.getTag();
                if (storedMessage != null) {
                    showFeedbackDialog(storedMessage); // Show combined feedback/suggestion
                }
            });

            // --- DO NOT ADVANCE ---
            btnMicro.setEnabled(true); // Re-enable mic for retry on the SAME question
            showCustomFailToast(this, "Chưa đúng. Xem gợi ý và thử lại.", R.drawable.fail_icon);
            //Toast.makeText(this, "Chưa đúng. Xem gợi ý và thử lại.", Toast.LENGTH_LONG).show(); // Vietnamese Toast
        }
    }

    //
    // --- Helper để phát âm thanh ---
    private void playSound(int soundId) {
        if (soundsLoaded && soundPool != null && soundId != 0) {
            // soundPool.play(soundID, leftVolume, rightVolume, priority, loop, rate);
            // leftVolume, rightVolume: 0.0 to 1.0
            // priority: 0 = lowest priority
            // loop: 0 = no loop, -1 = loop forever
            // rate: 1.0 = normal playback, 0.5 to 2.0 range
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
            Log.d(TAG, "Playing sound ID: " + soundId);
        } else {
            Log.w(TAG, "Cannot play sound - Sounds not loaded, soundPool null, or soundId invalid (ID: " + soundId + ", Loaded: " + soundsLoaded + ")");
        }
    }

    // --- Handle Evaluation Errors (API call failed, parsing failed, etc.) ---
    private void handleEvaluationError(String errorType, String userAnswer) {
        Log.e(TAG, "Handling Evaluation Error: " + errorType + " | User Answer: " + userAnswer);
        progressBar.setVisibility(View.GONE); // Hide progress bar
        btnMicro.setEnabled(true); // IMPORTANT: Re-enable mic so user isn't stuck

        // Show error toast to user
        Toast.makeText(this, "Lỗi đánh giá: " + errorType + ". Vui lòng thử lại.", Toast.LENGTH_LONG).show();

        // Still display the user's answer, but indicate failure
        if (this.txtResponse != null && this.responseSpeaker != null && this.avatarUser != null) {
            txtResponse.setText(userAnswer + "\n(Đánh giá thất bại)"); // Indicate error
            txtResponse.setTextColor(Color.GRAY); // Neutral color
            txtResponse.setVisibility(View.VISIBLE);
            responseSpeaker.setVisibility(View.VISIBLE); // Allow re-listening to their attempt
            avatarUser.setVisibility(View.VISIBLE);
            if (iconWarning != null) iconWarning.setVisibility(View.GONE); // Hide warning icon on error

            responseSpeaker.setOnClickListener(v -> speakText(userAnswer, "user_answer_error_" + currentQuestionIndex));
        } else {
            Log.e(TAG, "Current question views are null during error handling.");
        }

        // --- DO NOT ADVANCE on error ---
    }

    // --- Schedule Next Question ---
    // Added boolean parameter 'advanceIndex'
    private void scheduleNextQuestion(long delayMillis, boolean advanceIndex) {
        if (advanceIndex) {
            currentQuestionIndex++; // Only increment if advancing
            Log.d(TAG, "Scheduling next question. Advancing to index: " + currentQuestionIndex);
        } else {
            Log.d(TAG, "Scheduling UI update, staying on question index: " + currentQuestionIndex);
            // Ensure mic is enabled if not advancing (should be handled elsewhere, but as fallback)
            if (!btnMicro.isEnabled()) {
                btnMicro.setEnabled(true);
            }
        }
        // Always schedule the check/display of the (potentially new) question
        handler.postDelayed(this::showNextQuestion, delayMillis);
    }

    // --- Show Feedback Dialog (Overloaded) ---
//    private void showFeedbackDialog(String message) {
//        showFeedbackDialog(message); // Default title for main feedback
//    }

    private void showFeedbackDialog(String message) {
        final String DIALOG_TITLE = "Gợi ý phản hồi";
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity finishing, cannot show dialog.");
            return;
        }

        Drawable icon = ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_info);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon).mutate();
            DrawableCompat.setTint(icon, ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }

        new AlertDialog.Builder(this)
                .setTitle(DIALOG_TITLE) // Use provided title
                .setMessage(message) // Show the pre-formatted message
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setIcon(icon) // Set the tinted icon
                .setCancelable(true) // Allow dismissing by tapping outside
                .show();
    }


    // --- Lifecycle Methods ---
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called.");
        if (textToSpeech != null) {
            Log.d(TAG, "Shutting down TextToSpeech.");
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        // Remove any pending runnables in the handler
        if (handler != null) {
            Log.d(TAG, "Removing Handler callbacks.");
            handler.removeCallbacksAndMessages(null);
        }
        // Cancel any ongoing Volley requests associated with this activity's tag
        if (requestQueue != null) {
            Log.d(TAG, "Cancelling Volley requests with tag: " + TAG);
            requestQueue.cancelAll(TAG); // Important: Set tag on requests or cancel all
            // Alternatively, cancel all requests in the queue if this is the only activity using it
            // requestQueue.cancelAll(request -> true);
        }
        // if (speechRecognizer != null) {
        //     speechRecognizer.destroy();
        // }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called.");
        // Optional: Stop TTS if activity is paused
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            Log.d(TAG, "Stopping TTS onPause.");
            textToSpeech.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called.");
        // Optional: Re-initialize TTS or other resources if needed, though usually done in onCreate/onStart
    }


    // --- Permission Handling ---
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, user can now use the mic.
                Log.i(TAG,"RECORD_AUDIO permission granted.");
                Toast.makeText(this, "Đã cấp quyền ghi âm. Bạn có thể nhấn nút micro.", Toast.LENGTH_SHORT).show();
                // Optionally directly start recognition if user clicked before permission was granted
                // startSpeechRecognition();
            } else {
                // Permission denied. Disable mic functionality.
                Log.w(TAG,"RECORD_AUDIO permission denied.");
                Toast.makeText(this, "Cần quyền ghi âm để nhận dạng giọng nói.", Toast.LENGTH_LONG).show();
                btnMicro.setEnabled(false); // Disable the microphone button
                // Optionally show a more prominent message explaining why it's needed.
            }
        }
    }
}
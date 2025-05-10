//package com.example.langhexx.View;
//
//import android.content.Intent;
//import android.media.MediaPlayer;
//import android.os.Bundle;
//import android.os.Handler;
//import android.speech.tts.TextToSpeech;
//import android.speech.tts.UtteranceProgressListener;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.ImageButton;
//import android.widget.ListView;
//import android.widget.ProgressBar; // Đảm bảo import ProgressBar
//import android.widget.RadioButton;
//import android.widget.RadioGroup;
//import android.widget.SeekBar;
//import android.widget.TextView; // Giữ lại cho các TextView khác (như tiêu đề câu hỏi)
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AlertDialog;
//import androidx.appcompat.app.AppCompatActivity;
//
//// Thay đổi import Controller và Model nếu cần
//import com.example.langhexx.Controller.ListeningQuestionListAdapter;
//import com.example.langhexx.Model.CustomToast;
//import com.example.langhexx.Model.ListeningQuestion;
//import com.example.langhexx.R; // Thay đổi R nếu cần
//
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Locale;
//import java.util.Map;
//import java.util.UUID;
////
//
//
//public class InternalListeningTopic extends AppCompatActivity implements
//        TextToSpeech.OnInitListener,
//        MediaPlayer.OnPreparedListener,
//        MediaPlayer.OnCompletionListener,
//        SeekBar.OnSeekBarChangeListener {
//
//    private static final String TAG = "InternalListenTopic";
//    // Tạo ID duy nhất cho mỗi lần tổng hợp để tránh trùng lặp listener
//    private final String SYNTHESIS_UTTERANCE_ID = "SynthesisUtteranceId_" + UUID.randomUUID().toString();
//
//    // --- UI Elements ---
//    private ImageButton btnPlayAudio;
//    private SeekBar seekBarAudio;
//    private ListView lvQuestions;
//    private Button btnSubmit;
//    private ProgressBar progressBarAudioLoading; // ProgressBar để thay thế Toast
//
//    // --- TTS Members ---
//    private TextToSpeech tts;
//    private boolean isTtsInitialized = false;
//    private String scriptToSpeak;
//
//    // --- MediaPlayer Members ---
//    private MediaPlayer mediaPlayer;
//    private boolean isMediaPlayerPrepared = false;
//    private boolean isPlaying = false;
//    private File audioFile; // File âm thanh tạm được tổng hợp
//    private Handler progressHandler = new Handler(); // Handler để cập nhật tiến trình SeekBar
//    private Runnable updateSeekBarRunnable;
//    private TextView tvScreenTitle;
//
//    // --- Data Members ---
//    private List<ListeningQuestion> questionsList;
//    private ListeningQuestionListAdapter questionListAdapter;
//    private String levelName;
//    private String topicTitle;
//    private String exerciseTitle;
//    private ArrayList<String> allExerciseTitles;
//    //submit status
//    private static final int STATE_SUBMIT = 0;
//    private static final int STATE_RETRY = 1;
//    private static final int STATE_NEXT = 2;
//    private static final int STATE_FINISHED = -1;
//    private int currentButtonState = STATE_SUBMIT;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_internal_listening_topic); // Sử dụng layout có ProgressBar
//
//        // --- Nhận dữ liệu từ Intent ---
//        Intent intent = getIntent();
//        if (intent != null) {
//            levelName = intent.getStringExtra("LEVEL_NAME");
//            topicTitle = intent.getStringExtra("TOPIC_TITLE");
//            exerciseTitle = intent.getStringExtra("EXERCISE_TITLE");
//        } else {
//            // Xử lý trường hợp không có Intent hoặc dữ liệu
//            Toast.makeText(this, "Error: Missing exercise identifiers.", Toast.LENGTH_LONG).show();
//            Log.e(TAG, "Intent is null or missing required extras.");
//            finish(); // Đóng activity nếu thiếu dữ liệu cần thiết
//            return;
//        }
//
//        // Kiểm tra null cho các identifier
//        if (levelName == null || topicTitle == null || exerciseTitle == null) {
//            Toast.makeText(this, "Error: Invalid exercise identifiers.", Toast.LENGTH_LONG).show();
//            Log.e(TAG, "One or more identifiers are null: level=" + levelName + ", topic=" + topicTitle + ", exercise=" + exerciseTitle);
//            finish();
//            return;
//        }
//
//        addControls();
//        //
//        if (tvScreenTitle != null && exerciseTitle != null) {
//            tvScreenTitle.setText(exerciseTitle);
//        }
//        //
//        setupListView();
//        initializeTextToSpeech(); // Khởi tạo TTS trước
//        loadExerciseDataFromFirebase(); // Tải dữ liệu (sẽ kích hoạt tổng hợp TTS khi có script)
//        loadAllExerciseTitlesFromFirebase();
//        addEvents();
//    }
//
//    private void addControls() {
//        btnPlayAudio = findViewById(R.id.btnPlayAudio);
//        seekBarAudio = findViewById(R.id.seekBarAudio);
//        lvQuestions = findViewById(R.id.lvQuestions);
//        btnSubmit = findViewById(R.id.btnSubmit);
//        progressBarAudioLoading = findViewById(R.id.progressBarAudioLoading); // Lấy ProgressBar
//        tvScreenTitle = findViewById(R.id.tvScreenTitle);
//        // --- Đặt trạng thái ban đầu bằng hàm reset ---
//        resetAudioControlsUI();
//    }
//
//    private void setupListView() {
//        questionsList = new ArrayList<>();
//        // Khởi tạo Adapter
//        questionListAdapter = new ListeningQuestionListAdapter(this, questionsList);
//        lvQuestions.setAdapter(questionListAdapter);
//    }
//
//    private void initializeTextToSpeech() {
//        Log.d(TAG, "Initializing TextToSpeech...");
//        try {
//            tts = new TextToSpeech(this, this);
//        } catch (Exception e) {
//            Log.e(TAG, "Exception initializing TTS", e);
//            Toast.makeText(this, "Failed to initialize Text-to-Speech component.", Toast.LENGTH_LONG).show();
//            resetAudioControlsUI(); // Đảm bảo UI ở trạng thái không thể phát
//        }
//    }
//
//    // --- OnInitListener Implementation ---
//    @Override
//    public void onInit(int status) {
//        if (status == TextToSpeech.SUCCESS) {
//            // Ưu tiên tiếng Anh Mỹ, nếu không có thì dùng tiếng Anh chung
//            int result = tts.setLanguage(Locale.US);
//            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                Log.w(TAG, "TTS language US not supported, trying default English.");
//                result = tts.setLanguage(Locale.ENGLISH);
//                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                    Log.e(TAG, "TTS language English is not supported.");
//                    Toast.makeText(this, "Required TTS language not supported.", Toast.LENGTH_SHORT).show();
//                    resetAudioControlsUI();
//                    return;
//                }
//            }
//
//            Log.i(TAG, "TTS Initialized successfully with language: " + (tts.getLanguage() != null ? tts.getLanguage() : "Unknown"));
//            isTtsInitialized = true;
//            // Nếu script đã được tải trong khi TTS đang khởi tạo, bắt đầu tổng hợp ngay
//            if (scriptToSpeak != null && !scriptToSpeak.isEmpty() && audioFile == null) {
//                Log.d(TAG, "TTS ready, starting synthesis from onInit.");
//                synthesizeScriptToFile();
//            } else {
//                Log.d(TAG, "TTS ready, waiting for script or synthesis already in progress/done.");
//            }
//
//        } else {
//            Log.e(TAG, "TTS Initialization failed! Status code: " + status);
//            Toast.makeText(this, "Failed to initialize Text-to-Speech engine (Code: " + status + ").", Toast.LENGTH_SHORT).show();
//            resetAudioControlsUI();
//        }
//    }
//
//    private void loadExerciseDataFromFirebase() {
//        // Đã kiểm tra null các identifier trong onCreate
//
//        DatabaseReference exerciseRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/") // Thay URL nếu cần
//                .getReference("Lessons")
//                .child("Levels")
//                .child(levelName)
//                .child("Listening")
//                .child("Topics")
//                .child(topicTitle)
//                .child("Exercises")
//                .child(exerciseTitle);
//
//        Log.i(TAG, "Loading data from Firebase path: " + exerciseRef.toString());
//
//        // Hiển thị loading indicator ngay khi bắt đầu tải
//        showLoadingIndicator();
//
//        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (!snapshot.exists()) {
//                    Log.e(TAG, "Exercise data not found at the specified Firebase path.");
//                    Toast.makeText(InternalListeningTopic.this, "Exercise data not found.", Toast.LENGTH_SHORT).show();
//                    hideLoadingIndicatorWithError(); // Ẩn loading và reset UI nếu lỗi
//                    finish(); // Đóng activity nếu không có dữ liệu
//                    return;
//                }
//
//                // Lấy script để tổng hợp
//                scriptToSpeak = snapshot.child("script").getValue(String.class);
//                boolean hasScript = scriptToSpeak != null && !scriptToSpeak.isEmpty();
//
//                if (hasScript) {
//                    Log.d(TAG,"Script loaded from Firebase (" + scriptToSpeak.length() + " chars).");
//                    // Chỉ bắt đầu tổng hợp nếu TTS đã sẵn sàng và chưa có file audio
//                    if (isTtsInitialized && audioFile == null) {
//                        Log.d(TAG, "Script loaded, TTS ready, starting synthesis.");
//                        synthesizeScriptToFile(); // Hàm này sẽ tự quản lý ProgressBar
//                    } else if (!isTtsInitialized){
//                        Log.d(TAG, "Script loaded, waiting for TTS initialization to complete.");
//                        // ProgressBar vẫn hiển thị, onInit sẽ gọi synthesizeScriptToFile
//                    } else {
//                        Log.d(TAG, "Script loaded, but synthesis might be already done or in progress.");
//                        // Nếu audioFile đã tồn tại (ví dụ từ cache), setupMediaPlayer sẽ được gọi
//                        if(audioFile != null && audioFile.exists()) {
//                            setupMediaPlayer();
//                        }
//                    }
//                } else {
//                    Log.w(TAG, "'script' field is missing, empty, or not a String in Firebase.");
//                    Toast.makeText(InternalListeningTopic.this, "No audio script found.", Toast.LENGTH_SHORT).show();
//                    hideLoadingIndicatorWithError(); // Ẩn loading, reset UI vì không có audio
//                }
//
//                // Lấy danh sách câu hỏi (luôn thực hiện dù có script hay không)
//                DataSnapshot questionsSnapshot = snapshot.child("questions");
//                if (!questionsSnapshot.exists()) {
//                    Log.w(TAG, "No 'questions' node found in Firebase data.");
//                    if (questionsList.isEmpty()) { // Chỉ Toast nếu chưa có câu hỏi nào
//                        Toast.makeText(InternalListeningTopic.this, "No questions found for this exercise.", Toast.LENGTH_SHORT).show();
//                    }
//                    // Nếu không có script VÀ không có câu hỏi, thì không còn gì để làm
//                    if (!hasScript) {
//                        finish(); // Có thể đóng activity
//                    }
//                } else {
//                    List<ListeningQuestion> loadedQuestions = new ArrayList<>();
//                    int questionCounter = 1;
//                    for (DataSnapshot questionSnap : questionsSnapshot.getChildren()) {
//                        try {
//                            ListeningQuestion question = parseQuestionSnapshot(questionSnap, questionCounter);
//                            if (question != null) {
//                                loadedQuestions.add(question);
//                                questionCounter++;
//                            }
//                        } catch (Exception e) {
//                            Log.e(TAG, "Error parsing question data for key: " + questionSnap.getKey(), e);
//                        }
//                    }
//                    questionsList.clear();
//                    questionsList.addAll(loadedQuestions);
//                    questionListAdapter.updateData(questionsList); // Cập nhật adapter
//                    Log.i(TAG, "Successfully loaded " + questionsList.size() + " questions.");
//                }
//
//                // Nếu không có script, không cần chờ TTS, ẩn loading indicator ở đây
//                if (!hasScript) {
//                    hideLoadingIndicatorWithError(); // Reset audio controls
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e(TAG, "Firebase data loading cancelled or failed: " + error.getMessage(), error.toException());
//                Toast.makeText(InternalListeningTopic.this, "Error loading exercise data: " + error.getMessage(), Toast.LENGTH_LONG).show();
//                hideLoadingIndicatorWithError(); // Ẩn loading và reset UI
//                // Cân nhắc đóng activity hoặc cho phép thử lại
//            }
//        });
//    }
//
//    // Hàm helper để parse dữ liệu câu hỏi từ Firebase Snapshot
//    private ListeningQuestion parseQuestionSnapshot(DataSnapshot questionSnap, int questionNumber) {
//        ListeningQuestion question = new ListeningQuestion();
//        question.setId(questionSnap.getKey()); // Lấy key làm ID
//
//        String text = questionSnap.child("questionText").getValue(String.class);
//        String answer = questionSnap.child("correctAnswer").getValue(String.class);
//
//        // Lấy options một cách an toàn hơn
//        Map<String, String> stringOptionsMap = new HashMap<>();
//        DataSnapshot optionsSnapshot = questionSnap.child("options");
//        if (optionsSnapshot.exists() && optionsSnapshot.getValue() instanceof Map) {
//            try {
//                @SuppressWarnings("unchecked") // Cần thiết cho cast này
//                Map<String, Object> optionsMapObject = (Map<String, Object>) optionsSnapshot.getValue();
//                if (optionsMapObject != null) {
//                    for (Map.Entry<String, Object> entry : optionsMapObject.entrySet()) {
//                        // Chuyển đổi mọi giá trị sang String
//                        stringOptionsMap.put(entry.getKey(), String.valueOf(entry.getValue()));
//                    }
//                }
//            } catch (ClassCastException e) {
//                Log.e(TAG, "Error casting options to Map for question: " + questionSnap.getKey(), e);
//            }
//        } else {
//            Log.w(TAG, "Options node is missing or not a Map for question: " + questionSnap.getKey());
//        }
//        question.setOptions(stringOptionsMap);
//
//        // Kiểm tra dữ liệu cần thiết
//        if (text != null && !text.isEmpty() &&
//                answer != null && !answer.isEmpty() &&
//                !stringOptionsMap.isEmpty()) {
//            question.setQuestionText(text);
//            question.setCorrectAnswer(answer);
//            question.setQuestionNumberText("Question " + questionNumber + ":"); // Set số thứ tự
//            return question;
//        } else {
//            Log.w(TAG, "Skipping question due to missing data: Key=" + questionSnap.getKey() +
//                    ", Text=" + text + ", Answer=" + answer + ", OptionsEmpty=" + stringOptionsMap.isEmpty());
//            return null; // Trả về null nếu thiếu dữ liệu
//        }
//    }
//
//
//    private void synthesizeScriptToFile() {
//        if (!isTtsInitialized) {
//            Log.w(TAG, "Cannot synthesize: TTS not ready.");
//            Toast.makeText(this, "Text-to-Speech engine is not ready.", Toast.LENGTH_SHORT).show();
//            hideLoadingIndicatorWithError(); // Ẩn loading, reset UI
//            return;
//        }
//        if (scriptToSpeak == null || scriptToSpeak.isEmpty()) {
//            Log.w(TAG, "Cannot synthesize: Script is empty or null.");
//            hideLoadingIndicatorWithError(); // Ẩn loading, reset UI
//            return;
//        }
//        // Tránh gọi lại nếu đang tổng hợp hoặc đã có file/player
//        if (audioFile != null && audioFile.exists()){
//            Log.d(TAG, "Synthesis request ignored, audio file already exists or MediaPlayer is set up.");
//            if(mediaPlayer == null) {
//                Log.d(TAG, "Audio file exists but player not ready, attempting setup.");
//                setupMediaPlayer(); // Vẫn cần hiển thị loading khi setup player từ file cũ
//            } else if (isMediaPlayerPrepared) {
//                hideLoadingIndicator(); // Nếu player đã sẵn sàng thì ẩn loading
//            }
//            return;
//        }
//
//        try {
//            File outputDir = getCacheDir();
//            if (!outputDir.exists()) { outputDir.mkdirs(); }
//            String filename = "listening_" + levelName + "_" + topicTitle + "_" + exerciseTitle.hashCode() + ".wav";
//            audioFile = new File(outputDir, filename);
//
//            if (audioFile.exists() && audioFile.length() > 0) {
//                Log.i(TAG, "Using existing synthesized file: " + audioFile.getAbsolutePath());
//                showLoadingIndicator(); // Hiển thị loading khi setup từ file cũ
//                setupMediaPlayer();
//                return;
//            }
//            if (audioFile.exists()) { audioFile.delete(); }
//
//            Log.i(TAG, "Starting TTS synthesis to file: " + audioFile.getAbsolutePath());
//            showLoadingIndicator(); // Hiển thị loading trước khi bắt đầu tổng hợp
//
//            // Đặt listener TRƯỚC KHI gọi synthesizeToFile
//            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
//                @Override
//                public void onStart(String utteranceId) {
//                    Log.d(TAG, "TTS Synthesis started: " + utteranceId);
//                }
//                @Override
//                public void onDone(String utteranceId) {
//                    Log.d(TAG, "TTS Synthesis done: " + utteranceId);
//                    if (SYNTHESIS_UTTERANCE_ID.equals(utteranceId) && !isFinishing()) {
//                        runOnUiThread(() -> {
//                            if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
//                                Log.i(TAG, "Synthesis successful. Setting up MediaPlayer.");
//                                setupMediaPlayer(); // ProgressBar vẫn hiển thị, setupMediaPlayer sẽ xử lý
//                            } else {
//                                Log.e(TAG, "Synthesis done, but file invalid.");
//                                Toast.makeText(InternalListeningTopic.this, "Error creating audio file.", Toast.LENGTH_SHORT);
//                                audioFile = null;
//                                hideLoadingIndicatorWithError();
//                            }
//                        });
//                    }
//                }
//                @Override
//                public void onError(String utteranceId, int errorCode) {
//                    Log.e(TAG, "TTS Synthesis error: " + utteranceId + ", Code: " + errorCode);
//                    if (SYNTHESIS_UTTERANCE_ID.equals(utteranceId) && !isFinishing()) {
//                        runOnUiThread(() -> {
//                            Toast.makeText(InternalListeningTopic.this, "Audio synthesis failed (Error " + errorCode + ")", Toast.LENGTH_LONG);
//                            if (audioFile != null && audioFile.exists()) { audioFile.delete(); }
//                            audioFile = null;
//                            hideLoadingIndicatorWithError();
//                        });
//                    }
//                }
//                @Override
//                public void onError(String utteranceId) {
//                    onError(utteranceId, TextToSpeech.ERROR);
//                }
//            });
//
//            // Bắt đầu tổng hợp
//            HashMap<String, String> params = new HashMap<>();
//            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, SYNTHESIS_UTTERANCE_ID);
//            int result = tts.synthesizeToFile(scriptToSpeak, params, audioFile.getAbsolutePath());
//
//            if (result != TextToSpeech.SUCCESS) {
//                Log.e(TAG, "synthesizeToFile immediate failure. Code: " + result);
//                Toast.makeText(this, "Failed to start audio synthesis (Code: " + result + ").", Toast.LENGTH_SHORT);
//                if (audioFile != null && audioFile.exists()) { audioFile.delete(); }
//                audioFile = null;
//                hideLoadingIndicatorWithError();
//            } else {
//                Log.d(TAG, "synthesizeToFile request submitted.");
//            }
//
//        } catch (Exception e) {
//            Log.e(TAG, "Exception during synthesis", e);
//            Toast.makeText(this, "Error preparing audio: " + e.getMessage(), Toast.LENGTH_SHORT);
//            if (audioFile != null && audioFile.exists()) { audioFile.delete(); }
//            audioFile = null;
//            hideLoadingIndicatorWithError();
//        }
//    }
//
//    private void setupMediaPlayer() {
//        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
//            Log.e(TAG, "Cannot setup MediaPlayer: Audio file invalid.");
//            hideLoadingIndicatorWithError(); // Ẩn loading, reset UI
//            return;
//        }
//
//        // Đảm bảo vẫn hiển thị loading khi bắt đầu setup player
//        showLoadingIndicator();
//
//        releaseMediaPlayer(); // Giải phóng trình phát cũ
//
//        try {
//            Log.d(TAG, "Setting up MediaPlayer source: " + audioFile.getAbsolutePath());
//            mediaPlayer = new MediaPlayer();
//            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
//            mediaPlayer.setOnPreparedListener(this);
//            mediaPlayer.setOnCompletionListener(this);
//            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
//                Log.e(TAG, "MediaPlayer Error: what=" + what + ", extra=" + extra);
//                String errorMsg = "Error playing audio"; // Default message
//                // Add specific messages if needed
//                Toast.makeText(InternalListeningTopic.this, errorMsg + " (Code: " + what + ")", Toast.LENGTH_LONG).show();
//                releaseMediaPlayer();
//                hideLoadingIndicatorWithError(); // Ẩn loading, reset UI
//                return true;
//            });
//            Log.d(TAG, "MediaPlayer prepareAsync called.");
//            mediaPlayer.prepareAsync();
//
//        } catch (Exception e) { // Bắt các Exception có thể xảy ra
//            Log.e(TAG, "Exception setting up MediaPlayer", e);
//            Toast.makeText(this, "Error loading audio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//            releaseMediaPlayer();
//            hideLoadingIndicatorWithError(); // Ẩn loading, reset UI
//        }
//    }
//
//    // --- MediaPlayer.OnPreparedListener ---
//    @Override
//    public void onPrepared(MediaPlayer mp) {
//        if (mediaPlayer == null) {
//            Log.w(TAG, "onPrepared called but MediaPlayer is null.");
//            return;
//        }
//
//        Log.d(TAG, "MediaPlayer prepared.");
//        isMediaPlayerPrepared = true;
//        try {
//            int duration = mediaPlayer.getDuration();
//            if (duration > 0) {
//                Log.d(TAG, "Audio duration: " + duration + "ms");
//                // --- Audio sẵn sàng, ẩn loading, hiện nút Play ---
//                hideLoadingIndicator(); // Ẩn ProgressBar, hiện nút Play và bật controls
//                seekBarAudio.setMax(duration);
//            } else {
//                Log.w(TAG, "MediaPlayer prepared but duration is invalid: " + duration);
//                Toast.makeText(this, "Audio file seems corrupted.", Toast.LENGTH_LONG).show();
//                releaseMediaPlayer();
//                if (audioFile != null && audioFile.exists()) audioFile.delete();
//                audioFile = null;
//                hideLoadingIndicatorWithError(); // Ẩn loading, reset UI
//            }
//        } catch (IllegalStateException e) {
//            Log.e(TAG, "IllegalStateException in onPrepared.", e);
//            hideLoadingIndicatorWithError(); // Ẩn loading, reset UI nếu lỗi
//        }
//    }
//
//    // --- MediaPlayer.OnCompletionListener ---
//    @Override
//    public void onCompletion(MediaPlayer mp) {
//        if (mediaPlayer == null || !isMediaPlayerPrepared) {
//            Log.w(TAG, "onCompletion called but player invalid.");
//            return;
//        }
//
//        Log.d(TAG, "MediaPlayer playback completed.");
//        isPlaying = false;
//        try {
//            runOnUiThread(() -> {
//                if (mediaPlayer != null) {
//                    seekBarAudio.setProgress(mediaPlayer.getDuration());
//                    try {
//                        mediaPlayer.seekTo(0);
//                        seekBarAudio.setProgress(0);
//                    } catch (IllegalStateException seekEx) {
//                        Log.w(TAG, "Seek failed on completion", seekEx);
//                    }
//                }
//                if(btnPlayAudio != null) btnPlayAudio.setImageResource(R.drawable.icon_play_audio); // Reset icon Play
//            });
//            stopSeekBarUpdate();
//        } catch (IllegalStateException e){
//            Log.e(TAG,"IllegalStateException in onCompletion", e);
//            releaseMediaPlayer();
//            runOnUiThread(this::resetAudioControlsUI);
//        }
//    }
//
//    private void addEvents() {
//        if (btnPlayAudio != null) {
//            btnPlayAudio.setOnClickListener(v -> togglePlayPause());
//        }
//        if (btnSubmit != null) {
//            btnSubmit.setOnClickListener(v -> {
//                Log.d(TAG, "Submit button clicked. Current state: " + currentButtonState);
//                switch (currentButtonState) {
//                    case STATE_SUBMIT:
//                        // *** THAY ĐỔI: Gọi hàm kiểm tra trước khi quyết định hiển thị dialog ***
//                        checkAnswersAndShowConfirmationIfNeeded();
//                        break;
//                    case STATE_RETRY:
//                        retryExercise();
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
//        if (seekBarAudio != null) {
//            seekBarAudio.setOnSeekBarChangeListener(this);
//        }
//    }
//
//
//    private void checkAnswersAndShowConfirmationIfNeeded() {
//        Log.d(TAG, "Checking if all answers are provided before showing confirmation...");
//
//        if (questionListAdapter == null || questionsList == null) {
//            Log.e(TAG, "Cannot check answers: Adapter or questions list is null.");
//            Toast.makeText(this, "Error preparing submission.", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        Map<Integer, Integer> userAnswers = questionListAdapter.getSelectedAnswers();
//        int totalQuestions = questionsList.size();
//        boolean allAnswered = true;
//        int firstUnanswered = -1;
//
//        // Kiểm tra nếu không có câu hỏi
//        if (totalQuestions == 0) {
//            Log.w(TAG, "Check: No questions in the list to answer.");
//            Toast.makeText(this, "No questions available in this exercise.", Toast.LENGTH_SHORT).show();
//            if (btnSubmit != null) { // Vô hiệu hóa nút submit nếu không có câu hỏi
//                btnSubmit.setEnabled(false);
//                btnSubmit.setAlpha(0.5f);
//            }
//            return; // Không làm gì thêm
//        }
//
//        // Kiểm tra từng câu hỏi
//        for (int i = 0; i < totalQuestions; i++) {
//            if (userAnswers.getOrDefault(i, -1) == -1) { // -1 là giá trị mặc định nếu chưa chọn
//                allAnswered = false;
//                firstUnanswered = i;
//                Log.d(TAG, "Check: Question at index " + i + " not answered.");
//                break;
//            }
//        }
//
//        if (allAnswered) {
//            // Đã trả lời hết -> Hiển thị Dialog xác nhận
//            Log.d(TAG, "Check: All questions answered. Showing confirmation dialog.");
//            showSubmissionConfirmationDialog();
//        } else {
//            //
//            CustomToast.showFail(this, "Please answer all questions", R.drawable.fail_icon);
//            if (lvQuestions != null && firstUnanswered != -1) {
//                final int scrollToPos = firstUnanswered;
//                lvQuestions.post(() -> lvQuestions.smoothScrollToPosition(scrollToPos));
//                Log.d(TAG,"Check: Scrolling to first unanswered question at index: " + scrollToPos);
//            }
//        }
//    }
//
//    private void showSubmissionConfirmationDialog() {
//        new AlertDialog.Builder(this)
//                .setTitle("Xác nhận nộp bài")
//                .setMessage("Bạn có chắc chắn muốn nộp bài không?")
//                .setPositiveButton("Submit", (dialog, which) -> {
//                    Log.d(TAG, "Submission confirmed via dialog.");
//                    // *** Gọi hàm chứa logic chấm điểm và cập nhật trạng thái ***
//                    proceedWithSubmission();
//                })
//                .setNegativeButton("Cancel", (dialog, which) -> {
//                    Log.d(TAG, "Submission cancelled via dialog.");
//                    dialog.dismiss();
//                })
//                .setCancelable(false)
//                .show();
//    }
//
//    private void proceedWithSubmission() {
//        Log.i(TAG, "Proceeding with submission logic...");
//
//        // Dừng phát nhạc nếu đang phát
//        if (mediaPlayer != null && isPlaying) {
//            try {
//                mediaPlayer.pause();
//                isPlaying = false;
//                if (btnPlayAudio != null) btnPlayAudio.setImageResource(R.drawable.icon_play_audio);
//                stopSeekBarUpdate();
//                Log.d(TAG, "Playback paused before scoring.");
//            } catch (IllegalStateException e) {
//                Log.w(TAG, "Error pausing media before scoring", e);
//            }
//        }
//
//        // Các kiểm tra null cần thiết
//        if (questionListAdapter == null || questionsList == null || questionsList.isEmpty()) {
//            Log.e(TAG, "Cannot proceed with submission: Adapter or questions list invalid.");
//            Toast.makeText(this, "Error processing submission.", Toast.LENGTH_SHORT).show();
//            // Có thể reset nút Submit về trạng thái ban đầu nếu lỗi
//            if (btnSubmit != null) {
//                currentButtonState = STATE_SUBMIT;
//                btnSubmit.setText("Submit");
//                btnSubmit.setEnabled(true);
//                btnSubmit.setAlpha(1.0f);
//            }
//            return;
//        }
//
//        // Lấy lại câu trả lời (đã được xác nhận là đầy đủ)
//        Map<Integer, Integer> userAnswers = questionListAdapter.getSelectedAnswers();
//        int totalQuestions = questionsList.size();
//
//        // --- Chấm điểm và chuẩn bị dữ liệu kết quả ---
//        Log.d(TAG, "Scoring " + totalQuestions + " questions.");
//        int correctCount = 0;
//        Map<Integer, Boolean> correctnessMap = new HashMap<>();
//        for (int i = 0; i < totalQuestions; i++) {
//            // Logic chấm điểm giữ nguyên như trong hàm submitAnswers() cũ của bạn
//            if (i >= questionsList.size()) {
//                Log.e(TAG, "Index out of bounds accessing questionsList at " + i);
//                continue;
//            }
//            ListeningQuestion question = questionsList.get(i);
//            int selectedRadioButtonId = userAnswers.get(i); // Lấy ID đã lưu từ check
//            String selectedAnswerKey = "";
//            if (selectedRadioButtonId != -1) {
//                View selectedRbView = findViewById(selectedRadioButtonId); // Cần findViewByID vì đang ở Activity
//                if (selectedRbView instanceof RadioButton) {
//                    CharSequence tag = ((RadioButton) selectedRbView).getTag() instanceof CharSequence ? (CharSequence)((RadioButton) selectedRbView).getTag() : null;
//                    if (tag != null) {
//                        selectedAnswerKey = tag.toString();
//                    } else {
//                        Log.w(TAG, "RadioButton with ID " + selectedRadioButtonId + " has no tag or tag is not CharSequence! Attempting fallback by ID.");
//                        // Fallback based on ID (less robust)
//                        if (selectedRadioButtonId == R.id.rbOptionA) selectedAnswerKey = "A";
//                        else if (selectedRadioButtonId == R.id.rbOptionB) selectedAnswerKey = "B";
//                        else if (selectedRadioButtonId == R.id.rbOptionC) selectedAnswerKey = "C";
//                        else if (selectedRadioButtonId == R.id.rbOptionD) selectedAnswerKey = "D";
//                        else Log.e(TAG, "Could not determine selected key for RadioButton ID: " + selectedRadioButtonId);
//                    }
//                } else {
//                    Log.e(TAG, "View found by ID "+selectedRadioButtonId+" is not a RadioButton");
//                }
//            } else {
//                // This case should technically not happen if allAnswered was true, but log just in case
//                Log.e(TAG,"Selected RadioButton ID is -1 for question " + i + " during scoring!");
//            }
//
//            boolean isCorrect = false;
//            if (question.getCorrectAnswer() != null && !selectedAnswerKey.isEmpty() && selectedAnswerKey.equalsIgnoreCase(question.getCorrectAnswer())) {
//                correctCount++;
//                isCorrect = true;
//            }
//            correctnessMap.put(i, isCorrect);
//            Log.v(TAG, "Q" + (i + 1) + " (Index " + i + "): Selected Key='" + selectedAnswerKey + "', Correct Key='" + question.getCorrectAnswer() + "', Result=" + isCorrect);
//
//        }
//
//        // --- Hiển thị điểm số tổng quát qua Toast ---
//        String resultMessage = String.format(Locale.getDefault(), "Result: %d / %d correct!", correctCount, totalQuestions);
//        Toast.makeText(this, resultMessage, Toast.LENGTH_LONG).show();
//        Log.i(TAG, "Final Score: " + correctCount + "/" + totalQuestions);
//
//        // --- Gửi kết quả cho Adapter ---
//        questionListAdapter.showResults(userAnswers, correctnessMap);
//
//        // --- Cập nhật trạng thái và text của nút Submit ---
//        // Logic cập nhật nút Submit dựa trên điểm và hasNext giữ nguyên như trong hàm submitAnswers() cũ của bạn
//        boolean allCorrect = (correctCount == totalQuestions);
//        int currentIndex = -1;
//        boolean hasNext = false;
//
//        if (allExerciseTitles != null && !allExerciseTitles.isEmpty() && exerciseTitle != null) {
//            currentIndex = allExerciseTitles.indexOf(exerciseTitle);
//            hasNext = currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1;
//            Log.d(TAG,"Checking for next exercise inside proceedWithSubmission. Index: " + currentIndex + ", List size: " + allExerciseTitles.size() + ", HasNext: " + hasNext);
//        } else {
//            Log.w(TAG,"Cannot determine next exercise status inside proceedWithSubmission - exercise titles list not ready or current title missing.");
//            hasNext = false;
//        }
//
//        if (allCorrect) {
//            if (hasNext) {
//                Log.i(TAG,"All answers correct. Setting button to NEXT.");
//                currentButtonState = STATE_NEXT;
//                if (btnSubmit != null) {
//                    btnSubmit.setText("Next");
//                    btnSubmit.setEnabled(true);
//                    btnSubmit.setAlpha(1.0f);
//                }
//            } else {
//                Log.i(TAG,"All answers correct. This is the last exercise. Setting button to FINISH ! BACK NOW.");
//                currentButtonState = STATE_FINISHED;
//                if (btnSubmit != null) {
//                    btnSubmit.setText("Finish ! Back Now");
//                    btnSubmit.setEnabled(true);
//                    btnSubmit.setAlpha(1.0f);
//                }
//            }
//        } else {
//            Log.i(TAG,"Some answers incorrect. Setting button to RETRY.");
//            currentButtonState = STATE_RETRY;
//            if (btnSubmit != null) {
//                btnSubmit.setText("Retry");
//                btnSubmit.setEnabled(true);
//                btnSubmit.setAlpha(1.0f);
//            }
//        }
//        Log.d(TAG, "Submission process finished. Button state: " + currentButtonState + " ("+(btnSubmit != null ? btnSubmit.getText() : "null")+")");
//    }
//
//
//    private void retryExercise() {
//        Log.i(TAG, "Retry button clicked.");
//
//        // 1. Yêu cầu Adapter reset trạng thái
//        if (questionListAdapter != null) {
//            questionListAdapter.resetQuizState();
//        } else {
//            Log.e(TAG, "Adapter is null, cannot reset state for retry.");
//            // Optionally, try to reload data or show an error
//            Toast.makeText(this, "Error resetting quiz.", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        // 2. Reset trạng thái và text của nút
//        currentButtonState = STATE_SUBMIT;
//        if (btnSubmit != null) {
//            btnSubmit.setText("Submit");
//            btnSubmit.setEnabled(true); // Đảm bảo nút được bật lại
//            btnSubmit.setAlpha(1.0f);
//        }
//
//        // 3. Cuộn ListView lên đầu (tùy chọn)
//        if (lvQuestions != null) {
//            lvQuestions.post(() -> lvQuestions.smoothScrollToPosition(0));
//        }
//
//        Log.d(TAG,"Exercise state reset for retry. Button state: SUBMIT");
//    }
//
//    private void goToNextExercise() {
//        Log.i(TAG, "Attempting to go to the next exercise.");
//
//        if (allExerciseTitles == null || allExerciseTitles.isEmpty()) {
//            Log.e(TAG, "Exercise titles list is not loaded or empty. Cannot determine next exercise.");
//            Toast.makeText(this, "Could not load the exercise list.", Toast.LENGTH_SHORT).show();
//            // Vô hiệu hóa nút hoặc xử lý khác
//            if(btnSubmit != null) {
//                btnSubmit.setText("Error"); // Hoặc "Finished"
//                btnSubmit.setEnabled(false);
//                btnSubmit.setAlpha(0.5f);
//            }
//            return;
//        }
//
//        int currentIndex = allExerciseTitles.indexOf(exerciseTitle);
//        Log.d(TAG, "Current exercise '" + exerciseTitle + "' found at index: " + currentIndex);
//
//        if (currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1) {
//            // Có bài tập tiếp theo
//            String nextExerciseTitle = allExerciseTitles.get(currentIndex + 1);
//            Log.i(TAG, "Next exercise title: " + nextExerciseTitle);
//
//            // Tạo Intent để mở lại Activity này với bài tập mới
//            Intent nextIntent = new Intent(InternalListeningTopic.this, InternalListeningTopic.class);
//            nextIntent.putExtra("LEVEL_NAME", levelName);
//            nextIntent.putExtra("TOPIC_TITLE", topicTitle);
//            nextIntent.putExtra("EXERCISE_TITLE", nextExerciseTitle);
//            // QUAN TRỌNG: Đặt cờ để xóa Activity hiện tại khỏi stack nếu cần,
//            // nhưng thường thì chỉ cần finish() là đủ cho luồng đơn giản này.
//            // nextIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT); // Xem xét nếu cần trả kết quả về
//
//            startActivity(nextIntent);
//            finish(); // Đóng Activity hiện tại
//
//        } else {
//            // Không tìm thấy bài hiện tại hoặc đã là bài cuối cùng
//            if (currentIndex == -1) {
//                Log.e(TAG, "Current exercise title '" + exerciseTitle + "' not found in the loaded list!");
//                Toast.makeText(this, "Error: Could not find current exercise in list.", Toast.LENGTH_LONG).show();
//            } else {
//                Log.i(TAG, "This is the last exercise in the topic.");
//                CustomToast.showSuccess(this, "Congratulations! You've completed all exercises in this topic!" , R.drawable.success);
//            }
//            // Cập nhật nút Submit thành "Finished" hoặc trạng thái tương tự và vô hiệu hóa
//            if (btnSubmit != null) {
//                btnSubmit.setText("Finished!");
//                btnSubmit.setEnabled(false);
//                btnSubmit.setAlpha(0.5f);
//                currentButtonState = -1; // Đặt trạng thái không xác định hoặc hoàn thành
//            }
//        }
//    }
//
//    // --- Logic Play/Pause cho MediaPlayer ---
//    private void togglePlayPause() {
//        if (progressBarAudioLoading != null && progressBarAudioLoading.getVisibility() == View.VISIBLE) {
//            Log.d(TAG, "Play button clicked while loading, ignoring.");
//            Toast.makeText(this, "Audio is loading...", Toast.LENGTH_SHORT).show();
//            return; // Không làm gì khi đang load
//        }
//
//        if (!isMediaPlayerPrepared || mediaPlayer == null) {
//            Log.w(TAG, "togglePlayPause called but MediaPlayer not prepared or null.");
//            // Thử setup lại nếu có file và player chưa sẵn sàng (và không đang load)
//            if(audioFile != null && audioFile.exists() && mediaPlayer == null){
//                Log.d(TAG, "Attempting to re-setup MediaPlayer on play click.");
//                setupMediaPlayer(); // Hàm này sẽ tự hiển thị loading
//            } else {
//                Toast.makeText(this, "Audio not ready.", Toast.LENGTH_SHORT).show();
//            }
//            return;
//        }
//
//        try {
//            if (isPlaying) { // Đang phát -> Tạm dừng
//                mediaPlayer.pause();
//                isPlaying = false;
//                btnPlayAudio.setImageResource(R.drawable.icon_play_audio);
//                stopSeekBarUpdate();
//                Log.d(TAG, "Playback paused at " + mediaPlayer.getCurrentPosition() + "ms");
//            } else { // Đang dừng/hoàn thành -> Phát (lại)
//                mediaPlayer.start();
//                isPlaying = true;
//                btnPlayAudio.setImageResource(R.drawable.ic_pause); // Đổi icon thành Pause
//                startSeekBarUpdate();
//                Log.d(TAG, "Playback started/resumed from " + mediaPlayer.getCurrentPosition() + "ms");
//            }
//        } catch (IllegalStateException e){
//            Log.e(TAG, "IllegalStateException during togglePlayPause", e);
//            Toast.makeText(this, "Error controlling playback.", Toast.LENGTH_SHORT).show();
//            releaseMediaPlayer();
//            runOnUiThread(this::resetAudioControlsUI);
//        }
//    }
//
//    // --- Cập nhật SeekBar bằng Handler ---
//    private void startSeekBarUpdate() {
//        stopSeekBarUpdate(); // Clear previous runnable if exists
//        updateSeekBarRunnable = new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    if (mediaPlayer != null && isPlaying && isMediaPlayerPrepared) {
//                        int currentPosition = mediaPlayer.getCurrentPosition();
//                        seekBarAudio.setProgress(currentPosition);
//                        progressHandler.postDelayed(this, 300); // Update roughly 3 times per second
//                    } else {
//                        stopSeekBarUpdate(); // Stop if conditions are no longer met
//                    }
//                } catch (IllegalStateException e){
//                    Log.w(TAG, "IllegalStateException in updateSeekBarRunnable.", e);
//                    stopSeekBarUpdate();
//                } catch (Exception e) { // Catch any other potential exceptions
//                    Log.e(TAG, "Exception in updateSeekBarRunnable", e);
//                    stopSeekBarUpdate();
//                }
//            }
//        };
//        progressHandler.post(updateSeekBarRunnable); // Start the update loop
//    }
//
//    private void stopSeekBarUpdate() {
//        if (updateSeekBarRunnable != null) {
//            progressHandler.removeCallbacks(updateSeekBarRunnable);
//            updateSeekBarRunnable = null; // Clear the reference
//        }
//    }
//
//    // --- SeekBar.OnSeekBarChangeListener Implementation ---
//    @Override
//    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//        // No action needed here while just displaying progress,
//        // but could be used for showing time labels etc.
//    }
//
//    @Override
//    public void onStartTrackingTouch(SeekBar seekBar) {
//        // User started dragging the thumb
//        stopSeekBarUpdate(); // Pause automatic updates while user is controlling
//        Log.d(TAG, "SeekBar tracking started.");
//    }
//
//    @Override
//    public void onStopTrackingTouch(SeekBar seekBar) {
//        // User finished dragging the thumb
//        Log.d(TAG, "SeekBar tracking stopped at: " + seekBar.getProgress());
//        if (mediaPlayer != null && isMediaPlayerPrepared) {
//            try {
//                mediaPlayer.seekTo(seekBar.getProgress()); // Move playback position
//                if (isPlaying) {
//                    startSeekBarUpdate(); // Resume automatic updates if it was playing
//                } else {
//                    // If paused, just update the progress visually one last time
//                    // seekTo should have updated internal position, get it again
//                    seekBarAudio.setProgress(mediaPlayer.getCurrentPosition());
//                }
//            } catch (IllegalStateException e){
//                Log.e(TAG, "IllegalStateException during seekTo", e);
//                Toast.makeText(this, "Error seeking audio.", Toast.LENGTH_SHORT).show();
//                releaseMediaPlayer();
//                runOnUiThread(this::resetAudioControlsUI);
//            }
//        } else {
//            Log.w(TAG, "Cannot seek: MediaPlayer invalid.");
//        }
//    }
//    // --- End SeekBar.OnSeekBarChangeListener ---
//
//    private void submitAnswers() {
//        Log.i(TAG, "Submit process started (State: SUBMIT).");
//
//        // Dừng phát nhạc nếu đang phát
//        if (mediaPlayer != null && isPlaying) {
//            try {
//                mediaPlayer.pause();
//                isPlaying = false; // Update state flag
//                if (btnPlayAudio != null) btnPlayAudio.setImageResource(R.drawable.icon_play_audio);
//                stopSeekBarUpdate(); // Stop seekbar updates
//                Log.d(TAG, "Playback paused on submit.");
//            } catch (IllegalStateException e) {
//                Log.w(TAG, "Error pausing media on submit", e);
//            }
//        }
//
//        // ... (Toàn bộ logic còn lại của hàm submitAnswers() giữ nguyên) ...
//        // Kiểm tra Adapter
//        if (questionListAdapter == null) {
//            Log.e(TAG, "Adapter is null, cannot submit.");
//            Toast.makeText(this, "Error: Cannot process answers.", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        // Lấy câu trả lời từ Adapter
//        Map<Integer, Integer> userAnswers = questionListAdapter.getSelectedAnswers();
//        int totalQuestions = questionsList.size();
//
//        // Kiểm tra nếu không có câu hỏi
//        if (totalQuestions == 0) {
//            Log.w(TAG, "No questions to submit.");
//            Toast.makeText(this, "No questions available in this exercise.", Toast.LENGTH_SHORT).show();
//            if (btnSubmit != null) {
//                btnSubmit.setEnabled(false);
//                btnSubmit.setAlpha(0.5f);
//            }
//            return;
//        }
//
//        // --- Kiểm tra tất cả câu hỏi đã được trả lời chưa ---
//        boolean allAnswered = true;
//        int firstUnanswered = -1;
//        for (int i = 0; i < totalQuestions; i++) {
//            if (userAnswers.getOrDefault(i, -1) == -1) {
//                allAnswered = false;
//                firstUnanswered = i;
//                Log.d(TAG, "Question at index " + i + " not answered.");
//                break;
//            }
//        }
//
//        if (!allAnswered) {
//            CustomToast.showFail(this, "Please answer all questions!", R.drawable.fail_icon);
//            final int scrollToPos = firstUnanswered;
//            if (lvQuestions != null && scrollToPos != -1) {
//                lvQuestions.post(() -> lvQuestions.smoothScrollToPosition(scrollToPos));
//                Log.d(TAG,"Scrolling to first unanswered question at index: " + scrollToPos);
//            }
//            return;
//        }
//
//        // --- Chấm điểm và chuẩn bị dữ liệu kết quả ---
//        Log.d(TAG, "All questions answered. Scoring " + totalQuestions + " questions.");
//        int correctCount = 0;
//        Map<Integer, Boolean> correctnessMap = new HashMap<>();
//        for (int i = 0; i < totalQuestions; i++) {
//            if (i >= questionsList.size()) {
//                Log.e(TAG, "Index out of bounds accessing questionsList at " + i);
//                continue;
//            }
//            ListeningQuestion question = questionsList.get(i);
//            int selectedRadioButtonId = userAnswers.get(i);
//            String selectedAnswerKey = "";
//            if (selectedRadioButtonId != -1) {
//                View selectedRbView = findViewById(selectedRadioButtonId);
//                if (selectedRbView instanceof RadioButton) {
//                    CharSequence tag = ((RadioButton) selectedRbView).getTag() instanceof CharSequence ? (CharSequence)((RadioButton) selectedRbView).getTag() : null;
//                    if (tag != null) {
//                        selectedAnswerKey = tag.toString();
//                    } else {
//                        Log.w(TAG, "RadioButton with ID " + selectedRadioButtonId + " has no tag or tag is not CharSequence!");
//                        if (selectedRadioButtonId == R.id.rbOptionA) selectedAnswerKey = "A";
//                        else if (selectedRadioButtonId == R.id.rbOptionB) selectedAnswerKey = "B";
//                        else if (selectedRadioButtonId == R.id.rbOptionC) selectedAnswerKey = "C";
//                        else if (selectedRadioButtonId == R.id.rbOptionD) selectedAnswerKey = "D";
//                    }
//                }
//            } else {
//                Log.e(TAG,"Selected RadioButton ID is -1 for question " + i + " despite passing allAnswered check!");
//            }
//            boolean isCorrect = false;
//            if (question.getCorrectAnswer() != null && selectedAnswerKey.equalsIgnoreCase(question.getCorrectAnswer())) {
//                correctCount++;
//                isCorrect = true;
//            }
//            correctnessMap.put(i, isCorrect);
//            Log.v(TAG, "Q" + (i + 1) + " (Index " + i + "): Selected Key='" + selectedAnswerKey + "', Correct Key='" + question.getCorrectAnswer() + "', Result=" + isCorrect);
//        }
//
//        // --- Hiển thị điểm số tổng quát qua Toast ---
//        String resultMessage = String.format(Locale.getDefault(), "Result: %d / %d correct!", correctCount, totalQuestions);
//        Toast.makeText(this, resultMessage, Toast.LENGTH_LONG).show();
//        Log.i(TAG, "Final Score: " + correctCount + "/" + totalQuestions);
//
//        // --- Gửi kết quả cho Adapter ---
//        questionListAdapter.showResults(userAnswers, correctnessMap);
//
//        // --- Cập nhật trạng thái và text của nút Submit ---
//        boolean allCorrect = (correctCount == totalQuestions);
//        int currentIndex = -1;
//        boolean hasNext = false;
//
//        if (allExerciseTitles != null && !allExerciseTitles.isEmpty() && exerciseTitle != null) {
//            currentIndex = allExerciseTitles.indexOf(exerciseTitle);
//            hasNext = currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1;
//            Log.d(TAG,"Checking for next exercise inside submitAnswers. Index: " + currentIndex + ", List size: " + allExerciseTitles.size() + ", HasNext: " + hasNext);
//        } else {
//            Log.w(TAG,"Cannot determine next exercise status inside submitAnswers - exercise titles list not ready or current title missing.");
//            hasNext = false;
//        }
//
//        // Quyết định trạng thái nút Submit
//        if (allCorrect) {
//            if (hasNext) {
//                Log.i(TAG,"All answers correct. Setting button to NEXT.");
//                currentButtonState = STATE_NEXT;
//                if (btnSubmit != null) {
//                    btnSubmit.setText("Next");
//                    btnSubmit.setEnabled(true);
//                    btnSubmit.setAlpha(1.0f);
//                }
//            } else {
//                // Đúng hết VÀ KHÔNG còn bài tiếp theo
//                Log.i(TAG,"All answers correct. This is the last exercise. Setting button to FINISH ! BACK NOW.");
//                currentButtonState = STATE_FINISHED; // Trạng thái hoàn thành topic
//                if (btnSubmit != null) {
//                    btnSubmit.setText("Finish ! Back Now"); // *** THAY ĐỔI TEXT ***
//                    btnSubmit.setEnabled(true);         // *** GIỮ NÚT BẬT ***
//                    btnSubmit.setAlpha(1.0f);          // *** GIỮ NÚT RÕ RÀNG ***
//                }
//            }
//        } else {
//            // Có câu trả lời sai
//            Log.i(TAG,"Some answers incorrect. Setting button to RETRY.");
//            currentButtonState = STATE_RETRY;
//            if (btnSubmit != null) {
//                btnSubmit.setText("Retry");
//                btnSubmit.setEnabled(true);
//                btnSubmit.setAlpha(1.0f);
//            }
//        }
//        Log.d(TAG, "Submit process finished. Button state: " + currentButtonState + " ("+(btnSubmit != null ? btnSubmit.getText() : "null")+")");
//    }
//
//    // --- Lifecycle Methods ---
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        Log.d(TAG,"onPause called.");
//        // Pause audio playback if playing
//        if (mediaPlayer != null && isPlaying) {
//            try {
//                mediaPlayer.pause();
//            } catch (IllegalStateException e){
//                Log.e(TAG,"Error pausing media on pause.",e);
//            }
//            // UI update (icon) will be handled in onResume to ensure correct state upon return
//            stopSeekBarUpdate();
//            Log.d(TAG, "Playback paused due to onPause.");
//        }
//        // Stop any ongoing TTS synthesis or speech
//        if (tts != null) {
//            try {
//                tts.stop();
//            } catch (Exception e) {
//                Log.w(TAG, "Error stopping TTS on pause", e);
//            }
//        }
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        Log.d(TAG,"onResume called.");
//        // Restore the UI state of the play/pause button and seek bar updates
//        if (mediaPlayer != null && isMediaPlayerPrepared) {
//            // Check the actual player state, as it might have changed (e.g., finished while paused)
//            try {
//                isPlaying = mediaPlayer.isPlaying();
//            } catch (IllegalStateException e) {
//                Log.w(TAG, "Error checking isPlaying onResume", e);
//                isPlaying = false; // Assume not playing if state is invalid
//                // Consider resetting UI or player if state is bad
//            }
//
//            // Only update UI if audio isn't currently loading
//            if (progressBarAudioLoading == null || progressBarAudioLoading.getVisibility() == View.GONE) {
//                if (isPlaying) {
//                    if (btnPlayAudio != null) btnPlayAudio.setImageResource(R.drawable.ic_pause);
//                    startSeekBarUpdate(); // Resume seekbar updates
//                } else {
//                    if (btnPlayAudio != null) btnPlayAudio.setImageResource(R.drawable.icon_play_audio);
//                    try {
//                        // Update seekbar to the current position if paused
//                        if(seekBarAudio != null && mediaPlayer != null) { // Add null check for mediaPlayer
//                            seekBarAudio.setProgress(mediaPlayer.getCurrentPosition());
//                        }
//                    } catch(IllegalStateException e) {
//                        Log.w(TAG, "Error setting progress onResume", e);
//                        // Handle potential error if player is in a bad state
//                    }
//                }
//            }
//        } else if (progressBarAudioLoading != null && progressBarAudioLoading.getVisibility() == View.GONE) {
//            // If the player is not ready AND we are not loading, ensure UI is in the reset state
//            resetAudioControlsUI();
//        }
//    }
//
//
//    @Override
//    protected void onStop() {
//        super.onStop();
//        Log.d(TAG,"onStop called.");
//        // Release potentially heavy resources if the activity is not visible for long.
//        // Consider if releasing the MediaPlayer here is desired, or just in onDestroy.
//        // If released here, it needs to be re-initialized in onStart or onResume.
//        // For simplicity, current logic releases only in onDestroy.
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        Log.i(TAG,"onDestroy called. Releasing resources.");
//
//        // 1. Release MediaPlayer
//        releaseMediaPlayer(); // Handles stopping, resetting, releasing
//
//        // 2. Shutdown TextToSpeech
//        if (tts != null) {
//            Log.d(TAG,"Shutting down TTS engine.");
//            try {
//                tts.stop(); // Stop any ongoing speech/synthesis
//                tts.shutdown(); // Release TTS resources
//            } catch (Exception e) {
//                Log.e(TAG,"TTS shutdown exception", e);
//            }
//            tts = null; // Clear reference
//            isTtsInitialized = false;
//        }
//
//        // 3. Delete temporary audio file
//        if (audioFile != null && audioFile.exists()) {
//            Log.d(TAG, "Deleting temp file: " + audioFile.getName());
//            if (!audioFile.delete()) {
//                Log.w(TAG, "Failed to delete temp audio file: " + audioFile.getAbsolutePath());
//            }
//            audioFile = null; // Clear reference
//        }
//
//        // 4. Stop Handler callbacks
//        stopSeekBarUpdate(); // Ensure the runnable is removed
//        if (progressHandler != null) {
//            progressHandler.removeCallbacksAndMessages(null); // Remove any pending messages/runnables
//        }
//
//        Log.i(TAG,"onDestroy finished.");
//    }
//
//    // --- Phương thức giải phóng MediaPlayer ---
//    private void releaseMediaPlayer() {
//        Log.d(TAG,"Releasing MediaPlayer resources...");
//        stopSeekBarUpdate(); // Stop seekbar updates first
//
//        if (mediaPlayer != null) {
//            try {
//                if (mediaPlayer.isPlaying()) {
//                    mediaPlayer.stop(); // Stop playback
//                }
//                mediaPlayer.reset(); // Reset to idle state
//                mediaPlayer.release(); // Release system resources
//                Log.i(TAG, "MediaPlayer released.");
//            } catch (Exception e){ // Catch IllegalStateException or others
//                Log.e(TAG, "Exception releasing MediaPlayer", e);
//            } finally {
//                mediaPlayer = null; // Always set to null after attempting release
//            }
//        } else {
//            Log.d(TAG,"MediaPlayer already null.");
//        }
//        // Reset state flags
//        isMediaPlayerPrepared = false;
//        isPlaying = false;
//        // Don't reset the whole UI here; other parts of the code call resetAudioControlsUI when needed
//    }
//
//    // --- Hàm helper để reset UI điều khiển audio ---
//    private void resetAudioControlsUI(){
//        Log.d(TAG, "Resetting audio controls UI to initial state.");
//        // Ensure UI updates happen on the main thread
//        runOnUiThread(()-> {
//            // Hide ProgressBar
//            if (progressBarAudioLoading != null) {
//                progressBarAudioLoading.setVisibility(View.GONE);
//            }
//            // Show Play button, set default icon, and disable it
//            if (btnPlayAudio != null) {
//                btnPlayAudio.setVisibility(View.VISIBLE);
//                btnPlayAudio.setImageResource(R.drawable.icon_play_audio); // Default icon
//                btnPlayAudio.setEnabled(false); // Disabled initially
//            }
//            // Disable SeekBar and reset progress
//            if (seekBarAudio != null) {
//                seekBarAudio.setEnabled(false); // Disabled initially
//                seekBarAudio.setProgress(0);    // Reset progress
//            }
//        });
//    }
//
//    // --- Hàm helper để hiển thị loading ---
//    private void showLoadingIndicator() {
//        runOnUiThread(() -> {
//            if (progressBarAudioLoading != null) progressBarAudioLoading.setVisibility(View.VISIBLE);
//            if (btnPlayAudio != null) btnPlayAudio.setVisibility(View.INVISIBLE); // Hide play button
//            // Disable controls while loading
//            if (seekBarAudio != null) seekBarAudio.setEnabled(false);
//            if (btnPlayAudio != null) btnPlayAudio.setEnabled(false); // Disable hidden button too
//        });
//    }
//
//    // --- Hàm helper để ẩn loading khi thành công ---
//    private void hideLoadingIndicator() {
//        runOnUiThread(() -> {
//            if (progressBarAudioLoading != null) progressBarAudioLoading.setVisibility(View.GONE);
//            if (btnPlayAudio != null) {
//                btnPlayAudio.setVisibility(View.VISIBLE); // Show play button
//                btnPlayAudio.setImageResource(R.drawable.icon_play_audio); // Set default icon (might change later if playing)
//                btnPlayAudio.setEnabled(true); // Enable interaction
//            }
//            if (seekBarAudio != null) seekBarAudio.setEnabled(true); // Enable interaction
//        });
//    }
//
//    // --- Hàm helper để ẩn loading khi có lỗi ---
//    private void hideLoadingIndicatorWithError() {
//        // This effectively resets the UI to its initial, disabled state
//        resetAudioControlsUI();
//    }
//
//    @Override
//    public void onBackPressed() {
//        Log.d(TAG,"onBackPressed called.");
//        // Gracefully stop ongoing processes before exiting
//
//        // Pause MediaPlayer if playing
//        if (mediaPlayer != null && isPlaying) {
//            try {
//                mediaPlayer.pause();
//            } catch (IllegalStateException e) {
//                Log.w(TAG,"Error pausing on back press",e);
//            }
//        }
//
//        // Stop TTS if speaking/synthesizing
//        if (tts != null) {
//            try {
//                tts.stop();
//            } catch (Exception e) {
//                Log.w(TAG, "Error stopping TTS on back press", e);
//            }
//        }
//
//        // Stop handler callbacks
//        stopSeekBarUpdate();
//
//        // Release MediaPlayer resources (optional but good practice before finishing)
//        // Note: onDestroy will also call releaseMediaPlayer, but doing it here ensures
//        // it happens even if super.onBackPressed() somehow skips onDestroy quickly.
//        // releaseMediaPlayer(); // Consider if needed here in addition to onDestroy
//
//        super.onBackPressed(); // Perform the default back action (finish activity)
//        Log.d(TAG,"Activity finishing via back press.");
//    }
//
//
//    private void loadAllExerciseTitlesFromFirebase() {
//        if (levelName == null || topicTitle == null) {
//            Log.e(TAG, "Cannot load exercise titles: Level or Topic name is null.");
//            // Có thể disable nút Next hoặc xử lý lỗi khác
//            return;
//        }
//
//        allExerciseTitles = new ArrayList<>(); // Khởi tạo list
//
//        DatabaseReference exercisesRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
//                .getReference("Lessons")
//                .child("Levels")
//                .child(levelName)
//                .child("Listening")
//                .child("Topics")
//                .child(topicTitle)
//                .child("Exercises"); // Tham chiếu đến node chứa TẤT CẢ exercises
//
//        Log.d(TAG, "Loading all exercise titles from: " + exercisesRef.toString());
//
//        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (snapshot.exists()) {
//                    allExerciseTitles.clear(); // Xóa list cũ (nếu có)
//                    for (DataSnapshot exerciseSnap : snapshot.getChildren()) {
//                        String title = exerciseSnap.getKey();
//                        if (title != null && !title.isEmpty()) {
//                            allExerciseTitles.add(title);
//                            Log.v(TAG, "Found exercise title: " + title);
//                        }
//                    }
//                    Log.i(TAG, "Loaded " + allExerciseTitles.size() + " exercise titles for topic: " + topicTitle);
//                    updateSubmitButtonStateAfterTitlesLoaded();
//
//                } else {
//                    Log.w(TAG, "No exercises found under topic: " + topicTitle);
//                    // Không có bài tập nào khác, có thể xử lý ở đây
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e(TAG, "Failed to load exercise titles: " + error.getMessage());
//                Toast.makeText(InternalListeningTopic.this, "Error loading exercise list.", Toast.LENGTH_SHORT).show();
//                // Có thể vô hiệu hóa chức năng "Next"
//            }
//        });
//    }
//
//    private void updateSubmitButtonStateAfterTitlesLoaded() {
//        if (questionListAdapter == null || btnSubmit == null) {
//            Log.w(TAG,"Cannot update submit button state: Adapter or Button is null.");
//            return;
//        }
//
//        if (currentButtonState != STATE_SUBMIT) {
//            Log.d(TAG, "Attempting to update button state after titles loaded. Current state: " + currentButtonState);
//
//            int currentIndex = -1;
//            boolean hasNext = false;
//            if (allExerciseTitles != null && !allExerciseTitles.isEmpty() && exerciseTitle != null) {
//                currentIndex = allExerciseTitles.indexOf(exerciseTitle);
//                hasNext = currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1;
//            } else {
//                hasNext = false;
//                Log.w(TAG,"Exercise titles list is null, empty or title is missing when updating button state.");
//            }
//
//            boolean allCorrect = questionListAdapter.areAllAnswersCorrect();
//            Log.d(TAG, "Updating button state check: CurrentIndex=" + currentIndex + ", HasNext=" + hasNext + ", AllCorrect=" + allCorrect + ", CurrentState=" + currentButtonState);
//
//            if (currentButtonState == STATE_NEXT || currentButtonState == STATE_FINISHED) {
//                if (allCorrect) {
//                    if (hasNext) {
//                        Log.d(TAG, "Re-confirming button state to NEXT.");
//                        currentButtonState = STATE_NEXT;
//                        btnSubmit.setText("Next");
//                        btnSubmit.setEnabled(true);
//                        btnSubmit.setAlpha(1.0f);
//                    } else {
//                        // Đúng hết nhưng không có bài tiếp theo -> Đảm bảo là FINISHED
//                        Log.d(TAG, "Re-confirming button state to FINISH ! BACK NOW.");
//                        currentButtonState = STATE_FINISHED;
//                        btnSubmit.setText("Finish ! Back Now"); // *** THAY ĐỔI TEXT ***
//                        btnSubmit.setEnabled(true);         // *** GIỮ NÚT BẬT ***
//                        btnSubmit.setAlpha(1.0f);          // *** GIỮ NÚT RÕ RÀNG ***
//                    }
//                } else {
//                    Log.w(TAG, "State was NEXT/FINISHED but not all answers are correct! Resetting to RETRY.");
//                    currentButtonState = STATE_RETRY;
//                    btnSubmit.setText("Retry");
//                    btnSubmit.setEnabled(true);
//                    btnSubmit.setAlpha(1.0f);
//                }
//            }
//            else if (currentButtonState == STATE_RETRY) {
//                Log.d(TAG, "Button state is RETRY, no update needed after title load.");
//            }
//        } else {
//            Log.d(TAG, "Button state is still SUBMIT, no update needed after title load.");
//        }
//    }
//}
//
//
//
//

package com.example.langhexx.View;

import android.content.Context; // Added for ViewInterface
import android.content.Intent;
// Removed MediaPlayer, TextToSpeech, UtteranceProgressListener imports as they are handled by Controller
import android.os.Bundle;
// Removed Handler import
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull; // Keep for potential future use? (Not strictly needed now)
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.langhexx.Controller.ListeningController; // Import the Controller
import com.example.langhexx.Controller.ListeningQuestionListAdapter;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.ListeningQuestion;
import com.example.langhexx.R;

// Removed Firebase imports as they are handled by Controller
// Removed File, IOException, Locale, UUID imports

import java.util.ArrayList; // Keep for initializing list
import java.util.HashMap; // Keep for getAdapterSelectedAnswers return type
import java.util.List;
import java.util.Map;


// Implement the ViewInterface from the Controller
public class InternalListeningTopic extends AppCompatActivity implements
        ListeningController.ViewInterface, // Implement the controller's interface
        SeekBar.OnSeekBarChangeListener { // Keep for seekbar interaction delegation

    private static final String TAG = "InternalListenTopicVIEW"; // Changed TAG for clarity

    private ImageView imgClose;

    // --- UI Elements ---
    private ImageButton btnPlayAudio;
    private SeekBar seekBarAudio;
    private ListView lvQuestions;
    private Button btnSubmit;
    private ProgressBar progressBarAudioLoading;
    private TextView tvScreenTitle; // Renamed for clarity (was tvExerciseDisplayTitle previously often)
    // Removed instructionText as it wasn't used in the original listening activity provided

    // --- Adapter (Managed by View for UI binding) ---
    // private List<ListeningQuestion> questionsList; // Controller sends data, Adapter holds its own copy
    private ListeningQuestionListAdapter questionListAdapter;

    // --- Controller ---
    private ListeningController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_listening_topic);
        Log.d(TAG, "onCreate");

        addControls(); // Find UI elements
        setupListView(); // Setup adapter (initially empty)

        // Initialize Controller, passing the View (this) and Intent
        // The controller will handle loading data and initial setup logic
        controller = new ListeningController(this, getIntent());
        controller.initialize(); // Start loading data, TTS init, etc.

        addEvents(); // Setup listeners to delegate actions to controller
    }

    private void addControls() {
        Log.d(TAG, "addControls");
        btnPlayAudio = findViewById(R.id.btnPlayAudio);
        seekBarAudio = findViewById(R.id.seekBarAudio);
        lvQuestions = findViewById(R.id.lvQuestions);
        btnSubmit = findViewById(R.id.btnSubmit);
        progressBarAudioLoading = findViewById(R.id.progressBarAudioLoading);
        tvScreenTitle = findViewById(R.id.tvScreenTitle); // Make sure ID is correct in layout
        imgClose = findViewById(R.id.imgBackward);

        // Initial UI state set by controller via resetAudioControls() and setUIElementsVisibility(false)
    }

    private void setupListView() {
        Log.d(TAG, "setupListView");
        // Adapter needs an initial empty list
        questionListAdapter = new ListeningQuestionListAdapter(this, new ArrayList<>());
        lvQuestions.setAdapter(questionListAdapter);
    }

    private void addEvents() {
        Log.d(TAG, "addEvents");
        if (btnPlayAudio != null) {
            btnPlayAudio.setOnClickListener(v -> {
                if (controller != null) {
                    controller.togglePlayPause(); // Delegate to controller
                }
            });
        }
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                if (controller != null) {
                    controller.onSubmitButtonClicked(); // Delegate to controller
                }
            });
        }
        if (seekBarAudio != null) {
            seekBarAudio.setOnSeekBarChangeListener(this); // Keep 'this' as listener initially
        }

        imgClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    // --- Implementation of ListeningController.ViewInterface ---

    @Override
    public Context getContext() {
        return this; // Provide activity context when controller needs it
    }

    @Override
    public void displayExerciseTitle(String title) {
        Log.d(TAG, "displayExerciseTitle: " + title);
        runOnUiThread(() -> {
            if (tvScreenTitle != null) {
                tvScreenTitle.setText(title != null ? title : "Exercise"); // Set title, provide default
            }
        });
    }

    @Override
    public void updateAdapterData(List<ListeningQuestion> newQuestions) {
        Log.d(TAG, "updateAdapterData: Received " + (newQuestions != null ? newQuestions.size() : 0) + " questions.");
        runOnUiThread(() -> {
            if (questionListAdapter != null && newQuestions != null) {
                questionListAdapter.updateData(newQuestions); // Update adapter's data
                Log.d(TAG, "Adapter notified with " + newQuestions.size() + " questions.");
                // Visibility of list itself is handled by setUIElementsVisibility
            } else {
                Log.w(TAG, "Adapter is null or newQuestions is null, cannot update UI.");
                if (questionListAdapter != null) {
                    questionListAdapter.updateData(new ArrayList<>()); // Clear adapter if null data
                }
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
    public void setSubmitButtonState(int state, String text, boolean enabled) {
        Log.d(TAG, "setSubmitButtonState: State=" + state + ", Text=" + text);
        runOnUiThread(() -> {
            if (btnSubmit != null) {
                btnSubmit.setText(text);
                // Determine enabled state based on common patterns (adjust if controller needs more fine-grained control)
                // More robust: Controller could pass enabled flag separately if needed
                btnSubmit.setEnabled(enabled);
                btnSubmit.setAlpha(enabled ? 1.0f : 0.5f); // Visual cue for disabled
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
        // This method might not be needed if the adapter's internal check isn't used by the controller
        // If controller relies on its own scoring, this could be removed from interface/activity.
        // Keeping it for now as it was in Reading example.
        if (questionListAdapter != null) {
            return questionListAdapter.areAllAnswersCorrect();
        }
        Log.e(TAG, "areAdapterAnswersAllCorrect: Adapter is null");
        return false;
    }

    @Override
    public void showToast(String message) {
        Log.d(TAG, "showToast: " + message);
        runOnUiThread(() -> Toast.makeText(InternalListeningTopic.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void showFailToast(String message) {
        Log.d(TAG, "showFailToast: " + message);
        // Ensure you have the CustomToast class and fail_icon drawable
        runOnUiThread(() -> CustomToast.showFail(InternalListeningTopic.this, message, R.drawable.fail_icon));
    }

    @Override
    public void showConfirmationDialog(String title, String message, Runnable onConfirm) {
        Log.d(TAG, "showConfirmationDialog");
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Submit", (dialog, which) -> onConfirm.run()) // Execute Runnable on confirm
                .setNegativeButton("Cancel", (dialog, which) -> Log.d(TAG, "Submission cancelled."))
                .setCancelable(false) // Prevent dismissing by tapping outside
                .show());
    }

    @Override
    public void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle) {
        Log.i(TAG, "navigateToNextExercise: " + nextExerciseTitle);
        Intent nextIntent = new Intent(InternalListeningTopic.this, InternalListeningTopic.class); // Navigate to self
        nextIntent.putExtra("LEVEL_NAME", levelName);
        nextIntent.putExtra("TOPIC_TITLE", topicTitle);
        nextIntent.putExtra("EXERCISE_TITLE", nextExerciseTitle);
        startActivity(nextIntent);
        finish(); // Finish current activity
    }

    @Override
    public void finishActivity() {
        Log.i(TAG, "finishActivity called by controller.");
        finish();
    }

    @Override
    public void scrollToQuestion(int index) {
        Log.d(TAG, "scrollToQuestion: " + index);
        if (lvQuestions != null) {
            // Use post to ensure scrolling happens after layout calculation
            lvQuestions.post(() -> lvQuestions.smoothScrollToPosition(index));
        }
    }

    @Override
    public void setUIElementsVisibility(boolean visible) {
        Log.d(TAG, "setUIElementsVisibility: " + visible);
        runOnUiThread(() -> {
            int visibility = visible ? View.VISIBLE : View.INVISIBLE; // Use INVISIBLE to keep layout space
            // Only control elements related to the questions/submit flow here
            // Audio controls visibility is handled by showAudioLoading/Ready/Reset
            if (lvQuestions != null) {
                // Show list only if visible AND adapter has items
                boolean hasItems = questionListAdapter != null && questionListAdapter.getCount() > 0;
                lvQuestions.setVisibility(visible && hasItems ? View.VISIBLE : View.INVISIBLE);
            }
            if (btnSubmit != null) {
                boolean hasItems = questionListAdapter != null && questionListAdapter.getCount() > 0;
                btnSubmit.setVisibility(visible && hasItems ? View.VISIBLE : View.INVISIBLE);
            }
            // Title is usually always visible once set
            // if (tvScreenTitle != null) tvScreenTitle.setVisibility(visibility); // Or keep visible?
        });
    }


    // --- Audio Control Specific Interface Methods ---

    @Override
    public void showAudioLoading() {
        Log.d(TAG, "showAudioLoading");
        runOnUiThread(() -> {
            if (progressBarAudioLoading != null) progressBarAudioLoading.setVisibility(View.VISIBLE);
            if (btnPlayAudio != null) btnPlayAudio.setVisibility(View.INVISIBLE);
            if (seekBarAudio != null) seekBarAudio.setEnabled(false);
            if (btnPlayAudio != null) btnPlayAudio.setEnabled(false);
        });
    }

    @Override
    public void showAudioReady(int duration) {
        Log.d(TAG, "showAudioReady, Duration: " + duration);
        runOnUiThread(() -> {
            if (progressBarAudioLoading != null) progressBarAudioLoading.setVisibility(View.GONE);
            if (btnPlayAudio != null) {
                btnPlayAudio.setVisibility(View.VISIBLE);
                btnPlayAudio.setImageResource(R.drawable.icon_play_audio); // Default to Play icon
                btnPlayAudio.setEnabled(true);
            }
            if (seekBarAudio != null) {
                seekBarAudio.setEnabled(true);
                seekBarAudio.setMax(duration);
                seekBarAudio.setProgress(0); // Start at beginning
            }
        });
    }

    @Override
    public void updateAudioProgress(int progress, int max) {
        // No Log here as it's called frequently
        runOnUiThread(() -> {
            if (seekBarAudio != null && seekBarAudio.isEnabled()) { // Check if enabled
                // Update max just in case duration wasn't ready initially (unlikely but safe)
                if(seekBarAudio.getMax() != max) seekBarAudio.setMax(max);
                // Ensure progress doesn't exceed max visually
                seekBarAudio.setProgress(Math.min(progress, max));
            }
        });
    }

    @Override
    public void setPlayButtonState(boolean isPlaying) {
        Log.d(TAG, "setPlayButtonState: isPlaying=" + isPlaying);
        runOnUiThread(() -> {
            if (btnPlayAudio != null) {
                btnPlayAudio.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.icon_play_audio);
            }
        });
    }

    @Override
    public void resetAudioControls() {
        Log.d(TAG, "resetAudioControls");
        runOnUiThread(() -> {
            if (progressBarAudioLoading != null) progressBarAudioLoading.setVisibility(View.GONE);
            if (btnPlayAudio != null) {
                btnPlayAudio.setVisibility(View.VISIBLE);
                btnPlayAudio.setImageResource(R.drawable.icon_play_audio);
                btnPlayAudio.setEnabled(false); // Disabled initially
            }
            if (seekBarAudio != null) {
                seekBarAudio.setEnabled(false);
                seekBarAudio.setProgress(0);
                seekBarAudio.setMax(100); // Reset max to default or 0
            }
        });
    }

    @Override
    public void showAudioError(String errorMessage) {
        Log.e(TAG, "showAudioError: " + errorMessage);
        // Show toast AND reset controls typically
        showToast("Audio Error: " + errorMessage); // Use regular toast for errors
        resetAudioControls(); // Put controls back into a safe, disabled state
    }


    // --- SeekBar.OnSeekBarChangeListener ---
    // These methods now primarily DELAY actions to the Controller

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        // Can be used for displaying time dynamically if needed, but seeking action is onStopTrackingTouch
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "SeekBar tracking started by user.");
        // No direct action needed on controller here, stopSeekBarUpdate is handled internally by controller if playing
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "SeekBar tracking stopped by user at: " + seekBar.getProgress());
        if (controller != null) {
            controller.seekAudio(seekBar.getProgress()); // Delegate seek action to controller
        }
    }
    // --- End SeekBar.OnSeekBarChangeListener ---


    // --- Lifecycle Methods (Forwarding to Controller) ---
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause.");
        if (controller != null) {
            controller.onPause(); // Forward lifecycle event
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume.");
        if (controller != null) {
            controller.onResume(); // Forward lifecycle event
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop.");
        // Controller doesn't currently implement onStop, but could if needed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy.");
        if (controller != null) {
            controller.onDestroy(); // IMPORTANT: Allow controller to clean up resources
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed.");
        // Controller doesn't currently have specific back press logic,
        // but if it needed to save state or stop something critical, it could be added.
        // For now, just perform default behavior.
        super.onBackPressed();
        // Note: controller.onDestroy() will be called shortly after by the system.
    }

}

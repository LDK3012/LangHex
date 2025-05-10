//MVC
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
// import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

// Import contract and controller
import com.example.langhexx.Controller.SpeakingController; // Import controller
import com.example.langhexx.Model.SpeakingContract;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.R; // Thay đổi thành R
//
import android.content.SharedPreferences; // Thêm import
import com.google.firebase.auth.FirebaseAuth; // Thêm import
import com.google.firebase.auth.FirebaseUser; // Thêm import
import com.google.firebase.auth.UserInfo;      // Thêm import
import com.bumptech.glide.Glide;             // Đảm bảo đã import Glide
import com.bumptech.glide.signature.ObjectKey; // Thêm import cho signature
import com.example.langhexx.Model.UsernamePasswordSessionManager; // Thêm import
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.util.TextUtils;

import java.io.BufferedInputStream;           // Thêm import
import java.io.ByteArrayOutputStream;         // Thêm import
import java.io.IOException;                   // Thêm import
import java.io.InputStream;                   // Thêm import
import java.net.HttpURLConnection;            // Thêm import
import java.net.URL;                          // Thêm import
import java.util.concurrent.ExecutorService;    // Thêm import
import java.util.concurrent.Executors;      // Thêm import

import java.util.ArrayList;
import java.util.Locale;

public class InternalSpeakingTopic extends AppCompatActivity implements SpeakingContract.View { // Chỉ implement View

    private static final String TAG = "InternalSpeakingTopicView"; // Đổi TAG để phân biệt
    private static final int REQUEST_CODE_SPEECH_INPUT = 100;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // --- UI Elements ---
    private LinearLayout questionContainer;
    private LayoutInflater inflater;
    private ImageButton btnMicro;
    private ImageView imgClose, imgHome;
    private ScrollView scrollViewContent;
    private TextToSpeech textToSpeech;
    private SoundPool soundPool;
    private int correctSoundId;
    private int incorrectSoundId;
    private boolean soundsLoaded = false;
    private Handler uiHandler = new Handler(Looper.getMainLooper()); // Handler cho UI (ví dụ: scroll)

    // --- Views for the CURRENT question ---
    private View currentQaView;
    private TextView txtCurrentQuestion;
    private TextView txtResponse;
    private ImageView responseSpeaker;
    private ImageView avatarUser;
    private ImageView iconWarning;
    private ImageView questionSpeaker;
    private TextView tvTitle ;

    // --- Controller ---
    private SpeakingContract.Controller controller; // Tham chiếu đến Controller
    //
    private FirebaseAuth mAuth ;
    private UsernamePasswordSessionManager sessionManager;
    private static final String MS_GRAPH_PREFS = "MSGraphPrefs";
    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token";
    private static final String MICROSOFT_PROVIDER_ID = "microsoft.com";
    private ExecutorService avatarExecutorService;

    // --- Activity Lifecycle ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_speaking_topic);
        //
        // --- Khởi tạo các thành phần cần cho Avatar ---
        mAuth = FirebaseAuth.getInstance();
        sessionManager = new UsernamePasswordSessionManager(this); // Khởi tạo SessionManager
        avatarExecutorService = Executors.newSingleThreadExecutor();
        // Lấy dữ liệu Intent trước khi tạo Controller
        String levelName = getIntent().getStringExtra("levelName");
        String topicTitle = getIntent().getStringExtra("topicTitle");
        //
        // Initialize Controller (pass View reference and Context)
        controller = new SpeakingController(this, this, levelName, topicTitle);

        addControls(); // Initialize UI elements
        //show title
        tvTitle.setText(topicTitle);
        //
        addEvent();    // Setup listeners to forward to Controller
        initializeSoundPool(); // Initialize SoundPool
        initTextToSpeech(); // Initialize TTS (sẽ gọi controller.onTtsReady())

        // Controller handles the rest of the initialization flow
        controller.viewDidLoad(); // Báo cho Controller biết View đã sẵn sàng
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            controller.onDestroy(); // Báo cho Controller biết View sắp bị hủy
        }
        // View cleanup
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (soundPool != null) {
            soundPool.release();
        }
        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }
        if (avatarExecutorService != null && !avatarExecutorService.isShutdown()) {
            avatarExecutorService.shutdown();
        }
        super.onDestroy();
        Log.d(TAG, "onDestroy finished.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
        // Có thể thông báo cho Controller nếu cần xử lý gì đó khi Pause
        // controller.onViewPaused();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Có thể thông báo cho Controller nếu cần xử lý gì đó khi Resume
        // controller.onViewResumed();
    }

    // --- Initialization (UI only) ---

    private void addControls() {
        questionContainer = findViewById(R.id.questionContainer);
        inflater = LayoutInflater.from(this);
        btnMicro = findViewById(R.id.btnSpeakingMicro);
        imgClose = findViewById(R.id.imgBackward);
        scrollViewContent = findViewById(R.id.scrollViewContent);
        tvTitle = findViewById(R.id.tvScreenTitle) ;
        imgHome = findViewById(R.id.imgHome);
        //

    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    showToast("Ngôn ngữ nói (Tiếng Anh Mỹ) không được hỗ trợ!");
                    // Vẫn báo TTS ready để controller có thể load data dù TTS lỗi
                    if (controller instanceof SpeakingController) { // Kiểm tra kiểu để gọi phương thức cụ thể
                        ((SpeakingController) controller).onTtsReady();
                    }
                } else {
                    Log.d(TAG, "TextToSpeech initialized successfully.");
                    textToSpeech.setPitch(1.1f);
                    textToSpeech.setSpeechRate(0.95f);
                    // Báo cho Controller biết TTS đã sẵn sàng
                    if (controller instanceof SpeakingController) {
                        ((SpeakingController) controller).onTtsReady();
                    }
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed: " + status);
                showToast("Không thể khởi tạo chức năng đọc văn bản!");
                // Vẫn báo TTS ready để controller có thể load data dù TTS lỗi
                if (controller instanceof SpeakingController) {
                    ((SpeakingController) controller).onTtsReady();
                }
            }
        });
    }


    private void initializeSoundPool() {
        // Giữ nguyên logic khởi tạo SoundPool như trước
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttributes).build();
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) {
                Log.d(TAG, "Sound loaded: ID = " + sampleId);
                if (sampleId == correctSoundId || sampleId == incorrectSoundId) {
                    if (correctSoundId != 0 && incorrectSoundId != 0) {
                        soundsLoaded = true;
                        Log.d(TAG,"Both sounds potentially loaded.");
                    }
                }
            } else {
                showToast("Lỗi tải hiệu ứng âm thanh ID " + sampleId);
            }
        });
        try {
            correctSoundId = soundPool.load(this, R.raw.correct_answer, 1);
            incorrectSoundId = soundPool.load(this, R.raw.wrong_answer, 1);
            if(correctSoundId == 0 || incorrectSoundId == 0){
                showToast("Lỗi tìm file âm thanh trong res/raw");
            }
        } catch (Exception e) {
            showToast("Không tìm thấy file âm thanh trong res/raw");
            Log.e(TAG, "Error finding sound files", e);
        }
    }

    // --- Event Forwarding to Controller ---

    private void addEvent() {
        imgClose.setOnClickListener(v -> controller.onCloseButtonClicked());
        btnMicro.setOnClickListener(v -> controller.onMicButtonClicked());
        imgHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(InternalSpeakingTopic.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
        // Listener cho speaker/warning sẽ được set trong updateUiForNewQuestion
    }

    @Override
    public void displayQuestion(String question) {
        int currentIndex = -1; // Cần cách lấy index từ controller nếu logic này ở View
        updateUiForNewQuestion(question); // Cập nhật view mới
        setMicButtonEnabled(true); // Bật mic cho câu hỏi mới
        scrollDown();
    }

    @SuppressLint("InflateParams")
    @Override
    public void updateUiForNewQuestion(String question) {
        currentQaView = inflater.inflate(R.layout.custom_question_list, questionContainer, false);

        // Lấy các view con từ currentQaView
        txtCurrentQuestion = currentQaView.findViewById(R.id.txtQuestion);
        questionSpeaker = currentQaView.findViewById(R.id.speaker);
        txtResponse = currentQaView.findViewById(R.id.tvResponse);
        responseSpeaker = currentQaView.findViewById(R.id.responseSpeaker);
        avatarUser = currentQaView.findViewById(R.id.userAvatar);
        iconWarning = currentQaView.findViewById(R.id.iconWarning);

        txtCurrentQuestion.setText(question);
        questionSpeaker.setOnClickListener(v -> controller.onQuestionSpeakerClicked(question)); // Forward event

        hideResponseElements(); // Ẩn phần trả lời ban đầu

        questionContainer.addView(currentQaView);
    }

    @Override
    public void hideResponseElements() {
        if (txtResponse != null) txtResponse.setVisibility(View.GONE);
        if (responseSpeaker != null) responseSpeaker.setVisibility(View.GONE);
        if (avatarUser != null) avatarUser.setVisibility(View.GONE);
        if (iconWarning != null) {
            iconWarning.setVisibility(View.GONE);
            iconWarning.setOnClickListener(null); // Xóa listener cũ
        }
    }


    @Override
    public void displayUserAnswer(String userAnswer, boolean isCorrect) {

        if (currentQaView == null || txtResponse == null || responseSpeaker == null || avatarUser == null) return;

        txtResponse.setText(userAnswer);
        txtResponse.setTextColor(ContextCompat.getColor(this, isCorrect ? R.color.Lime : R.color.Red));
        txtResponse.setVisibility(View.VISIBLE);
        responseSpeaker.setVisibility(View.VISIBLE);
        //
        responseSpeaker.setOnClickListener(v -> controller.onResponseSpeakerClicked(userAnswer)); // Forward event
        if (avatarUser.getVisibility() != View.VISIBLE) {
            // Nếu avatar CHƯA hiển thị -> Đây là lần đầu hiển thị câu trả lời cho câu hỏi này
            loadAvatarBasedOnLogin(avatarUser); // Gọi hàm tải avatar (Firebase, MS Graph, or default)
            avatarUser.setVisibility(View.VISIBLE); // Làm cho avatar hiển thị
        } else {
            // Nếu avatar ĐÃ hiển thị -> Không cần làm gì cả, chỉ cần giữ nguyên
        }
    }

    // --- Phương thức mới: Load Avatar dựa trên trạng thái đăng nhập ---
    private void loadAvatarBasedOnLogin(ImageView targetAvatarView) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        // Ưu tiên 1: Firebase User
        if (currentUser != null) {
            // Kiểm tra xem có phải đăng nhập bằng Microsoft không
            boolean isMicrosoftUser = false;
            for (UserInfo profile : currentUser.getProviderData()) {
                if (MICROSOFT_PROVIDER_ID.equals(profile.getProviderId())) {
                    isMicrosoftUser = true;
                    break;
                }
            }

            if (isMicrosoftUser) {
                // Người dùng Microsoft -> Lấy ảnh từ Graph API
                Log.d(TAG, "Loading avatar for Microsoft user.");
                String msGraphToken = getMsGraphToken();
                if (!TextUtils.isEmpty(msGraphToken)) {
                    fetchMicrosoftProfilePhoto(msGraphToken, targetAvatarView);
                } else {
                    Log.w(TAG, "MS Graph token not found for avatar loading. Using default.");
                    loadDefaultAvatar(targetAvatarView, R.drawable.unknown_avatar); // Ảnh mặc định nếu ko có token
                }
            } else {
                // Người dùng Firebase khác (Google, Email/Pass,...) -> Dùng photoUrl nếu có
                Uri photoUrl = currentUser.getPhotoUrl();
                if (photoUrl != null) {
                    Log.d(TAG, "Loading avatar from Firebase photoUrl: " + photoUrl);
                    Glide.with(this)
                            .load(photoUrl)
                            .circleCrop()
                            .placeholder(R.drawable.unknown_avatar)
                            .error(R.drawable.unknown_avatar) // Ảnh mặc định nếu URL lỗi
                            .into(targetAvatarView);
                } else {
                    Log.d(TAG, "Firebase user has no photoUrl. Using default.");
                    loadDefaultAvatar(targetAvatarView, R.drawable.unknown_avatar); // Mặc định nếu ko có photoUrl
                }
            }
        }
        // Ưu tiên 2: Session Manager (Đăng nhập Username/Password)
        else if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Loading default avatar for Session Manager user.");
            loadDefaultAvatar(targetAvatarView, R.drawable.avatar); // Ảnh mặc định riêng cho session user
        }
        // Ưu tiên 3: Khách hoặc không xác định
        else {
            Log.d(TAG, "No logged-in user found. Loading default unknown avatar.");
            loadDefaultAvatar(targetAvatarView, R.drawable.unknown_avatar); // Ảnh mặc định cuối cùng
        }
    }

    // --- Phương thức phụ: Load ảnh mặc định bằng Glide ---
    private void loadDefaultAvatar(ImageView targetImageView, int drawableResId) {
        Glide.with(this)
                .load(drawableResId)
                .circleCrop()
                .placeholder(drawableResId) // Giữ chỗ bằng chính ảnh default
                .error(R.drawable.unknown_avatar) // Fallback cuối cùng nếu có lỗi drawable
                .into(targetImageView);
    }

    private String getMsGraphToken() {
        // Lấy context an toàn hơn
        Context context = getApplicationContext();
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(MS_GRAPH_TOKEN_KEY, null);
    }
    // Phương thức fetch ảnh MS (đã sửa để nhận targetImageView)
    private void fetchMicrosoftProfilePhoto(String accessToken, ImageView targetImageView) {
        if (avatarExecutorService == null || avatarExecutorService.isShutdown()) {
            Log.w(TAG, "Avatar ExecutorService is not running. Cannot fetch photo.");
            loadDefaultAvatar(targetImageView, R.drawable.unknown_avatar); // Load default nếu executor lỗi
            return;
        }

        avatarExecutorService.execute(() -> {
            HttpURLConnection urlConnection = null;
            InputStream inputStream = null;
            ByteArrayOutputStream buffer = null;
            byte[] photoData = null;
            int responseCode = -1;

            try {
                // ... (Logic kết nối và lấy dữ liệu byte[] từ Graph API giữ nguyên như HomeFragment) ...
                URL url = new URL("https://graph.microsoft.com/v1.0/me/photo/$value");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(15000);
                responseCode = urlConnection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = new BufferedInputStream(urlConnection.getInputStream());
                    buffer = new ByteArrayOutputStream();
                    byte[] data = new byte[1024];
                    int nRead;
                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    photoData = buffer.toByteArray();
                    Log.d(TAG, "Successfully fetched MS Graph photo data for avatar.");
                } else {
                    Log.e(TAG, "Failed to fetch MS Graph photo. Response code: " + responseCode);
                    // Có thể clear token nếu lỗi 401/403 ở đây
                    // if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    //     clearMsGraphToken(); // Cần context để clear
                    // }
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException while fetching MS Graph photo: ", e);
                photoData = null;
            } finally {
                // ... (Logic dọn dẹp tài nguyên giữ nguyên) ...
                if (inputStream != null) try { inputStream.close(); } catch (IOException e) {}
                if (buffer != null) try { buffer.close(); } catch (IOException e) {}
                if (urlConnection != null) urlConnection.disconnect();
            }

            // --- Update UI on the Main Thread ---
            final byte[] finalPhotoData = photoData;
            final int finalResponseCode = responseCode; // Dùng để log nếu cần

            // Đảm bảo Activity còn tồn tại trước khi cập nhật UI
            if (!isFinishing() && !isDestroyed()) {
                runOnUiThread(() -> {
                    // Lấy UID để làm signature (nếu có)
                    String signatureKey = "ms_avatar_default";
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    if (currentUser != null && !TextUtils.isEmpty(currentUser.getUid())) {
                        signatureKey = currentUser.getUid(); // Dùng UID làm signature
                    }

                    if (finalPhotoData != null) {
                        Glide.with(InternalSpeakingTopic.this) // Sử dụng Context Activity
                                .load(finalPhotoData)
                                .circleCrop()
                                .signature(new ObjectKey(signatureKey)) // Thêm signature
                                .placeholder(R.drawable.unknown_avatar)
                                .error(R.drawable.unknown_avatar) // Fallback nếu Glide lỗi load byte[]
                                .into(targetImageView); // Load vào ImageView được truyền vào
                        Log.d(TAG, "MS Graph photo loaded into avatar view.");
                    } else {
                        Log.w(TAG, "MS Graph photo data is null. Loading default avatar.");
                        // Load ảnh mặc định nếu fetch thất bại hoặc user không có ảnh (404)
                        loadDefaultAvatar(targetImageView, R.drawable.unknown_avatar);
                    }
                });
            } else {
                Log.w(TAG, "Activity is finishing/destroyed. Cannot update avatar UI.");
            }
        });
    }

    @Override
    public void displayEvaluationFeedback(String feedbackVi, String suggestionEn) {
        // Not used directly, feedback shown via dialog/warning icon
    }

    @Override
    public void showWarningIcon(boolean show, String feedbackMessage) {
        if (iconWarning == null) return;
        if (show) {
            iconWarning.setVisibility(View.VISIBLE);
            iconWarning.setImageResource(android.R.drawable.ic_dialog_info);
            // Tint icon
            Drawable icon = ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_info);
            if (icon != null) {
                icon = DrawableCompat.wrap(icon).mutate();
                DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.Red)); // Dùng màu đỏ từ R
                iconWarning.setImageDrawable(icon);
            }
            // Set listener to forward to controller
            iconWarning.setOnClickListener(v -> controller.onWarningIconClicked(feedbackMessage));
        } else {
            iconWarning.setVisibility(View.GONE);
            iconWarning.setOnClickListener(null);
        }
    }

    @Override
    public void showFeedbackDialog(String message) {
        // Giữ nguyên logic hiển thị AlertDialog như trước
        if (isFinishing() || isDestroyed()) return;
        Drawable icon = ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_info);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon).mutate();
            DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.Red)); // Dùng màu đỏ từ R
        }
        new AlertDialog.Builder(this)
                .setTitle("Gợi ý phản hồi")
                .setMessage(message)
                .setIcon(icon)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public void showCompletionMessage() {
        // Giữ nguyên logic hiển thị TextView hoàn thành như trước
        if (questionContainer.findViewWithTag("completion_message") == null) {
            TextView completionText = new TextView(this);
            completionText.setText("Chúc mừng! Bạn đã hoàn thành tất cả câu hỏi!");
            completionText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            completionText.setPadding(16, 32, 16, 32);
            completionText.setTag("completion_message");
            questionContainer.addView(completionText);
        }
        setMicButtonEnabled(false);
    }

    @Override
    public void showError(String message) {
        // Giữ nguyên logic hiển thị lỗi (Toast hoặc TextView) như trước
        showToast("Lỗi: " + message);
        Log.e(TAG, "Displaying Error: " + message);

        // Hiển thị lỗi trong container nếu chưa có câu hỏi
        if(questionContainer.getChildCount() == 0 && currentQaView == null) { // Kiểm tra chính xác hơn
            questionContainer.removeAllViews(); // Xóa các view cũ nếu có
            TextView errorText = new TextView(this);
            errorText.setText("Đã xảy ra lỗi: " + message);
            errorText.setTextColor(Color.RED);
            errorText.setPadding(16, 16, 16, 16);
            questionContainer.addView(errorText);
        }
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showCustomToast(boolean success, String message) {
        // Giữ nguyên logic CustomToast như trước
        if(success) {
            CustomToast.showSuccess(this, message, R.drawable.success);
        } else {
            CustomToast.showFail(this, message, R.drawable.fail_icon);
        }
    }

    @Override
    public void setMicButtonEnabled(boolean enabled) {
        // Giữ nguyên logic cập nhật trạng thái nút micro như trước
        btnMicro.setEnabled(enabled);
        btnMicro.setAlpha(enabled ? 1.0f : 0.5f);
    }

    @Override
    public void playSound(boolean isCorrect) {
        // Giữ nguyên logic phát âm thanh SoundPool như trước
        int soundId = isCorrect ? correctSoundId : incorrectSoundId;
        if (soundsLoaded && soundPool != null && soundId != 0) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        } else {
            Log.w(TAG, "Cannot play sound - Loaded: " + soundsLoaded + ", Pool: " + (soundPool != null) + ", ID: " + soundId);
        }
    }

    @Override
    public void speakText(String text, String utteranceId) {
        // Giữ nguyên logic gọi TTS như trước
        if (textToSpeech != null && text != null && !text.isEmpty() && textToSpeech.getEngines().size() > 0) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId);
        } else {
            Log.e(TAG, "TTS not ready, text empty, or no engines. Cannot speak.");
        }
    }

    @Override
    public Context getContext() {
        return this; // Trả về Context của Activity
    }

    @Override
    public void scrollDown() {
        // Giữ nguyên logic cuộn ScrollView như trước
        uiHandler.post(() -> scrollViewContent.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void finishActivity() {
        finish(); // Đóng Activity
    }

    // --- Permission Handling ---
    @Override
    public void requestAudioPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            controller.onPermissionResult(granted); // Forward result to Controller
        }
    }

    // --- Intent Handling ---
    @Override
    public void startSpeechRecognitionIntent() {
        // Giữ nguyên logic tạo và bắt đầu RecognizerIntent như trước
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        //... (các extra khác) ...
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói câu trả lời của bạn...");
        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            controller.onSpeechError("Không thể khởi động nhận dạng giọng nói."); // Forward error
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
                        String formattedResponse = spokenResponse.substring(0, 1).toUpperCase() + spokenResponse.substring(1);
                        controller.onSpeechResult(formattedResponse); // Forward result
                    } else {
                        controller.onSpeechError("Không nhận dạng được giọng nói rõ ràng."); // Forward error
                    }
                } else {
                    controller.onSpeechError("Không nhận dạng được giọng nói."); // Forward error
                }
            } else {
                String errorReason = "Unknown recognition error";
                switch (resultCode) { /* ... (các case lỗi như cũ) ... */
                    case RecognizerIntent.RESULT_AUDIO_ERROR: errorReason = "Lỗi âm thanh đầu vào."; break;
                    case RecognizerIntent.RESULT_CLIENT_ERROR: errorReason = "Lỗi phía client."; break;
                    case RecognizerIntent.RESULT_NETWORK_ERROR: errorReason = "Lỗi mạng."; break;
                    case RecognizerIntent.RESULT_NO_MATCH: errorReason = "Không có kết quả phù hợp."; break;
                    case RecognizerIntent.RESULT_SERVER_ERROR: errorReason = "Lỗi từ server nhận dạng."; break;
                    case RESULT_CANCELED: errorReason = null; break; // User cancelled
                }
                controller.onSpeechError(errorReason); // Forward error/cancel
            }
        }
    }
}

package com.example.langhexx.View;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.langhexx.Controller.SpeakingController;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.SpeakingContract;
import com.example.langhexx.Model.UsernamePasswordSessionManager;
import com.example.langhexx.R; // Thay thế bằng R file của bạn
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

// AZURE Speech SDK Imports
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class InternalSpeakingTopic extends AppCompatActivity implements SpeakingContract.View {

    private static final String TAG = "InternalSpeakingTopicView";
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
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private View currentQaView;
    private TextView txtCurrentQuestion;
    private TextView txtResponse;
    private ImageView responseSpeaker;
    private ImageView avatarUser;
    private ImageView iconWarning;
    private ImageView questionSpeaker;
    private TextView tvTitle;

    // --- Controller ---
    private SpeakingContract.Controller controller;

    // --- Firebase & Session ---
    private FirebaseAuth mAuth;
    private UsernamePasswordSessionManager sessionManager;
    private static final String MS_GRAPH_PREFS = "MSGraphPrefs";
    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token";
    private static final String MICROSOFT_PROVIDER_ID = "microsoft.com";
    private ExecutorService avatarExecutorService;

    // --- Azure Speech Recognizer ---
    private static final String AZURE_SPEECH_KEY = "75aMORlAm3JGJXfz0oOcHaX3hytrGyJ9MBRUfRGutW5qeZSuFjz3JQQJ99BEACYeBjFXJ3w3AAAYACOGDbeK"; // THAY THẾ BẰNG KEY CỦA BẠN
    private static final String AZURE_SPEECH_REGION = "eastus";
    private SpeechConfig azureSpeechConfig;
    private SpeechRecognizer azureSpeechRecognizer;
    private AudioConfig azureAudioConfig;
    private StringBuilder continuousRecoTextBuilder = new StringBuilder();

    // --- Dialog & State ---
    private boolean isCurrentlyListening = false;
    private AlertDialog speechConfirmationDialog;
    private TextView tvPartialSpeechTextInDialog;
    private boolean speechConfirmedManually = false;
    private boolean keepListeningActive = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_speaking_topic);

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new UsernamePasswordSessionManager(this);
        avatarExecutorService = Executors.newSingleThreadExecutor();

        String levelName = getIntent().getStringExtra("levelName");
        String topicTitle = getIntent().getStringExtra("topicTitle");

        controller = new SpeakingController(this, this, levelName, topicTitle);

        addControls();
        if (topicTitle != null) {
            tvTitle.setText(topicTitle);
        }
        addEvent();
        initializeSoundPool();
        initTextToSpeech();

        try {
            azureSpeechConfig = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SPEECH_REGION);
            azureSpeechConfig.setSpeechRecognitionLanguage("en-US");
            azureAudioConfig = AudioConfig.fromDefaultMicrophoneInput(); // Tạo một lần ở đây
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Azure SpeechConfig: " + e.getMessage(), e);
            showError("Azure Speech configuration error:" + e.getMessage());
            if (btnMicro != null) btnMicro.setEnabled(false);
        }

        controller.viewDidLoad();
    }

    private void addControls() {
        questionContainer = findViewById(R.id.questionContainer);
        inflater = LayoutInflater.from(this);
        btnMicro = findViewById(R.id.btnSpeakingMicro);
        imgClose = findViewById(R.id.imgBackward);
        scrollViewContent = findViewById(R.id.scrollViewContent);
        tvTitle = findViewById(R.id.tvScreenTitle);
        imgHome = findViewById(R.id.imgHome);
    }

    private void addEvent() {
        imgClose.setOnClickListener(v -> controller.onCloseButtonClicked());
        btnMicro.setOnClickListener(v -> {
            if (azureSpeechConfig == null) {
                showToast("Cấu hình Azure Speech chưa sẵn sàng.");
                return;
            }
            if (speechConfirmationDialog != null && speechConfirmationDialog.isShowing()) {
                Log.d(TAG, "Mic clicked: Dialog is showing.");
                return;
            }
            if (!isCurrentlyListening) {
                Log.d(TAG, "Mic clicked: Starting new Azure SR session.");
                controller.onMicButtonClicked();
            } else {
                Log.d(TAG, "Mic clicked: Azure SR is active. Stopping current session.");
                stopListening();
            }
        });
        imgHome.setOnClickListener(view -> {
            Intent intent = new Intent(InternalSpeakingTopic.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    showToast("Spoken language (U.S. English) is not supported!");
                } else { Log.d(TAG, "TextToSpeech initialized successfully."); textToSpeech.setPitch(1.1f); textToSpeech.setSpeechRate(0.95f); }
            } else { Log.e(TAG, "TextToSpeech initialization failed: " + status); showToast("Unable to initialize text-to-speech functionality!");}
            if (controller instanceof SpeakingController) { ((SpeakingController) controller).onTtsReady(); }
        });
    }

    private void initializeSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        soundPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttributes).build();
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) { Log.d(TAG, "Sound loaded: ID = " + sampleId); soundsLoaded = true; }
            else { showToast("Failed to load sound effect ID." + sampleId + " status " + status); }
        });
        try { correctSoundId = soundPool.load(this, R.raw.correct_answer, 1); incorrectSoundId = soundPool.load(this, R.raw.wrong_answer, 1); }
        catch (Exception e) { showToast("Không tìm thấy file âm thanh trong res/raw"); Log.e(TAG, "Error loading sound files", e); }
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called.");
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }

        if (isCurrentlyListening || (speechConfirmationDialog != null && speechConfirmationDialog.isShowing())) {
            Log.d(TAG, "onPause: Stopping active Azure speech recognition and dialog.");
            keepListeningActive = false;
            if (azureSpeechRecognizer != null) {
                try {
                    azureSpeechRecognizer.stopContinuousRecognitionAsync(); // Không cần .get() ở onPause
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping Azure recognizer in onPause: " + e.getMessage());
                }
            }
            dismissSpeechConfirmationDialog(); // Luôn đóng dialog khi pause
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called.");
        keepListeningActive = false;
        dismissSpeechConfirmationDialog();

        if (controller != null) { controller.onDestroy(); controller = null; }
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); textToSpeech = null; }
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        if (uiHandler != null) { uiHandler.removeCallbacksAndMessages(null); }

        if (azureSpeechRecognizer != null) {
            try {
                azureSpeechRecognizer.stopContinuousRecognitionAsync().get(); // Chờ để đảm bảo dừng hẳn
            } catch (Exception e) { Log.w(TAG, "Exception stopping recognizer in onDestroy: " + e.getMessage()); }
            azureSpeechRecognizer.close();
            azureSpeechRecognizer = null;
        }
        if (azureAudioConfig != null) { azureAudioConfig.close(); azureAudioConfig = null; }
        if (azureSpeechConfig != null) { azureSpeechConfig.close(); azureSpeechConfig = null; }

        if (avatarExecutorService != null && !avatarExecutorService.isShutdown()) {
            avatarExecutorService.shutdown();
        }
        super.onDestroy();
        Log.d(TAG, "onDestroy finished.");
    }

    @Override public void displayQuestion(String question) { updateUiForNewQuestion(question); setMicButtonEnabled(azureSpeechConfig != null); scrollDown(); }
    @SuppressLint("InflateParams") @Override public void updateUiForNewQuestion(String question) { currentQaView = inflater.inflate(R.layout.custom_question_list, questionContainer, false); txtCurrentQuestion = currentQaView.findViewById(R.id.txtQuestion); questionSpeaker = currentQaView.findViewById(R.id.speaker); txtResponse = currentQaView.findViewById(R.id.tvResponse); responseSpeaker = currentQaView.findViewById(R.id.responseSpeaker); avatarUser = currentQaView.findViewById(R.id.userAvatar); iconWarning = currentQaView.findViewById(R.id.iconWarning); txtCurrentQuestion.setText(question); questionSpeaker.setOnClickListener(v -> controller.onQuestionSpeakerClicked(question)); hideResponseElements(); questionContainer.addView(currentQaView); }
    @Override public void hideResponseElements() { if (txtResponse != null) txtResponse.setVisibility(View.GONE); if (responseSpeaker != null) responseSpeaker.setVisibility(View.GONE); if (avatarUser != null) avatarUser.setVisibility(View.GONE); if (iconWarning != null) { iconWarning.setVisibility(View.GONE); iconWarning.setOnClickListener(null); } }
    @Override public void displayUserAnswer(String userAnswer, boolean isCorrect) { if (currentQaView == null || txtResponse == null || responseSpeaker == null || avatarUser == null) return; txtResponse.setText(userAnswer); txtResponse.setTextColor(ContextCompat.getColor(this, isCorrect ? R.color.Lime : R.color.Red)); txtResponse.setVisibility(View.VISIBLE); responseSpeaker.setVisibility(View.VISIBLE); responseSpeaker.setOnClickListener(v -> controller.onResponseSpeakerClicked(userAnswer)); if (avatarUser.getVisibility() != View.VISIBLE) { loadAvatarBasedOnLogin(avatarUser); avatarUser.setVisibility(View.VISIBLE); } }
    private void loadAvatarBasedOnLogin(ImageView targetAvatarView) { FirebaseUser currentUser = mAuth.getCurrentUser(); if (currentUser != null) { boolean isMicrosoftUser = false; for (UserInfo profile : currentUser.getProviderData()) { if (MICROSOFT_PROVIDER_ID.equals(profile.getProviderId())) { isMicrosoftUser = true; break; } } if (isMicrosoftUser) { String msGraphToken = getMsGraphToken(); if (!TextUtils.isEmpty(msGraphToken)) fetchMicrosoftProfilePhoto(msGraphToken, targetAvatarView); else loadDefaultAvatar(targetAvatarView, R.drawable.unknown_avatar); } else { Uri photoUrl = currentUser.getPhotoUrl(); if (photoUrl != null) Glide.with(this).load(photoUrl).circleCrop().placeholder(R.drawable.unknown_avatar).error(R.drawable.unknown_avatar).into(targetAvatarView); else loadDefaultAvatar(targetAvatarView, R.drawable.unknown_avatar); } } else if (sessionManager.isLoggedIn()) { loadDefaultAvatar(targetAvatarView, R.drawable.avatar); } else { loadDefaultAvatar(targetAvatarView, R.drawable.unknown_avatar); } }
    private void loadDefaultAvatar(ImageView targetImageView, int drawableResId) { if (isFinishing() || isDestroyed()) return; Glide.with(this).load(drawableResId).circleCrop().placeholder(drawableResId).error(R.drawable.unknown_avatar).into(targetImageView); }
    private String getMsGraphToken() { SharedPreferences prefs = getApplicationContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE); return prefs.getString(MS_GRAPH_TOKEN_KEY, null); }
    private void fetchMicrosoftProfilePhoto(String accessToken, ImageView targetImageView) { if (avatarExecutorService == null || avatarExecutorService.isShutdown()) { runOnUiThread(() -> loadDefaultAvatar(targetImageView, R.drawable.unknown_avatar)); return; } avatarExecutorService.execute(() -> { HttpURLConnection urlConnection = null; InputStream inputStream = null; ByteArrayOutputStream buffer = null; byte[] photoData = null; try { URL url = new URL("https://graph.microsoft.com/v1.0/me/photo/$value"); urlConnection = (HttpURLConnection) url.openConnection(); urlConnection.setRequestProperty("Authorization", "Bearer " + accessToken); if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) { inputStream = new BufferedInputStream(urlConnection.getInputStream()); buffer = new ByteArrayOutputStream(); byte[] data = new byte[1024]; int nRead; while ((nRead = inputStream.read(data, 0, data.length)) != -1) buffer.write(data, 0, nRead); buffer.flush(); photoData = buffer.toByteArray(); } } catch (IOException e) { Log.e(TAG, "IOException fetching MS Photo", e); } finally { try { if (inputStream != null) inputStream.close(); if (buffer != null) buffer.close(); } catch (IOException ignored) {} if (urlConnection != null) urlConnection.disconnect(); } final byte[] finalPhotoData = photoData; if (!isFinishing() && !isDestroyed()) { runOnUiThread(() -> { String uid = (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getUid() != null) ? mAuth.getCurrentUser().getUid() : "default_ms_key"; if (finalPhotoData != null) Glide.with(InternalSpeakingTopic.this).load(finalPhotoData).circleCrop().signature(new ObjectKey(uid)).placeholder(R.drawable.unknown_avatar).error(R.drawable.unknown_avatar).into(targetImageView); else loadDefaultAvatar(targetImageView, R.drawable.unknown_avatar); }); } }); }
    @Override public void displayEvaluationFeedback(String feedbackVi, String suggestionEn) {}
    @Override public void showWarningIcon(boolean show, String feedbackMessage) { if (iconWarning == null) return; if (show) { iconWarning.setVisibility(View.VISIBLE); Drawable icon = ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_info); if (icon != null) { icon = DrawableCompat.wrap(icon).mutate(); DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.Red)); iconWarning.setImageDrawable(icon); } iconWarning.setOnClickListener(v -> { if (controller != null) controller.onWarningIconClicked(feedbackMessage); }); } else { iconWarning.setVisibility(View.GONE); iconWarning.setOnClickListener(null); } }
    @Override public void showFeedbackDialog(String message) { if (isFinishing() || isDestroyed()) return; Drawable icon = ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_info); if (icon != null) { icon = DrawableCompat.wrap(icon).mutate(); DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.Red)); } new AlertDialog.Builder(this).setTitle("Gợi ý phản hồi").setMessage(message).setIcon(icon).setPositiveButton("OK", (dialog, which) -> dialog.dismiss()).show(); }
    @Override public void showCompletionMessage() { if (questionContainer.findViewWithTag("completion_message") == null) { TextView completionText = new TextView(this); completionText.setText("Chúc mừng! Bạn đã hoàn thành tất cả câu hỏi!"); completionText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); completionText.setPadding(16, 32, 16, 32); completionText.setTag("completion_message"); questionContainer.addView(completionText); } setMicButtonEnabled(false); }
    @Override public void showError(String message) { showToast("Lỗi: " + message); Log.e(TAG, "Displaying Error: " + message); if (questionContainer.getChildCount() == 0 && currentQaView == null && !isFinishing()) { questionContainer.removeAllViews(); TextView errorText = new TextView(this); errorText.setText("Đã xảy ra lỗi: " + message + "\nVui lòng thử lại hoặc kiểm tra kết nối."); errorText.setTextColor(Color.RED); errorText.setPadding(16, 16, 16, 16); errorText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); questionContainer.addView(errorText); } }
    @Override public void showToast(String message) { if (!isFinishing() && message != null) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); } }
    @Override public void showCustomToast(boolean success, String message) { if (isFinishing()) return; if (success) { CustomToast.showSuccess(this, message, R.drawable.success); } else { CustomToast.showFail(this, message, R.drawable.fail_icon); } }
    @Override public void playSound(boolean isCorrect) { if (!soundsLoaded || soundPool == null) { Log.w(TAG, "Sound not played: soundsLoaded=" + soundsLoaded + ", soundPoolNull=" + (soundPool == null)); return; } int soundId = isCorrect ? correctSoundId : incorrectSoundId; if (soundId != 0) { soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f); } else { Log.w(TAG, "Sound not played: soundId is 0 for isCorrect=" + isCorrect); } }
    @Override public Context getContext() { return this; }
    @Override public void scrollDown() { uiHandler.post(() -> scrollViewContent.fullScroll(View.FOCUS_DOWN)); }
    @Override public void finishActivity() { finish(); }


    @Override
    public void setMicButtonEnabled(boolean enabled) {
        if (btnMicro == null) return;
        boolean dialogIsOpen = (speechConfirmationDialog != null && speechConfirmationDialog.isShowing());
        btnMicro.setEnabled(enabled && !dialogIsOpen && azureSpeechConfig != null);
        btnMicro.setAlpha(btnMicro.isEnabled() ? 1.0f : 0.5f);
        Log.d(TAG, "setMicButtonEnabled: " + enabled + ", DialogOpen: " + dialogIsOpen + ", AzureConfigReady: " + (azureSpeechConfig != null) + " -> MicEnabled: " + btnMicro.isEnabled());
    }

    @Override
    public void speakText(String text, String utteranceId) {
        if (textToSpeech != null && !TextUtils.isEmpty(text) && textToSpeech.getEngines().size() > 0) {
            if (isCurrentlyListening && azureSpeechRecognizer != null) {
                Log.d(TAG, "TTS speakText: Stopping active Azure listening to speak.");
                try {
                    azureSpeechRecognizer.stopContinuousRecognitionAsync();
                } catch (Exception e) { Log.e(TAG, "Error stopping Azure for TTS: " + e.getMessage()); }
            }
            dismissSpeechConfirmationDialog();
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        } else {
            Log.e(TAG, "TTS not ready or text empty.");
        }
    }

    @Override
    public void requestAudioPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            boolean audioGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.RECORD_AUDIO) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    audioGranted = true;
                    break;
                }
            }
            if (controller != null) {
                controller.onPermissionResult(audioGranted);
            }
            if (!audioGranted) {
                showToast("Recording permission is required to use this feature.");
                setMicButtonEnabled(false);
            } else {
                setMicButtonEnabled(azureSpeechConfig != null); // Bật mic nếu config đã sẵn sàng
            }
        }
    }

    // --- AZURE Speech Recognition & Dialog Methods ---
    @Override
    public void startListening() {
        if (azureSpeechConfig == null) {
            showToast("Azure Speech configuration is not ready.");
            Log.e(TAG, "Azure SpeechConfig is null. Cannot start listening.");
            return;
        }
        if (azureAudioConfig == null) {
            azureAudioConfig = AudioConfig.fromDefaultMicrophoneInput();
            if (azureAudioConfig == null) {
                showToast("Unable to configure microphone input");
                Log.e(TAG, "Azure AudioConfig is null after re-attempt. Cannot start listening.");
                return;
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission();
            return;
        }
        if (!isNetworkAvailable()) {
            showError("No internet connection! Try again");
            return;
        }

        if (isCurrentlyListening || (speechConfirmationDialog != null && speechConfirmationDialog.isShowing())) {
            Log.d(TAG, "Azure startListening: Ignored. Already listening or dialog is showing.");
            return;
        }

        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }

        if (azureSpeechRecognizer != null) { // Dọn dẹp instance cũ nếu có
            try {
                azureSpeechRecognizer.stopContinuousRecognitionAsync().get(); // Chờ dừng hẳn
                azureSpeechRecognizer.close();
                Log.d(TAG, "Closed previous Azure SpeechRecognizer instance.");
            } catch (Exception e) { Log.w(TAG, "Exception closing previous recognizer: " + e.getMessage()); }
            azureSpeechRecognizer = null;
        }

        azureSpeechRecognizer = new SpeechRecognizer(azureSpeechConfig, azureAudioConfig);

        // Reset các cờ và buffer cho phiên mới
        speechConfirmedManually = false;
        keepListeningActive = true;
        continuousRecoTextBuilder.setLength(0);

        // Đăng ký các event handlers
        azureSpeechRecognizer.sessionStarted.addEventListener((s, e) -> {
            Log.d(TAG, "Azure Session STARTED. SessionId: " + e.getSessionId());
            continuousRecoTextBuilder.setLength(0); // Reset lại ở đây để chắc chắn
            runOnUiThread(() -> {
                isCurrentlyListening = true;
                indicateListeningState(true);
                updateSpeechConfirmationDialog("Listening...");
            });
        });

        azureSpeechRecognizer.recognizing.addEventListener((s, e) -> {
            if (e.getResult().getReason() == ResultReason.RecognizingSpeech) {
                String partialText = e.getResult().getText();
                Log.d(TAG, "Azure RECOGNIZING: " + partialText);
                if (keepListeningActive && !speechConfirmedManually && !TextUtils.isEmpty(partialText)) {
                    String stableText = continuousRecoTextBuilder.toString();
                    String textToDisplayOnDialog = stableText + (stableText.isEmpty() ? "" : " ") + partialText;
                    runOnUiThread(() -> updateSpeechConfirmationDialog(textToDisplayOnDialog));
                }
            }
        });

        azureSpeechRecognizer.recognized.addEventListener((s, e) -> {
            if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                String recognizedSegmentText = e.getResult().getText();
                Log.d(TAG, "Azure RECOGNIZED segment: " + recognizedSegmentText);

                if (keepListeningActive && !speechConfirmedManually) {
                    if (!TextUtils.isEmpty(recognizedSegmentText)) {
                        if (continuousRecoTextBuilder.length() > 0) {
                            continuousRecoTextBuilder.append(" ");
                        }
                        continuousRecoTextBuilder.append(recognizedSegmentText);
                    }
                    final String fullStableText = continuousRecoTextBuilder.toString().trim();
                    runOnUiThread(() -> {
                        if (speechConfirmationDialog != null && speechConfirmationDialog.isShowing()) {
                            updateSpeechConfirmationDialog(fullStableText.isEmpty() ? "Pending..." : fullStableText);
                        } else if (keepListeningActive) { // Dialog bị đóng bất ngờ
                            showSpeechConfirmationDialog(fullStableText.isEmpty() ? "Pending..." : fullStableText);
                        }
                    });
                }
            } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                Log.d(TAG, "Azure NOMATCH: Speech could not be recognized for this segment. Details: " + e.getResult().getProperties().getProperty("CancellationDetails_ReasonDetailedText", ""));
                if (keepListeningActive && !speechConfirmedManually) {
                    runOnUiThread(() -> {
                        if (speechConfirmationDialog != null && speechConfirmationDialog.isShowing()) {
                            String currentDialogTextInView = tvPartialSpeechTextInDialog.getText().toString();
                            String builtText = continuousRecoTextBuilder.toString().trim();
                            if (builtText.isEmpty() && (currentDialogTextInView.equals("Listening...") || currentDialogTextInView.equals("Chuẩn bị thu âm...")) ) {
                                updateSpeechConfirmationDialog("(Unknown)");
                            } else if (!builtText.isEmpty()){
                                updateSpeechConfirmationDialog(builtText + " (Unknown)");
                            }
                        }
                    });
                }
            }
        });

        azureSpeechRecognizer.canceled.addEventListener((s, e) -> {
            Log.e(TAG, "Azure CANCELED: Reason=" + e.getReason() + ", ErrorDetails=" + e.getErrorDetails() + ", ErrorCode=" + e.getErrorCode());
            final String errorDetails = e.getErrorDetails();
            final String reason = e.getReason().toString();
            runOnUiThread(() -> {
                isCurrentlyListening = false;
                indicateListeningState(false);
                keepListeningActive = false;
                dismissSpeechConfirmationDialog();
                if (controller != null && !speechConfirmedManually) {
                    controller.onSpeechError("Lỗi Azure: " + reason + (TextUtils.isEmpty(errorDetails) ? "" : " - " + errorDetails));
                }
            });
        });

        azureSpeechRecognizer.sessionStopped.addEventListener((s, e) -> {
            Log.d(TAG, "Azure Session STOPPED. SessionId: " + e.getSessionId());
            runOnUiThread(() -> {
                isCurrentlyListening = false;
                indicateListeningState(false);
                if (keepListeningActive && !speechConfirmedManually) {
                    Log.w(TAG, "Azure session stopped unexpectedly while keepListeningActive was true.");
                    if (tvPartialSpeechTextInDialog != null) {
                        String currentText = tvPartialSpeechTextInDialog.getText().toString();
                        // Chỉ cập nhật nếu dialog đang ở trạng thái chờ hoặc không có nhiều text
                        if (currentText.equals("Listening...") || currentText.equals("Getting ready to record...") || continuousRecoTextBuilder.length() < 5) {
                            updateSpeechConfirmationDialog("Session ended. Retry ?");
                        }
                    }
                }
            });
        });

        showSpeechConfirmationDialog("Chuẩn bị thu âm...");
        azureSpeechRecognizer.startContinuousRecognitionAsync();
        Log.d(TAG, "Azure SpeechRecognizer: startContinuousRecognitionAsync called.");
    }


    @Override
    public void stopListening() {
        Log.d(TAG, "Azure stopListening: User or controller requested stop.");
        keepListeningActive = false; // Tắt cờ duy trì lắng nghe

        if (azureSpeechRecognizer != null) {
            try {
                azureSpeechRecognizer.stopContinuousRecognitionAsync();
                // isCurrentlyListening và dialog sẽ được xử lý bởi sessionStopped/canceled events
            } catch (Exception e) {
                Log.e(TAG, "Error stopping Azure recognizer in stopListening: " + e.getMessage());

                isCurrentlyListening = false;
                indicateListeningState(false);
                dismissSpeechConfirmationDialog();
            }
        } else {
            isCurrentlyListening = false;
            indicateListeningState(false);
            dismissSpeechConfirmationDialog();
        }
    }

    @Override
    public void indicateListeningState(boolean isSRListening) {
        if (btnMicro != null) {
            boolean dialogIsOpen = (speechConfirmationDialog != null && speechConfirmationDialog.isShowing());
            btnMicro.setEnabled(!dialogIsOpen && azureSpeechConfig != null && !isSRListening);
            btnMicro.setAlpha((isSRListening && !dialogIsOpen) ? 0.6f : (btnMicro.isEnabled() ? 1.0f : 0.5f) ); // Mờ đi nếu đang nghe (và không có dialog)
            Log.d(TAG, "IndicateListeningState (Azure): isSRListening=" + isSRListening +
                    ", dialogOpen=" + dialogIsOpen +
                    ", azureConfigNull=" + (azureSpeechConfig == null) +
                    ", btnMicroEnabled=" + btnMicro.isEnabled());
        }
    }

    @SuppressLint("InflateParams")
    @Override
    public void showSpeechConfirmationDialog(String initialText) {
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "showSpeechConfirmationDialog: Activity is finishing/destroyed.");
            return;
        }
        if (speechConfirmationDialog != null && speechConfirmationDialog.isShowing()) {
            // Nếu dialog đã hiển thị, chỉ cập nhật text nếu cần và đó không phải là text ban đầu
            if (tvPartialSpeechTextInDialog != null && !TextUtils.equals(tvPartialSpeechTextInDialog.getText(), initialText) &&
                    !(initialText.equals("Listening...") || initialText.equals("Getting ready to record...")) ) { // Không ghi đè lên prompt ban đầu
                tvPartialSpeechTextInDialog.setText(initialText);
            }
            return;
        }
        Log.d(TAG, "showSpeechConfirmationDialog: Creating new dialog with text: " + initialText);
        continuousRecoTextBuilder.setLength(0); // Reset bộ đệm khi dialog mới được tạo

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_speech_confirm, null);
        builder.setView(dialogView);

        tvPartialSpeechTextInDialog = dialogView.findViewById(R.id.tv_partial_speech_text);
        Button btnConfirmSpeech = dialogView.findViewById(R.id.btn_confirm_speech);
        Button btnCancelDialog = dialogView.findViewById(R.id.btn_cancel_speech_dialog);

        if (tvPartialSpeechTextInDialog != null)
            tvPartialSpeechTextInDialog.setText(initialText);

        speechConfirmationDialog = builder.create();
        speechConfirmationDialog.setCanceledOnTouchOutside(false);
        speechConfirmationDialog.setCancelable(false);

        btnConfirmSpeech.setOnClickListener(v -> {
            if (tvPartialSpeechTextInDialog == null) return;
            String finalTextToSubmit = tvPartialSpeechTextInDialog.getText().toString().trim();

            Log.d(TAG, "Dialog: 'Xác nhận' clicked. Final Text: '" + finalTextToSubmit + "'. Stopping Azure session.");

            keepListeningActive = false;
            speechConfirmedManually = true;

            if (azureSpeechRecognizer != null) {
                try { azureSpeechRecognizer.stopContinuousRecognitionAsync(); }
                catch (Exception e) { Log.e(TAG, "Error stopping Azure from Confirm: " + e.getMessage()); }
            }

            if (controller != null) {
                String formattedResponse = finalTextToSubmit;
                if (!TextUtils.isEmpty(formattedResponse) && !(formattedResponse.equals("Listening...") || formattedResponse.equals("Chuẩn bị thu âm...") || formattedResponse.contains("Không nhận dạng"))) {
                    formattedResponse = formattedResponse.substring(0, 1).toUpperCase() + (formattedResponse.length() > 1 ? formattedResponse.substring(1) : "");
                } else if (formattedResponse.contains("Không nhận dạng") || formattedResponse.contains("Đang xử lý") || formattedResponse.contains("Hết giờ") || formattedResponse.contains("Phiên kết thúc")) {
                    formattedResponse = ""; // Gửi chuỗi rỗng nếu là thông báo hệ thống/lỗi
                }
                controller.onSpeechResult(formattedResponse);
            }
            dismissSpeechConfirmationDialog();
        });

        btnCancelDialog.setOnClickListener(v -> {
            Log.d(TAG, "Dialog: 'Hủy' clicked. Stopping Azure session.");
            keepListeningActive = false;
            speechConfirmedManually = false;

            if (azureSpeechRecognizer != null) {
                try { azureSpeechRecognizer.stopContinuousRecognitionAsync(); }
                catch (Exception e) { Log.e(TAG, "Error stopping Azure from Cancel: " + e.getMessage());}
            }
            dismissSpeechConfirmationDialog();
            if (controller != null) {
                //
            }
        });

        if (!isFinishing()) {
            speechConfirmationDialog.show();
            if (btnMicro != null) btnMicro.setEnabled(false);
        }
    }

    @Override
    public void updateSpeechConfirmationDialog(String newTextToDisplay) {
        if (speechConfirmationDialog != null && speechConfirmationDialog.isShowing() && tvPartialSpeechTextInDialog != null) {
            if (TextUtils.isEmpty(newTextToDisplay) &&
                    (tvPartialSpeechTextInDialog.getText().toString().equals("Listening") ||
                            tvPartialSpeechTextInDialog.getText().toString().equals("Getting ready to record..."))) {
                // Không làm gì
            } else {
                tvPartialSpeechTextInDialog.setText(newTextToDisplay);
            }
        } else if ((speechConfirmationDialog == null || !speechConfirmationDialog.isShowing()) && keepListeningActive && !speechConfirmedManually) {
            Log.w(TAG, "updateSpeechConfirmationDialog: Dialog not showing but should be. Re-showing with text: " + newTextToDisplay);
            showSpeechConfirmationDialog(newTextToDisplay);
        }
    }

    @Override
    public void dismissSpeechConfirmationDialog() {
        if (speechConfirmationDialog != null && speechConfirmationDialog.isShowing()) {
            try { speechConfirmationDialog.dismiss(); Log.d(TAG, "dismissSpeechConfirmationDialog: Dialog dismissed."); }
            catch (Exception e) { Log.e(TAG, "Error dismissing dialog", e); }
        }
        speechConfirmationDialog = null;
        tvPartialSpeechTextInDialog = null;
        setMicButtonEnabled(azureSpeechConfig != null && !isCurrentlyListening);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }
}
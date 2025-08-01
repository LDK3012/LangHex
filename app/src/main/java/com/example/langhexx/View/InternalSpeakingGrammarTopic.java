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
import com.example.langhexx.Controller.SpeakingGrammarController;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.SpeakingContract;
import com.example.langhexx.Model.UsernamePasswordSessionManager;
import com.example.langhexx.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
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
import java.io.File;
import java.io.FileOutputStream;
import com.example.langhexx.Util.AzureTTSHelper;
import android.media.MediaPlayer;

public class InternalSpeakingGrammarTopic extends AppCompatActivity implements SpeakingContract.InteractiveSpeakingView {

    private static final String TAG = "InternalSpeakingGrammarTopicView";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private LinearLayout questionContainer;
    private LayoutInflater inflater;
    private ImageButton btnMicro;
    private ImageView imgClose, imgHome;
    private ScrollView scrollViewContent;
    private TextToSpeech textToSpeech;
    private SoundPool soundPool;
    private int correctSoundId, incorrectSoundId;
    private boolean soundsLoaded = false;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private View currentQaView;
    private TextView txtCurrentQuestion, txtResponse, tvTitle;
    private ImageView responseSpeaker, avatarUser, iconWarning, questionSpeaker;

    private SpeakingContract.Controller controller;
    private FirebaseAuth mAuth;
    private UsernamePasswordSessionManager sessionManager;
    private ExecutorService avatarExecutorService;
    private static final String MS_GRAPH_PREFS = "MSGraphPrefs";
    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token";
    private static final String MICROSOFT_PROVIDER_ID = "microsoft.com";

    private static final String AZURE_SPEECH_KEY = "75aMORlAm3JGJXfz0oOcHaX3hytrGyJ9MBRUfRGutW5qeZSuFjz3JQQJ99BEACYeBjFXJ3w3AAAYACOGDbeK"; // Replace with your key
    private static final String AZURE_SPEECH_REGION = "eastus";
    private SpeechConfig azureSpeechConfig;
    private SpeechRecognizer azureSpeechRecognizer;
    private AudioConfig azureAudioConfig;
    private StringBuilder continuousRecoTextBuilder = new StringBuilder();
    private boolean isCurrentlyListening = false, speechConfirmedManually = false, keepListeningActive = false;
    private AlertDialog speechConfirmationDialog;
    private TextView tvPartialSpeechTextInDialog;
    private MediaPlayer ttsPlayer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internal_speaking_grammar_topic);

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new UsernamePasswordSessionManager(this);
        avatarExecutorService = Executors.newSingleThreadExecutor();

        String levelName = getIntent().getStringExtra("levelName");
        String topicDisplayTitle = getIntent().getStringExtra("topicTitle");
        String topicIdFromIntent = getIntent().getStringExtra("topicId");
        String effectiveIdentifier = topicIdFromIntent;

        if (effectiveIdentifier == null || effectiveIdentifier.isEmpty()) {
            Log.w(TAG, "topicId from Intent is null/empty. Using topicDisplayTitle as identifier.");
            effectiveIdentifier = topicDisplayTitle;
        }

        if (levelName == null || effectiveIdentifier == null || levelName.isEmpty() || effectiveIdentifier.isEmpty()) {
            showError("Critical Error: Missing Level or Topic identifier.");
            Log.e(TAG, "Missing critical identifiers. Level: " + levelName + ", EffectiveID: " + effectiveIdentifier);
            finish();
            return;
        }
        controller = new SpeakingGrammarController(this, this, levelName, effectiveIdentifier);

        addControls();
        tvTitle.setText((topicDisplayTitle != null && !topicDisplayTitle.isEmpty()) ? topicDisplayTitle : effectiveIdentifier);
        addEvent();
        initializeSoundPool();
        initTextToSpeech();

        try {
            if (AZURE_SPEECH_KEY.equals("YOUR_AZURE_SPEECH_KEY") || AZURE_SPEECH_REGION.equals("YOUR_AZURE_REGION")) {
                showError("Azure Speech Key/Region not configured.");
                Log.e(TAG, "Azure Speech Key/Region not set. Please update constants.");
                if (btnMicro != null) btnMicro.setEnabled(false);
            } else {
                azureSpeechConfig = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SPEECH_REGION);
                azureSpeechConfig.setSpeechRecognitionLanguage("en-US");
                azureAudioConfig = AudioConfig.fromDefaultMicrophoneInput();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Azure SpeechConfig: " + e.getMessage(), e);
            showError("Azure Speech setup error: " + e.getMessage());
            if (btnMicro != null) btnMicro.setEnabled(false);
        }

        if (controller != null) controller.viewDidLoad();
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
        if (imgClose != null) imgClose.setOnClickListener(v -> { if (controller != null) controller.onCloseButtonClicked(); else finishActivity(); });
        if (imgHome != null) imgHome.setOnClickListener(view -> { if (controller != null) controller.onHomeButtonClicked(); else defaultHomeAction(); });
        if (btnMicro != null) btnMicro.setOnClickListener(v -> handleMicClick());
    }

    private void defaultHomeAction() {
        Intent intent = new Intent(InternalSpeakingGrammarTopic.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finishActivity();
    }

    private void handleMicClick() {
        if (azureSpeechConfig == null) { showToast("Speech recognition service not ready."); return; }
        if (speechConfirmationDialog != null && speechConfirmationDialog.isShowing()) { Log.d(TAG, "Mic click ignored: Dialog is showing."); return; }
        if (!isCurrentlyListening) {
            Log.d(TAG, "Mic clicked: Starting SR session.");
            if (controller != null) controller.onMicButtonClicked();
        } else {
            Log.d(TAG, "Mic clicked: SR active. Requesting stop.");
            stopListening();
        }
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    showToast("US English TTS not supported.");
                } else {
                    Log.d(TAG, "TTS initialized.");
                    textToSpeech.setPitch(1.1f); textToSpeech.setSpeechRate(0.95f);
                }
            } else {
                Log.e(TAG, "TTS initialization failed: " + status);
                showToast("Failed to initialize TTS.");
            }
            if (controller instanceof SpeakingGrammarController) ((SpeakingGrammarController) controller).onTtsReady();
        });
    }

    private void initializeSoundPool() {
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        soundPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(aa).build();
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            soundsLoaded = (status == 0);
            if (!soundsLoaded) showToast("Failed to load sound effect: " + sampleId);
        });
        try {
            correctSoundId = soundPool.load(this, R.raw.correct_answer, 1);
            incorrectSoundId = soundPool.load(this, R.raw.wrong_answer, 1);
        } catch (Exception e) { Log.e(TAG, "Error loading sound files", e); showToast("Sound files not found."); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (textToSpeech != null && textToSpeech.isSpeaking()) textToSpeech.stop();
        if (isCurrentlyListening || (speechConfirmationDialog != null && speechConfirmationDialog.isShowing())) {
            Log.d(TAG, "onPause: Stopping Azure SR & dismissing dialog.");
            keepListeningActive = false; isCurrentlyListening = false;
            if (azureSpeechRecognizer != null) try { azureSpeechRecognizer.stopContinuousRecognitionAsync(); } catch (Exception e) { Log.e(TAG, "Error stopping Azure SR in onPause: " + e.getMessage()); }
            indicateListeningState(false);
            dismissSpeechConfirmationDialog();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called.");
        keepListeningActive = false; isCurrentlyListening = false;
        dismissSpeechConfirmationDialog();
        if (controller != null) { controller.onDestroy(); controller = null; }
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); textToSpeech = null; }
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        if (uiHandler != null) uiHandler.removeCallbacksAndMessages(null);
        if (azureSpeechRecognizer != null) { try { azureSpeechRecognizer.stopContinuousRecognitionAsync().get(); } catch (Exception e) { Log.w(TAG, "Exception on Azure SR stop/close: " + e.getMessage()); } azureSpeechRecognizer.close(); azureSpeechRecognizer = null; }
        if (azureAudioConfig != null) { azureAudioConfig.close(); azureAudioConfig = null; }
        azureSpeechConfig = null;
        if (avatarExecutorService != null && !avatarExecutorService.isShutdown()) avatarExecutorService.shutdown();
        if (ttsPlayer != null) {
            ttsPlayer.release();
            ttsPlayer = null;
        }
    }

    @Override
    public void displayQuestionToAnswer(String question) {
        updateUiForNewQuestion(question);
    }

    @SuppressLint("InflateParams")
    @Override
    public void updateUiForNewQuestion(String question) {
        currentQaView = inflater.inflate(R.layout.custom_question_list, questionContainer, false);
        txtCurrentQuestion = currentQaView.findViewById(R.id.txtQuestion);
        questionSpeaker = currentQaView.findViewById(R.id.speaker);
        txtResponse = currentQaView.findViewById(R.id.tvResponse);
        responseSpeaker = currentQaView.findViewById(R.id.responseSpeaker);
        avatarUser = currentQaView.findViewById(R.id.userAvatar);
        iconWarning = currentQaView.findViewById(R.id.iconWarning);
        txtCurrentQuestion.setText(question);
        questionSpeaker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (controller != null) controller.playTtsWithAzure(getContext(), question);
            }
        });
        hideResponseElements();
        questionContainer.addView(currentQaView);
        setMicButtonEnabled(azureSpeechConfig != null);
        scrollDown();
    }

    @Override
    public void hideResponseElements() {
        if (txtResponse != null) txtResponse.setVisibility(View.GONE);
        if (responseSpeaker != null) responseSpeaker.setVisibility(View.GONE);
        if (avatarUser != null) avatarUser.setVisibility(View.GONE);
        if (iconWarning != null) { iconWarning.setVisibility(View.GONE); iconWarning.setOnClickListener(null); }
    }

    @Override
    public void displayUserAnswer(String userAnswer, boolean isCorrect) {
        if (currentQaView == null || txtResponse == null || responseSpeaker == null || avatarUser == null) { Log.e(TAG, "UI elements for answer are null."); return; }
        txtResponse.setText(userAnswer);
        txtResponse.setTextColor(ContextCompat.getColor(this, isCorrect ? R.color.Lime : R.color.Red));
        txtResponse.setVisibility(View.VISIBLE);
        responseSpeaker.setVisibility(View.VISIBLE);
        responseSpeaker.setOnClickListener(v -> { if (controller != null) controller.onResponseSpeakerClicked(userAnswer); });
        if (avatarUser.getVisibility() != View.VISIBLE) { loadAvatarBasedOnLogin(avatarUser); avatarUser.setVisibility(View.VISIBLE); }
        scrollDown();
    }

    private void loadAvatarBasedOnLogin(ImageView targetView) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            boolean isMs = false; for (UserInfo p : user.getProviderData()) if (MICROSOFT_PROVIDER_ID.equals(p.getProviderId())) { isMs = true; break; }
            if (isMs) { String token = getMsGraphToken(); if (!TextUtils.isEmpty(token)) fetchMicrosoftProfilePhoto(token, targetView); else loadDefaultAvatar(targetView, R.drawable.unknown_avatar); }
            else { Uri url = user.getPhotoUrl(); if (url != null) Glide.with(this).load(url).circleCrop().placeholder(R.drawable.unknown_avatar).error(R.drawable.unknown_avatar).into(targetView); else loadDefaultAvatar(targetView, R.drawable.unknown_avatar); }
        } else if (sessionManager.isLoggedIn()) loadDefaultAvatar(targetView, R.drawable.avatar);
        else loadDefaultAvatar(targetView, R.drawable.unknown_avatar);
    }

    private void loadDefaultAvatar(ImageView target, int resId) { if (isFinishing() || isDestroyed() || target == null) return; Glide.with(this).load(resId).circleCrop().placeholder(resId).error(R.drawable.unknown_avatar).into(target); }
    private String getMsGraphToken() { return getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE).getString(MS_GRAPH_TOKEN_KEY, null); }

    private void fetchMicrosoftProfilePhoto(String token, ImageView targetView) {
        if (avatarExecutorService == null || avatarExecutorService.isShutdown()) { runOnUiThread(() -> loadDefaultAvatar(targetView, R.drawable.unknown_avatar)); return; }
        avatarExecutorService.execute(() -> {
            HttpURLConnection conn = null; InputStream is = null; ByteArrayOutputStream bos = null; byte[] photo = null;
            try {
                conn = (HttpURLConnection) new URL("https://graph.microsoft.com/v1.0/me/photo/$value").openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) { is = new BufferedInputStream(conn.getInputStream()); bos = new ByteArrayOutputStream(); byte[] d = new byte[1024]; int r; while ((r = is.read(d)) != -1) bos.write(d, 0, r); bos.flush(); photo = bos.toByteArray(); }
                else Log.e(TAG, "MS Graph Photo API Error: " + conn.getResponseCode());
            } catch (IOException e) { Log.e(TAG, "IOException MS Photo", e); }
            finally { try { if (is != null) is.close(); if (bos != null) bos.close(); } catch (IOException ignored) {} if (conn != null) conn.disconnect(); }
            final byte[] finalPhoto = photo;
            if (!isFinishing() && !isDestroyed()) runOnUiThread(() -> { String key = (mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "ms_user") + "_" + System.currentTimeMillis(); if (finalPhoto != null) Glide.with(this).load(finalPhoto).circleCrop().signature(new ObjectKey(key)).placeholder(R.drawable.unknown_avatar).error(R.drawable.unknown_avatar).into(targetView); else loadDefaultAvatar(targetView, R.drawable.unknown_avatar); });
        });
    }

    @Override
    public void displayEvaluationFeedback(String feedbackEn, String suggestionEn) { // Changed from feedbackVi
        StringBuilder detailedMessage = new StringBuilder();
        boolean hasContent = false;
        if (feedbackEn != null && !feedbackEn.trim().isEmpty()) {
            detailedMessage.append("Explanation:\n").append(feedbackEn.trim()); // English label
            hasContent = true;
        }
        if (suggestionEn != null && !suggestionEn.trim().isEmpty()) {
            if (hasContent) detailedMessage.append("\n\n");
            detailedMessage.append("Suggested Answer:\n").append(suggestionEn.trim()); // English label
            hasContent = true;
        }
        if (hasContent) showFeedbackDialog(detailedMessage.toString());
        else Log.d(TAG, "displayEvaluationFeedback: No detailed feedback or suggestion to show.");
    }

    @Override
    public void showWarningIcon(boolean show, String feedbackMessage) {
        if (iconWarning == null) return;
        if (show) {
            iconWarning.setVisibility(View.VISIBLE);
            Drawable icon = ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_info);
            if (icon != null) { icon = DrawableCompat.wrap(icon).mutate(); DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.Red)); iconWarning.setImageDrawable(icon); }
            iconWarning.setOnClickListener(v -> { if (controller != null) controller.onWarningIconClicked(feedbackMessage); });
        } else { iconWarning.setVisibility(View.GONE); iconWarning.setOnClickListener(null); }
    }

    @Override
    public void showFeedbackDialog(String message) {
        if (isFinishing() || isDestroyed()) return;
        Drawable icon = ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_info);
        new AlertDialog.Builder(this)
                .setTitle("Feedback & Suggestion")
                .setMessage(message)
                .setIcon(icon)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public void showCompletionMessage() {
        if (questionContainer.findViewWithTag("completion_msg") == null) {
            TextView tv = new TextView(this); tv.setText("Congratulations! All questions completed!");
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); tv.setPadding(16,32,16,32); tv.setTag("completion_msg");
            questionContainer.addView(tv);
        }
        setMicButtonEnabled(false);
    }

    @Override
    public void showError(String message) {
        showToast("Error: " + message); Log.e(TAG, "Error displayed: " + message);
        if (questionContainer != null && questionContainer.getChildCount() == 0 && currentQaView == null && !isFinishing()) {
            questionContainer.removeAllViews(); TextView tv = new TextView(this);
            tv.setText("An error occurred: " + message + "\nPlease try again or check connection.");
            tv.setTextColor(Color.RED); tv.setPadding(16,16,16,16); tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            questionContainer.addView(tv);
        }
    }

    @Override
    public void showToast(String message) { if (!isFinishing() && message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    @Override
    public void onTtsPlayStart() {

    }

    @Override
    public void onTtsPlayEnd() {

    }

    @Override
    public void onTtsPlayError(String error) {

    }

    @Override
    public void showCustomToast(boolean success, String message) {
        if (isFinishing()) return;
        if (success) CustomToast.showSuccess(this, message, R.drawable.success);
        else CustomToast.showFail(this, message, R.drawable.fail_icon);
    }

    @Override
    public void playSound(boolean isCorrect) {
        if (!soundsLoaded || soundPool == null) { Log.w(TAG, "Sounds not loaded or SoundPool null."); return; }
        int id = isCorrect ? correctSoundId : incorrectSoundId;
        if (id != 0) soundPool.play(id, 1.0f, 1.0f, 1, 0, 1.0f);
        else Log.w(TAG, "Sound ID is 0 for correct=" + isCorrect);
    }

    @Override public Context getContext() { return this; }
    @Override public void scrollDown() { if (scrollViewContent != null) uiHandler.post(() -> scrollViewContent.fullScroll(View.FOCUS_DOWN)); }
    @Override public void finishActivity() { finish(); }

    @Override
    public void setMicButtonEnabled(boolean enabled) {
        if (btnMicro == null) return;
        boolean dialogOpen = speechConfirmationDialog != null && speechConfirmationDialog.isShowing();
        boolean actualState = enabled && !dialogOpen && azureSpeechConfig != null && !isCurrentlyListening;
        btnMicro.setEnabled(actualState);
        btnMicro.setAlpha(actualState ? 1.0f : 0.5f);
        Log.d(TAG, "setMicButton: req=" + enabled + ",dialog=" + dialogOpen + ",cfg=" + (azureSpeechConfig!=null) + ",listening=" + isCurrentlyListening + "->final=" + actualState);
    }

    @Override
    public void speakText(String text, String utteranceId) {
        if (textToSpeech != null && !TextUtils.isEmpty(text) && textToSpeech.getEngines().size() > 0) {
            if (isCurrentlyListening && azureSpeechRecognizer != null) { Log.d(TAG, "TTS: Stopping active Azure SR."); try { azureSpeechRecognizer.stopContinuousRecognitionAsync(); } catch (Exception e) { Log.e(TAG, "Error stopping Azure for TTS", e); }}
            dismissSpeechConfirmationDialog();
            Bundle params = new Bundle(); params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        } else { Log.e(TAG, "TTS not ready or text empty."); showToast("TTS not available."); }
    }

    @Override
    public void requestAudioPermission() { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET}, REQUEST_RECORD_AUDIO_PERMISSION); }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            boolean audioGranted = false;
            for(int i=0; i<permissions.length; i++) if(Manifest.permission.RECORD_AUDIO.equals(permissions[i]) && grantResults[i]==PackageManager.PERMISSION_GRANTED) audioGranted = true;
            if (controller != null) controller.onPermissionResult(audioGranted);
            if (!audioGranted) { showToast("Recording permission needed."); setMicButtonEnabled(false); }
            else setMicButtonEnabled(azureSpeechConfig != null && !isCurrentlyListening);
        }
    }

    @Override
    public void startListening() {
        if (azureSpeechConfig == null) { showToast("SR service not ready."); Log.e(TAG, "Azure SR config null."); return; }
        if (azureAudioConfig == null) { azureAudioConfig = AudioConfig.fromDefaultMicrophoneInput(); if (azureAudioConfig == null) { showToast("Mic input config failed."); Log.e(TAG, "Azure AudioConfig null."); return; }}
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestAudioPermission(); return; }
        if (!isNetworkAvailable()) { showError("No internet. Check connection."); return; }
        if (isCurrentlyListening || (speechConfirmationDialog != null && speechConfirmationDialog.isShowing())) { Log.d(TAG, "SR startListening: Ignored. Busy."); return; }
        if (textToSpeech != null && textToSpeech.isSpeaking()) textToSpeech.stop();

        if (azureSpeechRecognizer != null) { try { azureSpeechRecognizer.stopContinuousRecognitionAsync().get(); azureSpeechRecognizer.close(); } catch (Exception e) { Log.w(TAG, "Exception closing prev SR", e); } azureSpeechRecognizer = null; }
        azureSpeechRecognizer = new SpeechRecognizer(azureSpeechConfig, azureAudioConfig);
        speechConfirmedManually = false; keepListeningActive = true; continuousRecoTextBuilder.setLength(0);

        azureSpeechRecognizer.sessionStarted.addEventListener((s, e) -> runOnUiThread(() -> { isCurrentlyListening = true; indicateListeningState(true); showSpeechConfirmationDialog("Listening..."); Log.d(TAG, "Azure Session STARTED: " + e.getSessionId()); }));
        azureSpeechRecognizer.recognizing.addEventListener((s, e) -> { if (e.getResult().getReason() == ResultReason.RecognizingSpeech) { String pt = e.getResult().getText(); Log.d(TAG, "Azure RECOGNIZING: " + pt); if (keepListeningActive && !speechConfirmedManually && !TextUtils.isEmpty(pt)) runOnUiThread(() -> updateSpeechConfirmationDialog(continuousRecoTextBuilder.toString() + (continuousRecoTextBuilder.length()>0?" ":"") + pt)); }});
        azureSpeechRecognizer.recognized.addEventListener((s, e) -> {
            if (e.getResult().getReason() == ResultReason.RecognizedSpeech) { String rt = e.getResult().getText(); Log.d(TAG, "Azure RECOGNIZED: " + rt); if (keepListeningActive && !speechConfirmedManually) { if (!TextUtils.isEmpty(rt)) { if (continuousRecoTextBuilder.length()>0) continuousRecoTextBuilder.append(" "); continuousRecoTextBuilder.append(rt); } final String fst = continuousRecoTextBuilder.toString().trim(); runOnUiThread(() -> { if (speechConfirmationDialog != null && speechConfirmationDialog.isShowing()) updateSpeechConfirmationDialog(fst.isEmpty() ? "Listening..." : fst); else if (keepListeningActive) showSpeechConfirmationDialog(fst.isEmpty() ? "Listening..." : fst); }); }}
            else if (e.getResult().getReason() == ResultReason.NoMatch) { Log.d(TAG, "Azure NOMATCH: " + e.getResult().getProperties().getProperty("CancellationDetails_ReasonDetailedText", "No speech.")); if (keepListeningActive && !speechConfirmedManually) runOnUiThread(() -> { if (speechConfirmationDialog!=null && speechConfirmationDialog.isShowing()){ String cdt = tvPartialSpeechTextInDialog!=null?tvPartialSpeechTextInDialog.getText().toString():""; String bt = continuousRecoTextBuilder.toString().trim(); if(!bt.isEmpty())updateSpeechConfirmationDialog(bt+" (No further match)"); else if(cdt.equals("Listening...")||cdt.equals("Preparing to record..."))updateSpeechConfirmationDialog("(No speech detected)");}}); }
        });
        azureSpeechRecognizer.canceled.addEventListener((s, e) -> { Log.e(TAG, "Azure CANCELED: R=" + e.getReason() + ",ErrD=" + e.getErrorDetails() + ",ErrC=" + e.getErrorCode()); final String ed=e.getErrorDetails(); final String r=e.getReason().toString(); runOnUiThread(()->{isCurrentlyListening=false;indicateListeningState(false);keepListeningActive=false;dismissSpeechConfirmationDialog();if(controller!=null&&!speechConfirmedManually)controller.onSpeechError("SR error: "+r+(TextUtils.isEmpty(ed)?"":". "+ed));}); });
        azureSpeechRecognizer.sessionStopped.addEventListener((s, e) -> runOnUiThread(() -> { Log.d(TAG, "Azure Session STOPPED: " + e.getSessionId()); isCurrentlyListening = false; indicateListeningState(false); if(keepListeningActive&&!speechConfirmedManually){Log.w(TAG,"Azure session stopped unexpectedly.");if(speechConfirmationDialog!=null&&speechConfirmationDialog.isShowing()){String ct=tvPartialSpeechTextInDialog!=null?tvPartialSpeechTextInDialog.getText().toString():"";if(ct.equals("Listening...")||ct.equals("Preparing to record...")||continuousRecoTextBuilder.length()==0)updateSpeechConfirmationDialog("Session ended. Try again.");}}}));
        azureSpeechRecognizer.startContinuousRecognitionAsync(); Log.d(TAG, "Azure SR: startContinuousRecognitionAsync called.");
    }

    @Override
    public void stopListening() {
        Log.d(TAG, "SR stopListening called. keepListeningActive=false.");
        keepListeningActive = false;
        if (azureSpeechRecognizer != null) { try { azureSpeechRecognizer.stopContinuousRecognitionAsync(); } catch (Exception e) { Log.e(TAG, "Error stopping Azure SR", e); isCurrentlyListening=false;indicateListeningState(false);dismissSpeechConfirmationDialog();}}
        else { isCurrentlyListening=false;indicateListeningState(false);dismissSpeechConfirmationDialog(); }
    }

    @Override
    public void indicateListeningState(boolean isSRListening) {
        if (btnMicro == null) return;
        boolean dialogOpen = speechConfirmationDialog != null && speechConfirmationDialog.isShowing();
        boolean canClick = azureSpeechConfig != null && !dialogOpen;
        btnMicro.setEnabled(canClick);
        btnMicro.setAlpha(canClick ? 1.0f : 0.5f);
        // TODO: Change btnMicro icon based on isSRListening (e.g., to stop icon if listening)
        Log.d(TAG, "IndicateListening: SRlistening="+isSRListening+", dialogOpen="+dialogOpen+", micEnabled="+btnMicro.isEnabled());
    }

    @SuppressLint("InflateParams")
    @Override
    public void showSpeechConfirmationDialog(String initialText) {
        if (isFinishing() || isDestroyed()) { Log.w(TAG, "Activity finishing, cannot show SR dialog."); return; }
        if (speechConfirmationDialog != null && speechConfirmationDialog.isShowing()) { if (tvPartialSpeechTextInDialog!=null&&!TextUtils.equals(tvPartialSpeechTextInDialog.getText(),initialText)) if(!initialText.contains("Listening")&&!initialText.contains("Preparing")) tvPartialSpeechTextInDialog.setText(initialText); else if(tvPartialSpeechTextInDialog.getText().toString().contains("Listening")||tvPartialSpeechTextInDialog.getText().toString().contains("Preparing")) tvPartialSpeechTextInDialog.setText(initialText); return; }
        Log.d(TAG, "Creating SR dialog. Initial text: '" + initialText + "'");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_speech_confirm, null);
        tvPartialSpeechTextInDialog = dialogView.findViewById(R.id.tv_partial_speech_text);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm_speech);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_speech_dialog);
        if(tvPartialSpeechTextInDialog!=null) tvPartialSpeechTextInDialog.setText(initialText);

        AlertDialog.Builder builder = new AlertDialog.Builder(this).setView(dialogView);
        speechConfirmationDialog = builder.create();
        speechConfirmationDialog.setCanceledOnTouchOutside(false); speechConfirmationDialog.setCancelable(false);

        btnConfirm.setOnClickListener(v -> {
            if(tvPartialSpeechTextInDialog==null){Log.e(TAG,"SR Confirm: tvPartial is null!"); return;} String finalTxt = tvPartialSpeechTextInDialog.getText().toString().trim();
            Log.d(TAG, "SR Confirm: Text='" + finalTxt + "'. speechConfirmedManually=true.");
            keepListeningActive=false; speechConfirmedManually=true;
            if(azureSpeechRecognizer!=null) try{azureSpeechRecognizer.stopContinuousRecognitionAsync();}catch(Exception e){Log.e(TAG,"Err stop SR from Confirm",e);} else {isCurrentlyListening=false;indicateListeningState(false);}
            dismissSpeechConfirmationDialog(); // Dismiss first then process
            if(controller!=null){ String res=finalTxt; if(!TextUtils.isEmpty(res)&&!(res.toLowerCase().contains("listen")||res.toLowerCase().contains("prepare")||res.toLowerCase().contains("no match")||res.toLowerCase().contains("no speech")||res.toLowerCase().contains("session end")||res.equalsIgnoreCase("(unknown)"))) res=res.substring(0,1).toUpperCase()+(res.length()>1?res.substring(1):""); else res=""; Log.d(TAG,"SR Confirm: Submitting '"+res+"'"); controller.onSpeechResult(res);}
        });
        btnCancel.setOnClickListener(v -> {
            Log.d(TAG, "SR Cancel. keepListeningActive=false.");
            keepListeningActive=false; speechConfirmedManually=false;
            if(azureSpeechRecognizer!=null) try{azureSpeechRecognizer.stopContinuousRecognitionAsync();}catch(Exception e){Log.e(TAG,"Err stop SR from Cancel",e);} else {isCurrentlyListening=false;indicateListeningState(false);}
            dismissSpeechConfirmationDialog();
            // if(controller!=null) controller.onSpeechCancelled();
        });
        if(!isFinishing()) {speechConfirmationDialog.show(); if(btnMicro!=null)btnMicro.setEnabled(false);}
        else Log.w(TAG, "Activity finishing, SR dialog not shown.");
    }

    @Override
    public void updateSpeechConfirmationDialog(String newText) {
        if (speechConfirmationDialog!=null && speechConfirmationDialog.isShowing() && tvPartialSpeechTextInDialog!=null) { if (TextUtils.isEmpty(newText) && (tvPartialSpeechTextInDialog.getText().toString().contains("Listen")||tvPartialSpeechTextInDialog.getText().toString().contains("Prepar"))) {} else tvPartialSpeechTextInDialog.setText(newText); }
        else if ((speechConfirmationDialog==null||!speechConfirmationDialog.isShowing())&&keepListeningActive&&!speechConfirmedManually) { Log.w(TAG, "SR Dialog not showing but active. Re-showing. Text: "+newText); showSpeechConfirmationDialog(newText); }
    }

    @Override
    public void dismissSpeechConfirmationDialog() {
        if (speechConfirmationDialog!=null && speechConfirmationDialog.isShowing()) { try {speechConfirmationDialog.dismiss(); Log.d(TAG,"SR Dialog dismissed.");} catch(Exception e){Log.e(TAG,"Err dismissing SR dialog",e);}}
        speechConfirmationDialog=null; tvPartialSpeechTextInDialog=null;
        setMicButtonEnabled(azureSpeechConfig!=null && !isCurrentlyListening);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm != null ? cm.getActiveNetworkInfo() : null;
        return netInfo != null && netInfo.isConnected();
    }
}
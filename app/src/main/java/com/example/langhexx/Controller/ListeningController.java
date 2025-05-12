package com.example.langhexx.Controller;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.langhexx.Model.Exercise;
import com.example.langhexx.Model.ListeningQuestion;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ListeningController implements
        TextToSpeech.OnInitListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener {

    private static final String TAG = "ListeningController";
    private final String SYNTHESIS_UTTERANCE_ID = "SynthUtteranceId_" + UUID.randomUUID().toString();

    public interface ViewInterface {
        Context getContext();
        void displayExerciseTitle(String title);
        void updateAdapterData(List<ListeningQuestion> newQuestions);
        void showResultsInAdapter(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctnessMap);
        void resetAdapterState();
        void setSubmitButtonState(int state, String text, boolean enabled);
        Map<Integer, Integer> getAdapterSelectedAnswers();
        boolean areAdapterAnswersAllCorrect();
        void showToast(String message);
        void showFailToast(String message);
        void showConfirmationDialog(String title, String message, Runnable onConfirm);
        void navigateToNextExercise(String levelName, String topicId, String nextExerciseId, String topicDisplayTitle, String nextExerciseDisplayTitle);
        void finishActivity();
        void scrollToQuestion(int index);
        void setUIElementsVisibility(boolean visible);

        void showAudioLoading();
        void showAudioReady(int duration);
        void updateAudioProgress(int progress, int max);
        void setPlayButtonState(boolean isPlaying);
        void resetAudioControls();
        void showAudioError(String errorMessage);
    }

    public static final int STATE_SUBMIT = 0;
    public static final int STATE_RETRY = 1;
    public static final int STATE_NEXT = 2;
    public static final int STATE_FINISHED_NO_NEXT = -1;
    private int currentButtonState = STATE_SUBMIT;

    private ViewInterface view;
    private String levelName;
    private String topicId;
    private String exerciseId;
    private String topicDisplayTitle;
    private String exerciseDisplayTitle;

    private String scriptToSpeak;
    private List<ListeningQuestion> questionsList;
    private ArrayList<Exercise> allExercisesInTopic;
    private boolean exerciseDataLoaded = false;
    private boolean allExercisesLoaded = false;

    private DatabaseReference databaseReference;
    private TextToSpeech tts;
    private boolean isTtsInitialized = false;
    private File audioFile;
    private MediaPlayer mediaPlayer;
    private boolean isMediaPlayerPrepared = false;
    private boolean isPlaying = false;
    private Handler progressHandler = new Handler();
    private Runnable updateSeekBarRunnable;

    public ListeningController(ViewInterface view, Intent intent) {
        this.view = view;
        this.questionsList = new ArrayList<>();
        this.allExercisesInTopic = new ArrayList<>();
        this.databaseReference = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        if (intent != null) {
            levelName = intent.getStringExtra("LEVEL_NAME");
            topicId = intent.getStringExtra("TOPIC_ID");
            exerciseId = intent.getStringExtra("EXERCISE_ID");
            topicDisplayTitle = intent.getStringExtra("TOPIC_TITLE");
            exerciseDisplayTitle = intent.getStringExtra("EXERCISE_TITLE");

            Log.d(TAG, "Intent data: Level=" + levelName + ", TopicID=" + topicId +
                    ", ExerciseID=" + exerciseId + ", TopicTitle=" + topicDisplayTitle +
                    ", ExerciseTitle=" + exerciseDisplayTitle);
        } else {
            handleInitializationError("Error: Intent is null.");
            return;
        }

        if (levelName == null || topicId == null || exerciseId == null || topicDisplayTitle == null || exerciseDisplayTitle == null) {
            handleInitializationError("Error: Missing identifiers in Intent.");
            return;
        }
    }

    public void initialize() {
        Log.d(TAG, "Initializing Controller...");
        if (view == null) {
            Log.e(TAG, "ViewInterface is null in initialize. Cannot proceed.");
            return;
        }
        view.setUIElementsVisibility(false);
        view.displayExerciseTitle(exerciseDisplayTitle);
        view.resetAudioControls();
        initializeTextToSpeech();
        loadExerciseDataFromFirebase();
        loadAllExercisesInTopicFromFirebase();
    }

    private void handleInitializationError(String errorMessage) {
        Log.e(TAG, errorMessage);
        if (view != null) {
            view.showToast(errorMessage);
            view.finishActivity();
        }
    }

    private void initializeTextToSpeech() {
        Log.d(TAG, "Initializing TextToSpeech...");
        if (view == null || view.getContext() == null) {
            Log.e(TAG, "Cannot initialize TTS: View or Context is null.");
            if (view != null) {
                view.showAudioError("Internal error preparing audio.");
                view.resetAudioControls();
            }
            return;
        }
        try {
            tts = new TextToSpeech(view.getContext(), this);
        } catch (Exception e) {
            Log.e(TAG, "Exception initializing TTS", e);
            if (view != null) {
                view.showAudioError("Failed to initialize Text-to-Speech component.");
                view.resetAudioControls();
            }
        }
    }

    @Override
    public void onInit(int status) {
        if (view == null) return;
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language US not supported, trying default English.");
                result = tts.setLanguage(Locale.ENGLISH);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS language English is not supported.");
                    view.showAudioError("Required TTS language not supported.");
                    view.resetAudioControls();
                    isTtsInitialized = false;
                    return;
                }
            }
            Log.i(TAG, "TTS Initialized successfully with language: " + (tts.getLanguage() != null ? tts.getLanguage().getDisplayName() : "Unknown"));
            isTtsInitialized = true;
            setupTTSListener();
            if (scriptToSpeak != null && !scriptToSpeak.isEmpty() && audioFile == null) {
                Log.d(TAG, "TTS ready, starting synthesis from onInit.");
                synthesizeScriptToFile();
            } else {
                Log.d(TAG, "TTS ready, waiting for script or synthesis already in progress/done.");
            }
        } else {
            Log.e(TAG, "TTS Initialization failed! Status code: " + status);
            view.showAudioError("Failed to initialize Text-to-Speech engine (Code: " + status + ").");
            view.resetAudioControls();
            isTtsInitialized = false;
        }
    }

    private void setupTTSListener() {
        if (tts == null || view == null) return;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS Synthesis started: " + utteranceId);
            }
            @Override
            public void onDone(String utteranceId) {
                if (view == null) return;
                Log.d(TAG, "TTS Synthesis done: " + utteranceId);
                if (SYNTHESIS_UTTERANCE_ID.equals(utteranceId)) {
                    if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                        Log.i(TAG, "Synthesis successful. Setting up MediaPlayer.");
                        setupMediaPlayer();
                    } else {
                        Log.e(TAG, "Synthesis done, but file is invalid or missing.");
                        if (audioFile != null) Log.e(TAG, "File path: "+ audioFile.getAbsolutePath() + " Exists: "+ audioFile.exists() + " Length: "+ audioFile.length());
                        audioFile = null;
                        view.showAudioError("Error creating audio file.");
                        view.resetAudioControls();
                    }
                }
            }
            @Override
            public void onError(String utteranceId, int errorCode) {
                if (view == null) return;
                Log.e(TAG, "TTS Synthesis error: " + utteranceId + ", Code: " + errorCode);
                if (SYNTHESIS_UTTERANCE_ID.equals(utteranceId)) {
                    if (audioFile != null && audioFile.exists()) {
                        audioFile.delete();
                    }
                    audioFile = null;
                    view.showAudioError("Audio synthesis failed (Error " + errorCode + ")");
                    view.resetAudioControls();
                }
            }
            @Override
            public void onError(String utteranceId) {
                onError(utteranceId, TextToSpeech.ERROR);
            }
        });
    }

    private void loadExerciseDataFromFirebase() {
        if (view == null || levelName == null || topicId == null || exerciseId == null) {
            Log.e(TAG, "Cannot load exercise data: View or Identifiers are null.");
            if(view != null) handleFirebaseLoadError("Internal error: Missing identifiers.");
            return;
        }
        DatabaseReference exerciseRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Listening").child("Topics").child(topicId)
                .child("Exercises").child(exerciseId);

        Log.i(TAG, "Loading Listening exercise data from: " + exerciseRef.toString());
        view.showAudioLoading();

        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (view == null) return;
                if (!snapshot.exists()) {
                    Log.e(TAG, "Exercise data not found at path.");
                    handleFirebaseLoadError("Exercise data not found.");
                    return;
                }
                scriptToSpeak = snapshot.child("script").getValue(String.class);
                boolean hasScript = scriptToSpeak != null && !scriptToSpeak.isEmpty();

                if (hasScript) {
                    Log.d(TAG, "Script loaded (" + scriptToSpeak.length() + " chars).");
                    if (isTtsInitialized && audioFile == null) {
                        Log.d(TAG, "Script loaded, TTS ready, starting synthesis.");
                        synthesizeScriptToFile();
                    } else if (!isTtsInitialized) {
                        Log.d(TAG, "Script loaded, waiting for TTS initialization.");
                    } else {
                        Log.d(TAG, "Script loaded, but TTS not ready or synthesis already done/in progress.");
                        checkAndSetupPlayerFromCache();
                    }
                } else {
                    Log.w(TAG, "'script' field missing or empty in Firebase.");
                    view.showAudioError("No audio script found for this exercise.");
                    view.resetAudioControls();
                }
                DataSnapshot questionsSnapshot = snapshot.child("questions");
                List<ListeningQuestion> loadedQuestions = new ArrayList<>();
                if (questionsSnapshot.exists()) {
                    for (DataSnapshot questionSnap : questionsSnapshot.getChildren()) {
                        try {
                            ListeningQuestion question = parseListeningQuestionSnapshot(questionSnap);
                            if (question != null) loadedQuestions.add(question);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing question: " + questionSnap.getKey(), e);
                        }
                    }
                    Log.i(TAG, "Loaded " + loadedQuestions.size() + " questions.");
                } else {
                    Log.w(TAG, "No 'questions' node found.");
                }
                questionsList.clear();
                questionsList.addAll(loadedQuestions);
                view.updateAdapterData(questionsList);
                if(questionsList.isEmpty()) {
                    view.showToast("No questions available for this exercise.");
                }
                exerciseDataLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase loading cancelled/failed: " + error.getMessage(), error.toException());
                handleFirebaseLoadError("Error loading data: " + error.getMessage());
            }
        });
    }

    private ListeningQuestion parseListeningQuestionSnapshot(DataSnapshot questionSnap) {
        ListeningQuestion question = new ListeningQuestion();
        question.setId(questionSnap.getKey());
        String text = questionSnap.child("questionText").getValue(String.class);
        String answer = questionSnap.child("correctAnswer").getValue(String.class);
        Map<String, String> stringOptionsMap = new HashMap<>();
        DataSnapshot optionsSnapshot = questionSnap.child("options");
        if (optionsSnapshot.exists() && optionsSnapshot.getValue() instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> optionsMapObject = (Map<String, Object>) optionsSnapshot.getValue();
                if (optionsMapObject != null) {
                    for (Map.Entry<String, Object> entry : optionsMapObject.entrySet()) {
                        stringOptionsMap.put(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
            } catch (ClassCastException e) {
                Log.e(TAG, "Error casting options for question: " + questionSnap.getKey(), e);
                return null;
            }
        } else {
            Log.w(TAG, "'options' node missing or invalid format for question: " + questionSnap.getKey());
        }
        if (text != null && !text.isEmpty() && answer != null && !answer.isEmpty() && !stringOptionsMap.isEmpty()) {
            question.setQuestionText(text);
            question.setOptions(stringOptionsMap);
            question.setCorrectAnswer(answer);
            return question;
        } else {
            Log.w(TAG, "Skipping question due to missing essential data: Key=" + questionSnap.getKey());
            return null;
        }
    }

    private void loadAllExercisesInTopicFromFirebase() {
        if (view == null || levelName == null || topicId == null) {
            Log.e(TAG, "Cannot load all exercises: View, LevelName or TopicID null.");
            return;
        }
        DatabaseReference exercisesPathRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Listening").child("Topics").child(topicId)
                .child("Exercises");

        Log.d(TAG, "Loading all Listening exercises from: " + exercisesPathRef.toString());
        exercisesPathRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (view == null) return;
                allExercisesInTopic.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot exSnap : snapshot.getChildren()) {
                        String exId = exSnap.getKey();
                        String exTitle = exSnap.child("title").getValue(String.class);
                        if (exTitle == null || exTitle.isEmpty()) {
                            exTitle = exId;
                            Log.w(TAG, "Exercise ID " + exId + " in topic " + topicId + " (Listening) is missing title. Using ID.");
                        }
                        if (exId != null) {
                            allExercisesInTopic.add(new Exercise(exId, exTitle));
                        }
                    }
                    Log.i(TAG, "Loaded " + allExercisesInTopic.size() + " exercises for topic ID: " + topicId);
                } else {
                    Log.w(TAG, "No exercises found under topic ID: " + topicId + " (Listening)");
                }
                allExercisesLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load all Listening exercise identifiers: " + error.getMessage());
                allExercisesLoaded = true;
                if (view != null) checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void handleFirebaseLoadError(String message) {
        if (view != null) {
            view.showToast(message);
            view.resetAudioControls();
            if(!exerciseDataLoaded) {
                view.finishActivity();
            }
        }
    }

    private void checkIfAllDataLoadedAndReady() {
        if (exerciseDataLoaded && allExercisesLoaded && view != null) {
            Log.d(TAG, "All initial data (exercise + titles) loaded for Listening.");
            boolean hasContentToShow = (scriptToSpeak != null && !scriptToSpeak.isEmpty()) || !questionsList.isEmpty();
            view.setUIElementsVisibility(hasContentToShow);

            boolean canSubmit = !questionsList.isEmpty();
            resetSubmitButtonToSubmitState(canSubmit);

            if (!hasContentToShow && !questionsList.isEmpty()) { // Only questions, no script
                view.showToast("Audio script is missing, but questions are available.");
            } else if (!hasContentToShow && questionsList.isEmpty()) { // No script, no questions
                // If no content and no next exercise, button might become "Finish"
                if(!hasNextExercise()){
                    setSubmitButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
                } else {
                    // If there is a next exercise, but this one has no content, allow "Next"
                    setSubmitButtonState(STATE_NEXT, "Next Exercise");
                }
            }
        }
    }

    private void synthesizeScriptToFile() {
        if (view == null || view.getContext() == null) return;
        if (!isTtsInitialized) {
            Log.w(TAG, "Cannot synthesize: TTS not ready.");
            view.showAudioError("Text-to-Speech engine is not ready.");
            view.resetAudioControls();
            return;
        }
        if (scriptToSpeak == null || scriptToSpeak.isEmpty()) {
            Log.w(TAG, "Cannot synthesize: Script is empty or null.");
            view.resetAudioControls();
            return;
        }
        if (checkAndSetupPlayerFromCache()) {
            Log.d(TAG, "Synthesis skipped, using existing file/player.");
            return;
        }
        try {
            File outputDir = view.getContext().getCacheDir();
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("Failed to create cache directory for TTS audio.");
            }
            String filename = "listen_" + levelName.hashCode() + "_" + topicId.hashCode() + "_" + exerciseId.hashCode() + ".wav";
            audioFile = new File(outputDir, filename);
            if (audioFile.exists()) {
                audioFile.delete();
            }
            Log.i(TAG, "Starting TTS synthesis to file: " + audioFile.getAbsolutePath());
            view.showAudioLoading();
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, SYNTHESIS_UTTERANCE_ID);
            int result = tts.synthesizeToFile(scriptToSpeak, params, audioFile.getAbsolutePath());
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "synthesizeToFile immediate failure. Code: " + result);
                if (audioFile.exists()) audioFile.delete();
                audioFile = null;
                view.showAudioError("Failed to start audio synthesis (Code: " + result + ").");
                view.resetAudioControls();
            } else {
                Log.d(TAG, "synthesizeToFile request submitted successfully.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during synthesis preparation", e);
            if (audioFile != null && audioFile.exists()) audioFile.delete();
            audioFile = null;
            if (view != null) {
                view.showAudioError("Error preparing audio synthesis: " + e.getMessage());
                view.resetAudioControls();
            }
        }
    }

    private boolean checkAndSetupPlayerFromCache() {
        if (view == null || view.getContext() == null) return false;
        try {
            File outputDir = view.getContext().getCacheDir();
            String filename = "listen_" + levelName.hashCode() + "_" + topicId.hashCode() + "_" + exerciseId.hashCode() + ".wav";
            File cachedFile = new File(outputDir, filename);
            if (cachedFile.exists() && cachedFile.length() > 0) {
                Log.i(TAG, "Found existing synthesized file: " + cachedFile.getAbsolutePath());
                audioFile = cachedFile;
                view.showAudioLoading();
                setupMediaPlayer();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking/using cached audio file", e);
        }
        return false;
    }

    private void setupMediaPlayer() {
        if (view == null) return;
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            Log.e(TAG, "Cannot setup MediaPlayer: Audio file is invalid.");
            view.showAudioError("Audio file not available or corrupted.");
            view.resetAudioControls();
            return;
        }
        releaseMediaPlayer();
        try {
            Log.d(TAG, "Setting up MediaPlayer with source: " + audioFile.getAbsolutePath());
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer Error: what=" + what + ", extra=" + extra);
                String errorMsg = "Error playing audio";
                if(view != null) {
                    view.showAudioError(errorMsg + " (Code: " + what + ")");
                    view.resetAudioControls();
                }
                releaseMediaPlayer();
                return true;
            });
            mediaPlayer.prepareAsync();
            Log.d(TAG, "MediaPlayer prepareAsync() called.");
        } catch (Exception e) {
            Log.e(TAG, "Exception setting up MediaPlayer", e);
            if (view != null) {
                view.showAudioError("Error loading audio: " + e.getMessage());
                view.resetAudioControls();
            }
            releaseMediaPlayer();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mediaPlayer == null || view == null) {
            Log.w(TAG, "onPrepared called but MediaPlayer or View is null.");
            return;
        }
        Log.d(TAG, "MediaPlayer prepared.");
        isMediaPlayerPrepared = true;
        try {
            int duration = mediaPlayer.getDuration();
            if (duration > 0) {
                Log.d(TAG, "Audio duration: " + duration + "ms");
                view.showAudioReady(duration);
            } else {
                Log.w(TAG, "MediaPlayer prepared but duration is invalid: " + duration);
                view.showAudioError("Audio file seems corrupted or has zero duration.");
                view.resetAudioControls();
                releaseMediaPlayer();
                if (audioFile != null && audioFile.exists()) audioFile.delete();
                audioFile = null;
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException in onPrepared.", e);
            if(view != null) {
                view.showAudioError("Error getting audio duration.");
                view.resetAudioControls();
            }
            releaseMediaPlayer();
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mediaPlayer == null || view == null || !isMediaPlayerPrepared) {
            Log.w(TAG, "onCompletion called but player/view invalid or not prepared.");
            return;
        }
        Log.d(TAG, "MediaPlayer playback completed.");
        isPlaying = false;
        stopSeekBarUpdate();
        try {
            view.setPlayButtonState(false);
            int duration = mediaPlayer.getDuration();
            if (duration > 0) view.updateAudioProgress(duration, duration);
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException in onCompletion", e);
            if (view != null) view.resetAudioControls();
            releaseMediaPlayer();
        }
    }

    public void togglePlayPause() {
        if (view == null) return;
        if (!isMediaPlayerPrepared || mediaPlayer == null) {
            Log.w(TAG, "togglePlayPause: MediaPlayer not ready.");
            if (audioFile != null && audioFile.exists()) {
                Log.d(TAG,"Attempting to setup player on togglePlayPause because it wasn't ready.");
                setupMediaPlayer();
            } else {
                view.showAudioError("Audio is not available to play.");
                view.resetAudioControls();
            }
            return;
        }
        try {
            if (isPlaying) {
                mediaPlayer.pause();
                isPlaying = false;
                view.setPlayButtonState(false);
                stopSeekBarUpdate();
                Log.d(TAG, "Playback paused via toggle.");
            } else {
                mediaPlayer.start();
                isPlaying = true;
                view.setPlayButtonState(true);
                startSeekBarUpdate();
                Log.d(TAG, "Playback started/resumed via toggle.");
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException during togglePlayPause", e);
            if (view != null) {
                view.showAudioError("Error controlling playback.");
                view.resetAudioControls();
            }
            releaseMediaPlayer();
        }
    }

    public void seekAudio(int progress) {
        if (mediaPlayer != null && isMediaPlayerPrepared) {
            try {
                mediaPlayer.seekTo(progress);
                if(view != null) view.updateAudioProgress(progress, mediaPlayer.getDuration());
                Log.d(TAG, "Seeked to: " + progress);
            } catch (IllegalStateException e) {
                Log.e(TAG, "IllegalStateException during seekAudio", e);
                if(view != null) {
                    view.showAudioError("Error seeking audio.");
                    view.resetAudioControls();
                }
                releaseMediaPlayer();
            }
        } else {
            Log.w(TAG, "Cannot seek: MediaPlayer not prepared.");
        }
    }

    private void startSeekBarUpdate() {
        stopSeekBarUpdate();
        if (mediaPlayer == null || view == null) return;
        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (mediaPlayer != null && isPlaying && isMediaPlayerPrepared && view != null) {
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        int duration = mediaPlayer.getDuration();
                        view.updateAudioProgress(currentPosition, duration);
                        progressHandler.postDelayed(this, 300);
                    } else {
                        stopSeekBarUpdate();
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "IllegalStateException in updateSeekBarRunnable.", e);
                    stopSeekBarUpdate();
                } catch (Exception e) {
                    Log.e(TAG, "Exception in updateSeekBarRunnable", e);
                    stopSeekBarUpdate();
                }
            }
        };
        progressHandler.post(updateSeekBarRunnable);
    }

    private void stopSeekBarUpdate() {
        if (updateSeekBarRunnable != null) {
            progressHandler.removeCallbacks(updateSeekBarRunnable);
            updateSeekBarRunnable = null;
        }
    }

    public void onSubmitButtonClicked() {
        if (view == null) return;
        Log.d(TAG, "Submit button clicked. Current state: " + currentButtonState);
        pauseAudioPlayback();
        if (questionsList.isEmpty()) {
            Log.w(TAG, "Submit button clicked, but no questions are loaded.");
            if(hasNextExercise()){
                goToNextExercise();
            } else {
                view.showToast("No questions to submit and no next exercise.");
                finishExercise();
            }
            return;
        }
        switch (currentButtonState) {
            case STATE_SUBMIT:
                checkAnswersAndShowConfirmationIfNeeded();
                break;
            case STATE_RETRY:
                retryExercise();
                break;
            case STATE_NEXT:
                goToNextExercise();
                break;
            case STATE_FINISHED_NO_NEXT:
                Log.i(TAG, "'Finish ! Back Now' button clicked. Finishing activity.");
                finishExercise();
                break;
            default:
                Log.w(TAG, "Unknown button state clicked: " + currentButtonState);
                break;
        }
    }

    private void checkAnswersAndShowConfirmationIfNeeded() {
        Log.d(TAG, "Checking answers...");
        if(view == null) return;
        Map<Integer, Integer> userAnswers = view.getAdapterSelectedAnswers();
        int totalQuestions = questionsList.size();
        if (totalQuestions == 0) {
            Log.w(TAG, "checkAnswers: No questions available.");
            updateButtonStateBasedOnResults(true);
            return;
        }
        boolean allAnswered = true;
        int firstUnanswered = -1;
        for (int i = 0; i < totalQuestions; i++) {
            if (userAnswers.getOrDefault(i, -1) == -1) {
                allAnswered = false;
                firstUnanswered = i;
                break;
            }
        }
        if (allAnswered) {
            Log.d(TAG, "All questions answered. Showing confirmation.");
            view.showConfirmationDialog("Confirm", "Are you sure want to submit ?", this::proceedWithSubmission);
        } else {
            Log.d(TAG, "Not all questions answered. First unanswered: " + firstUnanswered);
            view.showFailToast("Please answer all questions!");
            if (firstUnanswered != -1) {
                view.scrollToQuestion(firstUnanswered);
            }
        }
    }

    private void proceedWithSubmission() {
        Log.i(TAG, "Proceeding with submission...");
        if (view == null || questionsList == null) {
            Log.e(TAG,"Cannot proceed, view or question list is null.");
            return;
        }
        Map<Integer, Integer> userAnswers = view.getAdapterSelectedAnswers();
        int totalQuestions = questionsList.size();
        if (totalQuestions == 0) {
            Log.w(TAG, "Proceeding with submission, but no questions exist.");
            updateButtonStateBasedOnResults(true);
            return;
        }
        Log.d(TAG, "Scoring " + totalQuestions + " questions.");
        int correctCount = 0;
        Map<Integer, Boolean> correctnessMap = new HashMap<>();
        for (int i = 0; i < totalQuestions; i++) {
            if (i >= questionsList.size()) {
                Log.w(TAG, "Index out of bounds during submission: " + i);
                continue;
            }
            ListeningQuestion question = questionsList.get(i);
            int selectedRadioButtonId = userAnswers.getOrDefault(i, -1);
            String selectedAnswerKey = mapRadioButtonIdToKey(selectedRadioButtonId);
            boolean isCorrect = question.getCorrectAnswer() != null &&
                    !selectedAnswerKey.isEmpty() &&
                    selectedAnswerKey.equalsIgnoreCase(question.getCorrectAnswer());
            if (isCorrect) {
                correctCount++;
            }
            correctnessMap.put(i, isCorrect);
            Log.v(TAG, "Q" + (i + 1) + ": SelectedID=" + selectedRadioButtonId + " (Key='" + selectedAnswerKey + "'), CorrectKey='" + question.getCorrectAnswer() + "', Result=" + isCorrect);
        }
        String resultMessage = String.format(Locale.getDefault(), "Result: %d / %d correct!", correctCount, totalQuestions);
        view.showToast(resultMessage);
        Log.i(TAG, "Final Score: " + correctCount + "/" + totalQuestions);
        view.showResultsInAdapter(userAnswers, correctnessMap);
        boolean allCorrect = (correctCount == totalQuestions);
        updateButtonStateBasedOnResults(allCorrect);
        Log.d(TAG, "Submission process finished. Button state: " + currentButtonState);
    }

    private String mapRadioButtonIdToKey(int selectedRadioButtonId) {
        if (selectedRadioButtonId == com.example.langhexx.R.id.rbOptionA) return "A";
        if (selectedRadioButtonId == com.example.langhexx.R.id.rbOptionB) return "B";
        if (selectedRadioButtonId == com.example.langhexx.R.id.rbOptionC) return "C";
        if (selectedRadioButtonId == com.example.langhexx.R.id.rbOptionD) return "D";
        return "";
    }

    private void updateButtonStateBasedOnResults(boolean allCorrect) {
        if (view == null) return;
        boolean hasNext = hasNextExercise();
        Log.d(TAG, "updateButtonStateBasedOnResults: AllCorrect=" + allCorrect + ", HasNext=" + hasNext);
        if (allCorrect) {
            if (hasNext) {
                Log.i(TAG, "All correct. Has next exercise. Setting state to NEXT.");
                setSubmitButtonState(STATE_NEXT, "Next Exercise");
            } else {
                Log.i(TAG, "All correct. Last exercise. Setting state to FINISHED_NO_NEXT.");
                setSubmitButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
            }
        } else {
            Log.i(TAG, "Some answers incorrect. Setting state to RETRY.");
            setSubmitButtonState(STATE_RETRY, "Retry");
        }
    }

    private void retryExercise() {
        Log.i(TAG, "Retry button clicked. Resetting state.");
        if(view == null) return;
        view.resetAdapterState();
        resetSubmitButtonToSubmitState(true);
        view.scrollToQuestion(0);
        if(mediaPlayer != null && isMediaPlayerPrepared) {
            pauseAudioPlayback();
            seekAudio(0);
            if (view != null && mediaPlayer != null) { // Add null check for mediaPlayer
                view.updateAudioProgress(0, mediaPlayer.getDuration());
            }
        }
        Log.d(TAG, "Exercise state reset for retry. Button state: SUBMIT");
    }

    private void goToNextExercise() {
        Log.i(TAG, "Attempting to go to next Listening exercise.");
        if (view == null || !allExercisesLoaded || allExercisesInTopic == null || allExercisesInTopic.isEmpty()) {
            Log.e(TAG, "Cannot go to next: View null or AllExercises list not ready.");
            if(view != null) view.showToast("Could not determine the next exercise.");
            setSubmitButtonState(STATE_FINISHED_NO_NEXT, "Error - Finish");
            return;
        }
        int currentIndex = -1;
        for (int i = 0; i < allExercisesInTopic.size(); i++) {
            if (allExercisesInTopic.get(i).getId().equals(exerciseId)) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex >= 0 && currentIndex < allExercisesInTopic.size() - 1) {
            Exercise nextExercise = allExercisesInTopic.get(currentIndex + 1);
            Log.i(TAG, "Navigating to next Listening exercise: ID=" + nextExercise.getId() + ", Title=" + nextExercise.getTitle());
            view.navigateToNextExercise(levelName, topicId, nextExercise.getId(), topicDisplayTitle, nextExercise.getTitle());
        } else {
            Log.w(TAG, "goToNext called, but no next Listening exercise found. CurrentIndex=" + currentIndex + ", ListSize=" + allExercisesInTopic.size());
            if(view != null) view.showToast("You have completed all Listening exercises in this topic!");
            setSubmitButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
        }
    }

    private void finishExercise() {
        Log.i(TAG, "finishExercise called.");
        if (view != null) {
            view.finishActivity();
        }
    }

    private boolean hasNextExercise() {
        if (allExercisesLoaded && allExercisesInTopic != null && !allExercisesInTopic.isEmpty() && exerciseId != null) {
            int currentIndex = -1;
            for (int i = 0; i < allExercisesInTopic.size(); i++) {
                if (allExercisesInTopic.get(i).getId().equals(exerciseId)) {
                    currentIndex = i;
                    break;
                }
            }
            boolean hasNext = currentIndex >= 0 && currentIndex < allExercisesInTopic.size() - 1;
            Log.d(TAG, "hasNextExercise (Listening) Check: CurrentExerciseID=" + exerciseId + ", CurrentIndex=" + currentIndex + ", ListSize=" + allExercisesInTopic.size() + ", HasNext=" + hasNext);
            return hasNext;
        }
        Log.d(TAG, "hasNextExercise (Listening) Check: AllExercisesLoaded=" + allExercisesLoaded + ", List empty/null=" + (allExercisesInTopic == null || allExercisesInTopic.isEmpty()));
        return false;
    }

    private void setSubmitButtonState(int state, String text) {
        Log.d(TAG, "Setting submit button state: " + state + " (" + text + ")");
        currentButtonState = state;
        if (view != null) {
            boolean enabled = (state != STATE_FINISHED_NO_NEXT || (state == STATE_SUBMIT && !questionsList.isEmpty()));
            view.setSubmitButtonState(state, text, enabled);
        }
    }

    private void resetSubmitButtonToSubmitState(boolean enabled) {
        currentButtonState = STATE_SUBMIT;
        if (view != null) {
            view.setSubmitButtonState(STATE_SUBMIT, "Submit", enabled && !questionsList.isEmpty());
        }
    }

    private void pauseAudioPlayback() {
        if (mediaPlayer != null && isPlaying && isMediaPlayerPrepared) {
            try {
                mediaPlayer.pause();
                isPlaying = false;
                if(view != null) view.setPlayButtonState(false);
                stopSeekBarUpdate();
                Log.d(TAG, "Audio playback paused.");
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error pausing media player", e);
            }
        }
    }

    public void onPause() {
        Log.d(TAG, "onPause called by View.");
        pauseAudioPlayback();
        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception e) { Log.w(TAG,"Error stopping TTS on pause", e);}
        }
    }

    public void onResume() {
        Log.d(TAG, "onResume called by View.");
        if (mediaPlayer != null && isMediaPlayerPrepared && view != null) {
            try {
                view.setPlayButtonState(isPlaying);
                if (isPlaying) {
                    startSeekBarUpdate();
                } else {
                    if (mediaPlayer != null) { // Add null check
                        view.updateAudioProgress(mediaPlayer.getCurrentPosition(), mediaPlayer.getDuration());
                    }
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "Error restoring state onResume", e);
                if (view != null) { // Add null check for view
                    view.resetAudioControls();
                }
                releaseMediaPlayer();
            }
        } else if (view != null && (mediaPlayer == null || !isMediaPlayerPrepared)) {
            view.resetAudioControls();
        }
    }

    public void onDestroy() {
        Log.i(TAG, "onDestroy called by View. Releasing resources.");
        releaseMediaPlayer();
        if (tts != null) {
            Log.d(TAG, "Shutting down TTS engine.");
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) { Log.e(TAG,"TTS shutdown exception", e); }
            tts = null;
            isTtsInitialized = false;
        }
        if (audioFile != null && audioFile.exists()) {
            Log.d(TAG, "Deleting temp file: " + audioFile.getName());
            if (!audioFile.delete()) {
                Log.w(TAG, "Failed to delete temp audio file: " + audioFile.getAbsolutePath());
            }
            audioFile = null;
        }
        stopSeekBarUpdate();
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
        }
        this.view = null;
        Log.i(TAG, "Controller onDestroy finished.");
    }

    private void releaseMediaPlayer() {
        Log.d(TAG, "Releasing MediaPlayer...");
        stopSeekBarUpdate();
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
                Log.i(TAG, "MediaPlayer released.");
            } catch (Exception e) {
                Log.e(TAG, "Exception releasing MediaPlayer", e);
            } finally {
                mediaPlayer = null;
            }
        }
        isMediaPlayerPrepared = false;
        isPlaying = false;
    }
}
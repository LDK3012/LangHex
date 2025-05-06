package com.example.langhexx.Controller; // Or your appropriate package

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.langhexx.Model.ListeningQuestion; // Use ListeningQuestion model
import com.example.langhexx.R; // Required for mapping RadioButton IDs

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

    // --- View Interface ---
    public interface ViewInterface {
        // Standard View Operations
        Context getContext(); // Controller needs context for TTS/File ops
        void displayExerciseTitle(String title);
        void updateAdapterData(List<ListeningQuestion> newQuestions);
        void showResultsInAdapter(Map<Integer, Integer> userAnswers, Map<Integer, Boolean> correctnessMap);
        void resetAdapterState();
        void setSubmitButtonState(int state, String text, boolean enabled);
        Map<Integer, Integer> getAdapterSelectedAnswers();
        boolean areAdapterAnswersAllCorrect();
        void showToast(String message);
        void showFailToast(String message); // Assuming CustomToast is accessible
        void showConfirmationDialog(String title, String message, Runnable onConfirm);
        void navigateToNextExercise(String levelName, String topicTitle, String nextExerciseTitle);
        void finishActivity();
        void scrollToQuestion(int index);
        void setUIElementsVisibility(boolean visible); // Overall visibility

        // Audio Control Specific Operations
        void showAudioLoading();
        void showAudioReady(int duration); // Pass duration to set seekbar max
        void updateAudioProgress(int progress, int max); // Update seekbar progress
        void setPlayButtonState(boolean isPlaying); // true for playing (show pause icon), false for paused (show play icon)
        void resetAudioControls(); // Reset play button, seekbar to initial disabled state
        void showAudioError(String errorMessage); // Show an error related to audio
    }

    // --- State Constants (Submit Button) ---
    public static final int STATE_SUBMIT = 0;
    public static final int STATE_RETRY = 1;
    public static final int STATE_NEXT = 2;
    public static final int STATE_FINISHED_HAS_NEXT = 3; // Might not be needed if logic combines NEXT/FINISHED
    public static final int STATE_FINISHED_NO_NEXT = -1;
    private int currentButtonState = STATE_SUBMIT;

    // --- Data Members ---
    private ViewInterface view;
    private String levelName;
    private String topicTitle;
    private String exerciseTitle;
    private String scriptToSpeak;
    private List<ListeningQuestion> questionsList;
    private ArrayList<String> allExerciseTitles;
    private boolean exerciseDataLoaded = false;
    private boolean titlesLoaded = false;

    // --- Firebase ---
    private DatabaseReference databaseReference;

    // --- TTS Members ---
    private TextToSpeech tts;
    private boolean isTtsInitialized = false;
    private File audioFile; // Synthesized audio file

    // --- MediaPlayer Members ---
    private MediaPlayer mediaPlayer;
    private boolean isMediaPlayerPrepared = false;
    private boolean isPlaying = false;
    private Handler progressHandler = new Handler();
    private Runnable updateSeekBarRunnable;


    public ListeningController(ViewInterface view, Intent intent) {
        this.view = view;
        this.questionsList = new ArrayList<>();
        this.allExerciseTitles = new ArrayList<>();
        // Ensure you have the correct Firebase URL if not using the default
        this.databaseReference = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        // Extract data from Intent
        if (intent != null) {
            levelName = intent.getStringExtra("LEVEL_NAME");
            topicTitle = intent.getStringExtra("TOPIC_TITLE");
            exerciseTitle = intent.getStringExtra("EXERCISE_TITLE");
            Log.d(TAG, "Intent data: Level=" + levelName + ", Topic=" + topicTitle + ", Exercise=" + exerciseTitle);
        } else {
            handleInitializationError("Error: Intent is null.");
            return;
        }

        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            handleInitializationError("Error: Missing exercise identifiers in Intent.");
            return;
        }
    }

    // Called by View after basic setup
    public void initialize() {
        Log.d(TAG, "Initializing Controller...");
        view.setUIElementsVisibility(false); // Hide main content initially
        view.displayExerciseTitle(exerciseTitle); // Show title early
        view.resetAudioControls(); // Set audio controls to initial disabled state
        initializeTextToSpeech(); // Start TTS initialization
        loadExerciseDataFromFirebase(); // Start loading script and questions
        loadAllExerciseTitlesFromFirebase(); // Start loading list of all exercises
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
        try {
            // View provides the context needed for TTS initialization
            tts = new TextToSpeech(view.getContext(), this);
        } catch (Exception e) {
            Log.e(TAG, "Exception initializing TTS", e);
            if (view != null) {
                view.showAudioError("Failed to initialize Text-to-Speech component.");
                view.resetAudioControls();
            }
        }
    }

    // --- TextToSpeech.OnInitListener ---
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US); // Prioritize US English
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language US not supported, trying default English.");
                result = tts.setLanguage(Locale.ENGLISH);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS language English is not supported.");
                    if (view != null) {
                        view.showAudioError("Required TTS language not supported.");
                        view.resetAudioControls();
                    }
                    isTtsInitialized = false;
                    return;
                }
            }

            Log.i(TAG, "TTS Initialized successfully with language: " + (tts.getLanguage() != null ? tts.getLanguage() : "Unknown"));
            isTtsInitialized = true;
            setupTTSListener(); // Set up the utterance progress listener

            // If script arrived while TTS was initializing, start synthesis now
            if (scriptToSpeak != null && !scriptToSpeak.isEmpty() && audioFile == null) {
                Log.d(TAG, "TTS ready, starting synthesis from onInit.");
                synthesizeScriptToFile();
            } else {
                Log.d(TAG, "TTS ready, waiting for script or synthesis already in progress/done.");
            }

        } else {
            Log.e(TAG, "TTS Initialization failed! Status code: " + status);
            if (view != null) {
                view.showAudioError("Failed to initialize Text-to-Speech engine (Code: " + status + ").");
                view.resetAudioControls();
            }
            isTtsInitialized = false;
        }
    }

    private void setupTTSListener() {
        if (tts == null) return;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS Synthesis started: " + utteranceId);
                // Optional: Update view if needed
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS Synthesis done: " + utteranceId);
                // Ensure we are acting on the correct synthesis request
                if (SYNTHESIS_UTTERANCE_ID.equals(utteranceId) && view != null) {
                    // Check if the file was created successfully
                    if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                        Log.i(TAG, "Synthesis successful. Setting up MediaPlayer.");
                        // Still showing loading, setupMediaPlayer will handle hiding it
                        setupMediaPlayer();
                    } else {
                        Log.e(TAG, "Synthesis done, but file is invalid or missing.");
                        if (audioFile != null) Log.e(TAG, "File path: "+ audioFile.getAbsolutePath() + " Exists: "+ audioFile.exists() + " Length: "+ audioFile.length());
                        audioFile = null; // Invalidate the file reference
                        view.showAudioError("Error creating audio file.");
                        view.resetAudioControls();
                    }
                }
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                Log.e(TAG, "TTS Synthesis error: " + utteranceId + ", Code: " + errorCode);
                if (SYNTHESIS_UTTERANCE_ID.equals(utteranceId) && view != null) {
                    // Clean up potentially corrupted file
                    if (audioFile != null && audioFile.exists()) {
                        audioFile.delete();
                    }
                    audioFile = null;
                    view.showAudioError("Audio synthesis failed (Error " + errorCode + ")");
                    view.resetAudioControls();
                }
            }
            // Deprecated but sometimes needed for older OS versions
            @Override
            public void onError(String utteranceId) {
                onError(utteranceId, TextToSpeech.ERROR);
            }
        });
    }

    private void loadExerciseDataFromFirebase() {
        if (levelName == null || topicTitle == null || exerciseTitle == null) {
            Log.e(TAG, "Cannot load exercise data: Identifiers are null.");
            // Error handled in initialize()
            return;
        }
        DatabaseReference exerciseRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Listening").child("Topics").child(topicTitle).child("Exercises").child(exerciseTitle); // Target "Listening"

        Log.i(TAG, "Loading exercise data from: " + exerciseRef.toString());

        if (view != null) view.showAudioLoading(); // Show loading for audio synthesis/setup

        exerciseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "Exercise data not found at path.");
                    handleFirebaseLoadError("Exercise data not found.");
                    return;
                }

                // --- Load Script ---
                scriptToSpeak = snapshot.child("script").getValue(String.class);
                boolean hasScript = scriptToSpeak != null && !scriptToSpeak.isEmpty();

                if (hasScript) {
                    Log.d(TAG, "Script loaded (" + scriptToSpeak.length() + " chars).");
                    // Only start synthesis if TTS is ready AND we haven't synthesized yet
                    if (isTtsInitialized && audioFile == null) {
                        Log.d(TAG, "Script loaded, TTS ready, starting synthesis.");
                        synthesizeScriptToFile(); // This manages the loading indicator
                    } else if (!isTtsInitialized) {
                        Log.d(TAG, "Script loaded, waiting for TTS initialization.");
                        // Loading indicator is already shown
                    } else {
                        Log.d(TAG, "Script loaded, but TTS not ready or synthesis already done/in progress.");
                        // If file exists from previous run/cache, try setting up player
                        checkAndSetupPlayerFromCache();
                    }
                } else {
                    Log.w(TAG, "'script' field missing or empty in Firebase.");
                    if (view != null) {
                        view.showAudioError("No audio script found for this exercise.");
                        view.resetAudioControls(); // Reset audio controls as there's no audio
                    }
                }

                // --- Load Questions ---
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
                if (view != null) {
                    view.updateAdapterData(questionsList); // Update adapter via View Interface
                    if(questionsList.isEmpty()) {
                        view.showToast("No questions available for this exercise.");
                        // If no script AND no questions, maybe finish? Or rely on button state logic.
                    }
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
        // Similar parsing logic as ReadingController, but creates ListeningQuestion
        ListeningQuestion question = new ListeningQuestion();
        question.setId(questionSnap.getKey()); // Use Firebase key as ID

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
                        stringOptionsMap.put(entry.getKey(), String.valueOf(entry.getValue())); // Convert to String
                    }
                }
            } catch (ClassCastException e) {
                Log.e(TAG, "Error casting options for question: " + questionSnap.getKey(), e);
            }
        } else {
            Log.w(TAG, "'options' node missing or invalid format for question: " + questionSnap.getKey());
        }

        // Validate essential fields
        if (text != null && !text.isEmpty() && answer != null && !answer.isEmpty() && !stringOptionsMap.isEmpty()) {
            question.setQuestionText(text);
            question.setOptions(stringOptionsMap);
            question.setCorrectAnswer(answer);
            // Question number can be set by adapter based on position
            return question;
        } else {
            Log.w(TAG, "Skipping question due to missing essential data: Key=" + questionSnap.getKey() +
                    ", Text=" + (text != null && !text.isEmpty()) +
                    ", Answer=" + (answer != null && !answer.isEmpty()) +
                    ", Options=" + !stringOptionsMap.isEmpty());
            return null;
        }
    }

    private void loadAllExerciseTitlesFromFirebase() {
        if (levelName == null || topicTitle == null) {
            Log.e(TAG, "Cannot load titles: Level/Topic null.");
            return;
        }
        DatabaseReference exercisesRef = databaseReference
                .child("Lessons").child("Levels").child(levelName)
                .child("Listening").child("Topics").child(topicTitle).child("Exercises"); // Target "Listening"

        Log.d(TAG, "Loading all titles from: " + exercisesRef.toString());
        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allExerciseTitles.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot exSnap : snapshot.getChildren()) {
                        String title = exSnap.getKey();
                        if (title != null && !title.isEmpty()) {
                            allExerciseTitles.add(title);
                        }
                    }
                    // Optional: Sort titles if needed
                    // Collections.sort(allExerciseTitles);
                    Log.i(TAG, "Loaded " + allExerciseTitles.size() + " titles for topic: " + topicTitle);
                } else {
                    Log.w(TAG, "No exercises found under topic path: " + topicTitle);
                }
                titlesLoaded = true;
                checkIfAllDataLoadedAndReady();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load all exercise titles: " + error.getMessage());
                titlesLoaded = true; // Mark as loaded (with error) to allow UI to proceed if needed
                checkIfAllDataLoadedAndReady();
            }
        });
    }

    private void handleFirebaseLoadError(String message) {
        if (view != null) {
            view.showToast(message);
            view.resetAudioControls(); // Ensure audio controls are reset on error
            // Consider finishing if essential data failed
            if(!exerciseDataLoaded) { // Finish if the main exercise data failed
                view.finishActivity();
            }
        }
    }

    private void checkIfAllDataLoadedAndReady() {
        // This check is now more about enabling the submit button and general UI
        // Audio readiness is handled separately by its own callbacks
        if (exerciseDataLoaded && titlesLoaded && view != null) {
            Log.d(TAG, "All initial data (exercise + titles) loaded.");
            boolean hasContentToShow = (scriptToSpeak != null && !scriptToSpeak.isEmpty()) || !questionsList.isEmpty();
            view.setUIElementsVisibility(hasContentToShow); // Show main layout if there's script or questions
            // Initial button state depends on whether there are questions
            if(questionsList.isEmpty()){
                if (scriptToSpeak != null && !scriptToSpeak.isEmpty()) {
                    // Only script, no questions - maybe a 'Listen Only' state?
                    // For now, disable submit
                    resetSubmitButtonToSubmitState(false); // Disable submit
                } else {
                    // No script, no questions - error state, likely already handled
                    resetSubmitButtonToSubmitState(false);
                }
            } else {
                // Has questions, enable submit button initially
                resetSubmitButtonToSubmitState(true); // Enable submit
            }
        }
    }

    // --- Audio Synthesis and Playback ---

    private void synthesizeScriptToFile() {
        if (view == null) return; // View detached

        if (!isTtsInitialized) {
            Log.w(TAG, "Cannot synthesize: TTS not ready.");
            view.showAudioError("Text-to-Speech engine is not ready.");
            view.resetAudioControls();
            return;
        }
        if (scriptToSpeak == null || scriptToSpeak.isEmpty()) {
            Log.w(TAG, "Cannot synthesize: Script is empty or null.");
            // No need to show error again if already shown during load
            view.resetAudioControls();
            return;
        }
        // Avoid re-synthesis if file exists or player is ready
        if (checkAndSetupPlayerFromCache()) {
            Log.d(TAG, "Synthesis skipped, using existing file/player.");
            return; // Already handled by checkAndSetupPlayerFromCache
        }


        try {
            // Use app's cache directory
            File outputDir = view.getContext().getCacheDir();
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("Failed to create cache directory.");
            }
            // Create a unique filename based on exercise details
            String filename = "listen_" + levelName.hashCode() + "_" + topicTitle.hashCode() + "_" + exerciseTitle.hashCode() + ".wav";
            audioFile = new File(outputDir, filename);

            // Delete old file if it exists but is potentially incomplete/corrupted
            if (audioFile.exists()) { audioFile.delete(); }

            Log.i(TAG, "Starting TTS synthesis to file: " + audioFile.getAbsolutePath());
            view.showAudioLoading(); // Show loading indicator

            // TTS Listener is already set up in onInit
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, SYNTHESIS_UTTERANCE_ID);

            int result = tts.synthesizeToFile(scriptToSpeak, params, audioFile.getAbsolutePath());

            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "synthesizeToFile immediate failure. Code: " + result);
                if (audioFile.exists()) audioFile.delete(); // Clean up failed file attempt
                audioFile = null;
                view.showAudioError("Failed to start audio synthesis (Code: " + result + ").");
                view.resetAudioControls();
            } else {
                Log.d(TAG, "synthesizeToFile request submitted successfully.");
                // Loading indicator remains visible until onDone/onError callback
            }

        } catch (Exception e) { // Catch IOException or other potential errors
            Log.e(TAG, "Exception during synthesis preparation", e);
            if (audioFile != null && audioFile.exists()) audioFile.delete();
            audioFile = null;
            view.showAudioError("Error preparing audio synthesis: " + e.getMessage());
            view.resetAudioControls();
        }
    }

    // Checks if a cached audio file exists and attempts to set up the player
    private boolean checkAndSetupPlayerFromCache() {
        if (view == null) return false;
        try {
            File outputDir = view.getContext().getCacheDir();
            String filename = "listen_" + levelName.hashCode() + "_" + topicTitle.hashCode() + "_" + exerciseTitle.hashCode() + ".wav";
            File cachedFile = new File(outputDir, filename);

            if (cachedFile.exists() && cachedFile.length() > 0) {
                Log.i(TAG, "Found existing synthesized file: " + cachedFile.getAbsolutePath());
                audioFile = cachedFile; // Use the existing file
                view.showAudioLoading(); // Show loading while setting up player
                setupMediaPlayer();
                return true; // Indicates we are using the cached file
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking/using cached audio file", e);
        }
        return false; // No valid cached file found or error occurred
    }


    private void setupMediaPlayer() {
        if (view == null) return; // View detached

        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            Log.e(TAG, "Cannot setup MediaPlayer: Audio file is invalid.");
            view.showAudioError("Audio file not available or corrupted.");
            view.resetAudioControls();
            return;
        }

        releaseMediaPlayer(); // Ensure previous player is released

        try {
            Log.d(TAG, "Setting up MediaPlayer with source: " + audioFile.getAbsolutePath());
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer Error: what=" + what + ", extra=" + extra);
                String errorMsg = "Error playing audio"; // Generic message
                // You could add more specific messages based on 'what' or 'extra' if needed
                if(view != null) {
                    view.showAudioError(errorMsg + " (Code: " + what + ")");
                    view.resetAudioControls();
                }
                releaseMediaPlayer(); // Release faulty player
                // Optionally delete the problematic audio file
                // if (audioFile != null && audioFile.exists()) audioFile.delete();
                // audioFile = null;
                return true; // Error handled
            });

            mediaPlayer.prepareAsync(); // Prepare asynchronously
            Log.d(TAG, "MediaPlayer prepareAsync() called.");
            // Loading indicator remains visible until onPrepared or onError

        } catch (Exception e) { // Catch IOException, IllegalStateException, etc.
            Log.e(TAG, "Exception setting up MediaPlayer", e);
            if (view != null) {
                view.showAudioError("Error loading audio: " + e.getMessage());
                view.resetAudioControls();
            }
            releaseMediaPlayer();
        }
    }

    // --- MediaPlayer.OnPreparedListener ---
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
                view.showAudioReady(duration); // Inform view: hide loading, enable controls, set seekbar max
            } else {
                Log.w(TAG, "MediaPlayer prepared but duration is invalid: " + duration);
                view.showAudioError("Audio file seems corrupted or has zero duration.");
                view.resetAudioControls();
                releaseMediaPlayer();
                // Optionally delete the bad file
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

    // --- MediaPlayer.OnCompletionListener ---
    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mediaPlayer == null || view == null || !isMediaPlayerPrepared) {
            Log.w(TAG, "onCompletion called but player/view invalid or not prepared.");
            return;
        }
        Log.d(TAG, "MediaPlayer playback completed.");
        isPlaying = false;
        stopSeekBarUpdate(); // Stop updates first
        try {
            // Reset UI state
            view.setPlayButtonState(false); // Show play icon
            // Reset seekbar to end, then optionally to start
            int duration = mediaPlayer.getDuration();
            if (duration > 0) view.updateAudioProgress(duration, duration); // Move thumb to end

            // Optional: Seek back to beginning after completion
            // mediaPlayer.seekTo(0);
            // view.updateAudioProgress(0, duration);

        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException in onCompletion", e);
            // Reset might be needed if player state is corrupt
            view.resetAudioControls();
            releaseMediaPlayer();
        }
    }

    // --- Playback Control Methods (Called by View) ---

    public void togglePlayPause() {
        if (view == null) return;

        if (!isMediaPlayerPrepared || mediaPlayer == null) {
            Log.w(TAG, "togglePlayPause: MediaPlayer not ready.");
            // If audioFile exists, maybe try setting it up again?
            if (audioFile != null && audioFile.exists()) {
                Log.d(TAG,"Attempting to setup player on togglePlayPause because it wasn't ready.");
                setupMediaPlayer(); // Will show loading
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
                view.setPlayButtonState(false); // Show Play icon
                stopSeekBarUpdate();
                Log.d(TAG, "Playback paused via toggle.");
            } else {
                mediaPlayer.start();
                isPlaying = true;
                view.setPlayButtonState(true); // Show Pause icon
                startSeekBarUpdate();
                Log.d(TAG, "Playback started/resumed via toggle.");
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException during togglePlayPause", e);
            view.showAudioError("Error controlling playback.");
            view.resetAudioControls();
            releaseMediaPlayer();
        }
    }

    public void seekAudio(int progress) {
        if (mediaPlayer != null && isMediaPlayerPrepared) {
            try {
                mediaPlayer.seekTo(progress);
                // Update the view immediately after seeking
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
            // Optionally inform the user or just ignore seek if player isn't ready
        }
    }

    private void startSeekBarUpdate() {
        stopSeekBarUpdate(); // Ensure no duplicates
        if (mediaPlayer == null || view == null) return;

        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (mediaPlayer != null && isPlaying && isMediaPlayerPrepared && view != null) {
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        int duration = mediaPlayer.getDuration();
                        view.updateAudioProgress(currentPosition, duration);
                        progressHandler.postDelayed(this, 300); // Schedule next update
                    } else {
                        stopSeekBarUpdate(); // Stop if conditions change
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "IllegalStateException in updateSeekBarRunnable.", e);
                    stopSeekBarUpdate();
                    // Consider resetting UI if player state is bad
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

    // --- Submission and Navigation Logic ---

    public void onSubmitButtonClicked() {
        if (view == null) return;
        Log.d(TAG, "Submit button clicked. Current state: " + currentButtonState);

        // Pause audio if playing
        pauseAudioPlayback();

        // Prevent action if no questions loaded
        if (questionsList.isEmpty()) {
            Log.w(TAG, "Submit button clicked, but no questions are loaded.");
            view.showToast("No questions to submit.");
            // Decide what happens if only audio exists: maybe Finish/Next?
            // updateButtonStateBasedOnResults(true); // Treat as complete?
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
            case STATE_FINISHED_HAS_NEXT: // Treat same as NEXT
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

        // Should not happen if button logic is correct, but check anyway
        if (totalQuestions == 0) {
            Log.w(TAG, "checkAnswers: No questions available.");
            updateButtonStateBasedOnResults(true); // Treat as 'all correct' to move on if needed
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
            view.showConfirmationDialog("Xác nhận nộp bài", "Bạn có chắc chắn muốn nộp bài không?", this::proceedWithSubmission);
        } else {
            Log.d(TAG, "Not all questions answered. First unanswered: " + firstUnanswered);
            view.showFailToast("Please answer all questions!"); // Use fail toast
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
            updateButtonStateBasedOnResults(true); // Treat as all correct if no questions
            return;
        }

        Log.d(TAG, "Scoring " + totalQuestions + " questions.");
        int correctCount = 0;
        Map<Integer, Boolean> correctnessMap = new HashMap<>();

        // --- Scoring Logic ---
        for (int i = 0; i < totalQuestions; i++) {
            if (i >= questionsList.size()) {
                Log.w(TAG, "Index out of bounds during submission: " + i);
                continue;
            }
            ListeningQuestion question = questionsList.get(i);
            int selectedRadioButtonId = userAnswers.getOrDefault(i, -1);
            // Map the RadioButton ID back to the answer key ("A", "B", "C", "D")
            // IMPORTANT: This mapping relies on the RadioButton IDs defined in R.java
            // It's generally better if the adapter stores the selected *key* directly,
            // but if it stores the ID, we map it back here.
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

        // Tell the View to update the adapter to show results
        view.showResultsInAdapter(userAnswers, correctnessMap);

        // Update button state based on results and whether there's a next exercise
        boolean allCorrect = (correctCount == totalQuestions);
        updateButtonStateBasedOnResults(allCorrect);

        Log.d(TAG, "Submission process finished. Button state: " + currentButtonState);
    }

    // Helper to map RadioButton Resource IDs to Answer Keys
    private String mapRadioButtonIdToKey(int selectedRadioButtonId) {
        // This needs to match the IDs used in your item_question.xml layout
        if (selectedRadioButtonId == R.id.rbOptionA) return "A";
        if (selectedRadioButtonId == R.id.rbOptionB) return "B";
        if (selectedRadioButtonId == R.id.rbOptionC) return "C";
        if (selectedRadioButtonId == R.id.rbOptionD) return "D";
        return ""; // Return empty if ID is -1 or not recognized
    }

    private void updateButtonStateBasedOnResults(boolean allCorrect) {
        if (view == null) return;
        boolean hasNext = hasNextExercise();
        Log.d(TAG, "updateButtonStateBasedOnResults: AllCorrect=" + allCorrect + ", HasNext=" + hasNext);

        if (allCorrect) {
            if (hasNext) {
                Log.i(TAG, "All correct. Has next exercise. Setting state to NEXT.");
                setSubmitButtonState(STATE_NEXT, "Next");
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

        // 1. Ask the View to reset the adapter's state
        view.resetAdapterState();

        // 2. Reset the Controller's and View's button state back to SUBMIT
        resetSubmitButtonToSubmitState(true); // Enable submit button

        // 3. Ask the View to scroll to the top question (optional)
        view.scrollToQuestion(0);

        // 4. Reset audio playback to the beginning
        if(mediaPlayer != null && isMediaPlayerPrepared) {
            pauseAudioPlayback(); // Ensure it's paused first
            seekAudio(0); // Seek to start
            view.updateAudioProgress(0, mediaPlayer.getDuration()); // Update UI
        }

        Log.d(TAG, "Exercise state reset for retry. Button state: SUBMIT");
    }

    private void goToNextExercise() {
        Log.i(TAG, "Attempting to go to next exercise.");
        if (view == null || !titlesLoaded || allExerciseTitles == null || allExerciseTitles.isEmpty()) {
            Log.e(TAG, "Cannot go to next: View null or Titles list not ready.");
            if(view != null) view.showToast("Could not determine the next exercise.");
            // Fallback: Maybe set to Finish?
            setSubmitButtonState(STATE_FINISHED_NO_NEXT, "Error - Finish");
            return;
        }

        int currentIndex = allExerciseTitles.indexOf(exerciseTitle);
        if (currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1) {
            String nextExerciseTitle = allExerciseTitles.get(currentIndex + 1);
            Log.i(TAG, "Navigating to next exercise: " + nextExerciseTitle);
            // Tell the View to navigate
            view.navigateToNextExercise(levelName, topicTitle, nextExerciseTitle);
        } else {
            Log.w(TAG, "goToNext called, but no next exercise found. CurrentIndex=" + currentIndex + ", ListSize=" + allExerciseTitles.size());
            view.showToast("You have completed all exercises in this topic!");
            setSubmitButtonState(STATE_FINISHED_NO_NEXT, "Finish ! Back Now");
            // finishExercise(); // Optionally finish directly
        }
    }

    private void finishExercise() {
        Log.i(TAG, "finishExercise called.");
        if (view != null) {
            view.finishActivity(); // Tell the view to close itself
        }
    }

    private boolean hasNextExercise() {
        if (titlesLoaded && allExerciseTitles != null && !allExerciseTitles.isEmpty() && exerciseTitle != null) {
            int currentIndex = allExerciseTitles.indexOf(exerciseTitle);
            boolean hasNext = currentIndex >= 0 && currentIndex < allExerciseTitles.size() - 1;
            Log.d(TAG, "hasNextExercise Check: CurrentIndex=" + currentIndex + ", ListSize=" + allExerciseTitles.size() + ", HasNext=" + hasNext);
            return hasNext;
        }
        Log.d(TAG, "hasNextExercise Check: Titles loaded=" + titlesLoaded + ", List empty=" + (allExerciseTitles == null || allExerciseTitles.isEmpty()));
        return false;
    }

    // Sets the internal state and tells the View to update the submit button
    private void setSubmitButtonState(int state, String text) {
        Log.d(TAG, "Setting submit button state: " + state + " (" + text + ")");
        currentButtonState = state;
        if (view != null) {
            boolean enabled = (state == STATE_RETRY || state == STATE_NEXT || state == STATE_FINISHED_NO_NEXT || state == STATE_SUBMIT);
            view.setSubmitButtonState(state, text, enabled);
        }
    }

    private void resetSubmitButtonToSubmitState(boolean enabled) {
        currentButtonState = STATE_SUBMIT;
        if (view != null) {
            view.setSubmitButtonState(STATE_SUBMIT, "Submit", enabled);
            // Explicitly enable/disable based on the parameter
            // TODO: Re-evaluate how enabled state should work here
            // The view's implementation of setSubmitButtonState should handle enabling/disabling
        }
    }

    private void pauseAudioPlayback() {
        if (mediaPlayer != null && isPlaying && isMediaPlayerPrepared) {
            try {
                mediaPlayer.pause();
                isPlaying = false;
                if(view != null) view.setPlayButtonState(false); // Update button icon
                stopSeekBarUpdate();
                Log.d(TAG, "Audio playback paused.");
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error pausing media player", e);
            }
        }
    }


    // --- Lifecycle Hook (Called by View) ---
    public void onPause() {
        Log.d(TAG, "onPause called by View.");
        pauseAudioPlayback();
        // Stop TTS if it happens to be speaking (though unlikely in this flow)
        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception e) { Log.w(TAG,"Error stopping TTS on pause", e);}
        }
    }

    public void onResume() {
        Log.d(TAG, "onResume called by View.");
        // Restore UI state based on actual player state
        if (mediaPlayer != null && isMediaPlayerPrepared && view != null) {
            boolean wasPlaying = false;
            try {
                // Check if it was playing *before* pause (isPlaying flag should be accurate)
                // Or check the actual state if unsure: wasPlaying = mediaPlayer.isPlaying();
                wasPlaying = isPlaying; // Rely on our state flag set in onPause/toggle
                view.setPlayButtonState(wasPlaying); // Set correct icon
                if (wasPlaying) {
                    startSeekBarUpdate(); // Resume updates if it was playing
                } else {
                    // Update seekbar to current pos if paused
                    view.updateAudioProgress(mediaPlayer.getCurrentPosition(), mediaPlayer.getDuration());
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "Error restoring state onResume", e);
                view.resetAudioControls();
                releaseMediaPlayer();
            }
        } else if (view != null && (mediaPlayer == null || !isMediaPlayerPrepared)) {
            // If player isn't ready, ensure controls are in the reset state
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

        // Clear view reference to prevent leaks
        this.view = null;
        Log.i(TAG, "Controller onDestroy finished.");
    }

    // --- Utility to release MediaPlayer ---
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
        // Do NOT reset UI here, calling function should handle UI state
    }
}
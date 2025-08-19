package vn.enghexteam.enghex.Model;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;

public class SpeakingPronunciationModel implements SpeakingContract.Model {
    private static final String TAG = "SpeakingVoiceModel";
    private static final String FIREBASE_DB_URL = "https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private ArrayList<String> scriptList = new ArrayList<>();
    private int currentScriptIndex = 0;
    public SpeakingPronunciationModel(Context context) {
      //
    }

    @Override
    public void loadQuestions(String levelName, String topicId, SpeakingContract.QuestionListener listener) {
        if (levelName == null || levelName.isEmpty() || topicId == null || topicId.isEmpty()) {
            Log.e(TAG, "loadQuestions: LevelName or TopicId is null or empty.");
            listener.onQuestionLoadError("Missing Level Name or Topic ID !");
            return;
        }

        DatabaseReference scriptsRef = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Speaking")
                .child("Pronunciation")
                .child("Topics")
                .child(topicId)
                .child("scripts");

        scriptsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                scriptList.clear();
                currentScriptIndex = 0;
                if (snapshot.exists() && snapshot.hasChildren()) {
                    for (DataSnapshot scriptSnap : snapshot.getChildren()) {
                        String script = scriptSnap.getValue(String.class);
                        if (script != null && !script.trim().isEmpty()) {
                            scriptList.add(script);
                        }
                    }
                    listener.onQuestionsLoaded(new ArrayList<>(scriptList));
                } else {
                    listener.onQuestionLoadError("No script found for this topic !");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onQuestionLoadError("Error loading data from Firebase: " + error.getMessage());
            }
        });
    }

    @Override
    public void evaluateAnswer(String script, String userAnswerRecordingPath, SpeakingContract.EvaluationListener listener) {
        //
    }

    @Override
    public String getCurrentQuestion() {
        if (scriptList != null && !scriptList.isEmpty() && currentScriptIndex >= 0 && currentScriptIndex < scriptList.size()) {
            return scriptList.get(currentScriptIndex);
        }
        return null;
    }

    @Override
    public int getCurrentQuestionIndex() {
        return currentScriptIndex;
    }

    @Override
    public int getQuestionCount() {
        return scriptList != null ? scriptList.size() : 0;
    }

    @Override
    public void advanceQuestionIndex() {
        if (currentScriptIndex < getQuestionCount() - 1) {
            currentScriptIndex++;
        }
    }

    @Override
    public void previousQuestionIndex() {
        if (currentScriptIndex > 0) {
            currentScriptIndex--;
        }
    }

    @Override
    public void cleanup() {
        scriptList.clear();
        currentScriptIndex = 0;
    }
}
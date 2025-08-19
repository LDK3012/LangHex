package vn.enghexteam.enghex.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import vn.enghexteam.enghex.Controller.TopicAdapter;
import vn.enghexteam.enghex.Model.Topics;
import vn.enghexteam.enghex.R;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class ChooseSpeakingPronunciationTopicActivity extends AppCompatActivity {

    private ListView lvTopics;
    private ArrayList<Topics> topicsArrayList;
    private TopicAdapter topicAdapter;
    private String levelName;
    private ImageView imgBack, imgHome;
    private static final String ACTIVITY_TAG = "ChoosePronunciationTopic";
    private final String CURRENT_SUB_SKILL_NAME = "Pronunciation";
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "TopicPrefs";
    private String clickedTopicsKey;
    private Set<String> clickedTopicDisplayNamesForPronunciation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_speaking_pronunciation_topic);

        levelName = getIntent().getStringExtra("levelName");
        if (levelName == null || levelName.isEmpty()) {
            Toast.makeText(this, "Error: Can't identify Levels!", Toast.LENGTH_LONG).show();
            Log.e(ACTIVITY_TAG, "levelName is null or empty!");
            finish();
            return;
        }
        Log.d(ACTIVITY_TAG, "Level received: " + levelName + " for sub-skill " + CURRENT_SUB_SKILL_NAME + " (Current time: " + Calendar.getInstance().getTime() + ")");

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        clickedTopicsKey = levelName + "_Speaking_" + CURRENT_SUB_SKILL_NAME + "_clickedTopicDisplayNames";
        loadClickedTopicDisplayNames();

        addControls();
        loadTopicsFromFirebase();
        addEvents();
    }

    private void loadClickedTopicDisplayNames() {
        Set<String> loadedSet = sharedPreferences.getStringSet(clickedTopicsKey, null);
        if (loadedSet != null) {
            clickedTopicDisplayNamesForPronunciation = new HashSet<>(loadedSet);
        } else {
            clickedTopicDisplayNamesForPronunciation = new HashSet<>();
        }
        Log.d(ACTIVITY_TAG, "Loaded " + (clickedTopicDisplayNamesForPronunciation != null ? clickedTopicDisplayNamesForPronunciation.size() : 0) + " clicked topics (" + CURRENT_SUB_SKILL_NAME + ") from SharedPreferences.");
    }

    private void saveClickedTopicDisplayName(String topicDisplayName) {
        if (clickedTopicDisplayNamesForPronunciation == null) {
            clickedTopicDisplayNamesForPronunciation = new HashSet<>();
        }
        boolean added = clickedTopicDisplayNamesForPronunciation.add(topicDisplayName);

        if (added) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putStringSet(clickedTopicsKey, clickedTopicDisplayNamesForPronunciation);
            editor.apply();
            Log.d(ACTIVITY_TAG, "Saved topic '" + topicDisplayName + "' (" + CURRENT_SUB_SKILL_NAME + ") to SharedPreferences.");
        }
    }

    public void removeClickedTopicHistory(String topicDisplayName) {
        if (clickedTopicDisplayNamesForPronunciation == null) return;

        boolean removed = clickedTopicDisplayNamesForPronunciation.remove(topicDisplayName);
        if (removed) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putStringSet(clickedTopicsKey, clickedTopicDisplayNamesForPronunciation);
            editor.apply();
            Log.d(ACTIVITY_TAG, "Topic history has been deleted '" + topicDisplayName + "' (" + CURRENT_SUB_SKILL_NAME + ") from SharedPreferences.");
            Toast.makeText(this, "Topic '" + topicDisplayName + "' history has been deleted.", Toast.LENGTH_SHORT).show();

            if (topicAdapter != null) {
                topicAdapter.setHighlightedTopicDisplayNames(clickedTopicDisplayNamesForPronunciation);
                topicAdapter.notifyDataSetChanged();
            }
        }
    }

    private void addControls() {
        lvTopics = findViewById(R.id.lvTopics);
        if (lvTopics == null) {
            Log.e(ACTIVITY_TAG, "ListView with ID lvTopics not found in layout activity_choose_speaking_voice_topic.xml");
            Toast.makeText(this, "Layout error: ListView not found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        topicsArrayList = new ArrayList<>();
        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName, "Speaking");
        topicAdapter.setHighlightedTopicDisplayNames(this.clickedTopicDisplayNamesForPronunciation);
        lvTopics.setAdapter(topicAdapter);

        imgBack = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);
    }

    private void loadTopicsFromFirebase() {
        DatabaseReference topicsPathRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Speaking")
                .child(CURRENT_SUB_SKILL_NAME)
                .child("Topics");

        Log.d(ACTIVITY_TAG, "Loading Pronunciation topics from: " + topicsPathRef.toString());

        topicsPathRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(ACTIVITY_TAG, "onDataChange triggered for Pronunciation. Path: " + snapshot.getRef().toString());
                Log.d(ACTIVITY_TAG, "Pronunciation Snapshot exists: " + snapshot.exists());
                Log.d(ACTIVITY_TAG, "Pronunciation Snapshot children count: " + snapshot.getChildrenCount());

                topicsArrayList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot topicSnap : snapshot.getChildren()) {
                        String topicId = topicSnap.getKey();
                        String topicNameDisplay = topicSnap.child("topicName").getValue(String.class);

                        if (topicNameDisplay == null || topicNameDisplay.isEmpty()) {
                            topicNameDisplay = topicId; // Fallback
                            Log.w(ACTIVITY_TAG, "Pronunciation Topic ID " + topicId + " missing topicName, using ID.");
                        }

                        if (topicId != null) {
                            topicsArrayList.add(new Topics(topicId, topicNameDisplay));
                        }
                    }
                    Log.d(ACTIVITY_TAG, "Loaded " + topicsArrayList.size() + " " + CURRENT_SUB_SKILL_NAME + " topics.");
                } else {
                    Log.w(ACTIVITY_TAG, "No " + CURRENT_SUB_SKILL_NAME + " topics found for level: " + levelName + " at " + topicsPathRef.toString());
                    Toast.makeText(ChooseSpeakingPronunciationTopicActivity.this, "No " + CURRENT_SUB_SKILL_NAME + " topics found.", Toast.LENGTH_SHORT).show();
                }

                if (topicAdapter != null) {
                    topicAdapter.setHighlightedTopicDisplayNames(clickedTopicDisplayNamesForPronunciation);
                    topicAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseSpeakingPronunciationTopicActivity.this, "Failed to load Pronunciation topics: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(ACTIVITY_TAG, "Firebase Error for Pronunciation Topics: " + error.getMessage());
            }
        });
    }

    private void addEvents() {
        if (imgBack != null) {
            imgBack.setOnClickListener(v -> finish());
        }

        if (imgHome != null) {
            imgHome.setOnClickListener(view -> {
                Intent intent = new Intent(ChooseSpeakingPronunciationTopicActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }

        lvTopics.setOnItemClickListener((parent, view, position, id) -> {
            Topics selectedTopic = topicsArrayList.get(position);
            String topicId = selectedTopic.getId();
            String topicDisplayName = selectedTopic.getTopicName();

            saveClickedTopicDisplayName(topicDisplayName);

            if (topicAdapter != null) {
                topicAdapter.setHighlightedTopicDisplayNames(clickedTopicDisplayNamesForPronunciation);
                topicAdapter.notifyDataSetChanged();
            }

            // TODO: Chuyển sang Activity thực hành Pronunciation (ví dụ: InternalSpeakingPronunciationTopic.class)
            Intent intent = new Intent(ChooseSpeakingPronunciationTopicActivity.this, InternalSpeakingPronunciationTopic.class);
             intent.putExtra("levelName", levelName);
             intent.putExtra("topicId", topicId);
             intent.putExtra("topicTitle", topicDisplayName);
             startActivity(intent);
        });
    }
}
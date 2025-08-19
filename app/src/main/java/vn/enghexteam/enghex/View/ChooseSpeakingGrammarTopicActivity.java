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

public class ChooseSpeakingGrammarTopicActivity extends AppCompatActivity {

    private ListView lvTopics;
    private ArrayList<Topics> topicsArrayList;
    private TopicAdapter topicAdapter;
    private String levelName;
    private ImageView imgBack, imgHome;
    private static final String ACTIVITY_TAG = "ChooseTopicSpeakingActivity";
    private final String CURRENT_SKILL_NAME = "Speaking";
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "TopicPrefs";
    private String clickedTopicsKey;
    private Set<String> clickedTopicDisplayNamesForSpeaking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_speaking_grammar);

        levelName = getIntent().getStringExtra("levelName");
        if (levelName == null || levelName.isEmpty()) {
            Toast.makeText(this, "Error: Can't identify Levels!", Toast.LENGTH_LONG).show();
            Log.e(ACTIVITY_TAG, "levelName is null or empty!");
            finish();
            return;
        }
        Log.d(ACTIVITY_TAG, "Level received: " + levelName + " for skill " + CURRENT_SKILL_NAME + " (Current time: " + Calendar.getInstance().getTime() + ")");

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        clickedTopicsKey = levelName + "_" + CURRENT_SKILL_NAME + "_clickedTopicDisplayNames";
        loadClickedTopicDisplayNamesForSpeaking();

        addControls();
        loadTopicsFromFirebase();
        addEvents();
    }

    private void loadClickedTopicDisplayNamesForSpeaking() {
        Set<String> loadedSet = sharedPreferences.getStringSet(clickedTopicsKey, null);
        if (loadedSet != null) {
            clickedTopicDisplayNamesForSpeaking = new HashSet<>(loadedSet);
        } else {
            clickedTopicDisplayNamesForSpeaking = new HashSet<>();
        }
        Log.d(ACTIVITY_TAG, "Loaded " + clickedTopicDisplayNamesForSpeaking.size() + " clicked topics (Speaking) from SharedPreferences.");
    }

    private void saveClickedTopicDisplayNameForSpeaking(String topicDisplayName) {
        if (clickedTopicDisplayNamesForSpeaking == null) {
            clickedTopicDisplayNamesForSpeaking = new HashSet<>();
        }
        boolean added = clickedTopicDisplayNamesForSpeaking.add(topicDisplayName);

        if (added) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putStringSet(clickedTopicsKey, clickedTopicDisplayNamesForSpeaking);
            editor.apply();
            Log.d(ACTIVITY_TAG, "Saved topic '" + topicDisplayName + "' (Speaking) to SharedPreferences.");
        }
    }

    public void removeClickedTopicHistory(String topicDisplayName) {
        if (clickedTopicDisplayNamesForSpeaking == null) return;

        boolean removed = clickedTopicDisplayNamesForSpeaking.remove(topicDisplayName);
        if (removed) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putStringSet(clickedTopicsKey, clickedTopicDisplayNamesForSpeaking);
            editor.apply();
            Log.d(ACTIVITY_TAG, "Topic history has been deleted '" + topicDisplayName + "' (Speaking) from SharedPreferences.");
            Toast.makeText(this, "Topic '" + topicDisplayName + "' history has been deleted.", Toast.LENGTH_SHORT).show();

            if (topicAdapter != null) {
                topicAdapter.setHighlightedTopicDisplayNames(clickedTopicDisplayNamesForSpeaking);
                topicAdapter.notifyDataSetChanged();
            }
        }
    }

    private void addControls() {
        lvTopics = findViewById(R.id.lvTopics);
        topicsArrayList = new ArrayList<>();

        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName, CURRENT_SKILL_NAME);
        topicAdapter.setHighlightedTopicDisplayNames(this.clickedTopicDisplayNamesForSpeaking);
        lvTopics.setAdapter(topicAdapter);

        imgBack = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);
    }

    private void loadTopicsFromFirebase() {
        DatabaseReference topicsPathRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child(CURRENT_SKILL_NAME)
                .child("Q&A")
                .child("Topics");

        Log.d(ACTIVITY_TAG, "Loading topics from: " + topicsPathRef.toString());

        topicsPathRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                topicsArrayList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot topicSnap : snapshot.getChildren()) {
                        String topicId = topicSnap.getKey();
                        String topicNameDisplay = topicSnap.child("topicName").getValue(String.class);

                        if (topicNameDisplay == null || topicNameDisplay.isEmpty()) {
                            topicNameDisplay = topicId;
                            Log.w(ACTIVITY_TAG, "Topic ID " + topicId + " missing topicName, using ID as display name.");
                        }

                        if (topicId != null) {
                            topicsArrayList.add(new Topics(topicId, topicNameDisplay));
                        }
                    }
                    Log.d(ACTIVITY_TAG, "Loaded " + topicsArrayList.size() + " " + CURRENT_SKILL_NAME + " topics.");
                } else {
                    Log.d(ACTIVITY_TAG, "No " + CURRENT_SKILL_NAME + " topics found for level: " + levelName); // levelName is not directly used in this path anymore, but log can remain for context
                    Toast.makeText(ChooseSpeakingGrammarTopicActivity.this, "No " + CURRENT_SKILL_NAME + " topics found.", Toast.LENGTH_SHORT).show();
                }
                if (topicAdapter != null) {
                    topicAdapter.setHighlightedTopicDisplayNames(clickedTopicDisplayNamesForSpeaking);
                    topicAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseSpeakingGrammarTopicActivity.this, "Failed to load: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(ACTIVITY_TAG, "Firebase Error: " + error.getMessage());
            }
        });
    }

    private void addEvents() {
        if (imgBack != null) {
            imgBack.setOnClickListener(v -> finish());
        }

        if (imgHome != null) {
            imgHome.setOnClickListener(view -> {
                Intent intent = new Intent(ChooseSpeakingGrammarTopicActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }

        lvTopics.setOnItemClickListener((parent, view, position, id) -> {
            Topics selectedTopic = topicsArrayList.get(position);
            String topicId = selectedTopic.getId();
            String topicDisplayName = selectedTopic.getTopicName();

            saveClickedTopicDisplayNameForSpeaking(topicDisplayName);

            if (topicAdapter != null) {
                topicAdapter.setHighlightedTopicDisplayNames(clickedTopicDisplayNamesForSpeaking);
                topicAdapter.notifyDataSetChanged();
            }

            Intent intent = new Intent(ChooseSpeakingGrammarTopicActivity.this, InternalSpeakingGrammarTopic.class);
            intent.putExtra("levelName", levelName);
            intent.putExtra("topicId", topicId);
            intent.putExtra("topicTitle", topicDisplayName);
            startActivity(intent);
        });
    }
}
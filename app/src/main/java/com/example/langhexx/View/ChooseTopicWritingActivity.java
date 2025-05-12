package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.langhexx.Controller.TopicAdapter;
import com.example.langhexx.Controller.WritingTopicAdapter;
import com.example.langhexx.Model.Topics; // Ensure this is your updated Topics model
import com.example.langhexx.Model.WritingTopics;
import com.example.langhexx.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class ChooseTopicWritingActivity extends AppCompatActivity {
    private ListView lvTopics;
    private ArrayList<WritingTopics> topicsArrayList;
    private WritingTopicAdapter topicAdapter;
    private String levelName;
    private ImageView imgBack, imgHome;
    private static final String ACTIVITY_TAG = "ChooseTopicWriting";
    private final String CURRENT_SKILL_NAME = "Writing";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_topic_writing);

        levelName = getIntent().getStringExtra("levelName");

        if (levelName == null || levelName.isEmpty()) {
            Toast.makeText(this, "Error: Level not identified.", Toast.LENGTH_LONG).show();
            Log.e(ACTIVITY_TAG, "levelName is null or empty!");
            finish();
            return;
        }
        Log.d(ACTIVITY_TAG, "Level received: " + levelName + " for skill " + CURRENT_SKILL_NAME);

        addControls();
        loadTopicsFromFirebase(levelName);
        addEvents();
    }

    private void addControls() {
        lvTopics = findViewById(R.id.lvTopics);
        topicsArrayList = new ArrayList<>();
        // Pass the updated Topics model to the adapter
        topicAdapter = new WritingTopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName, CURRENT_SKILL_NAME);
        lvTopics.setAdapter(topicAdapter);
        imgBack = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);
    }

    private void loadTopicsFromFirebase(String levelName) {
        DatabaseReference topicRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child(CURRENT_SKILL_NAME)
                .child("Topics");

        Log.d(ACTIVITY_TAG, "Loading topics from: " + topicRef.toString());

        topicRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                topicsArrayList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot topicNodeSnap : snapshot.getChildren()) { // Iterate through Topic IDs
                        String topicId = topicNodeSnap.getKey();
                        // Get topicName from the child "topicName" or "title"
                        String topicDisplayName = topicNodeSnap.child("topicName").getValue(String.class);
                        if (topicDisplayName == null) { // Fallback if "topicName" doesn't exist, try "title"
                            topicDisplayName = topicNodeSnap.child("title").getValue(String.class);
                        }

                        if (topicId != null && topicDisplayName != null) {
                            topicsArrayList.add(new WritingTopics(topicId, topicDisplayName));
                        } else {
                            Log.w(ACTIVITY_TAG, "Topic ID or Name is null for a child under " + topicRef.toString());
                        }
                    }
                    Log.d(ACTIVITY_TAG, "Loaded " + topicsArrayList.size() + " " + CURRENT_SKILL_NAME + " topics.");
                } else {
                    Log.d(ACTIVITY_TAG, "No " + CURRENT_SKILL_NAME + " topics found for level: " + levelName);
                    Toast.makeText(ChooseTopicWritingActivity.this, "No " + CURRENT_SKILL_NAME + " topics for this level.", Toast.LENGTH_SHORT).show();
                }
                topicAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseTopicWritingActivity.this, "Error loading " + CURRENT_SKILL_NAME + " topics: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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
                Intent intent = new Intent(ChooseTopicWritingActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            });
        }

        lvTopics.setOnItemClickListener((parent, view, position, id) -> {
            WritingTopics selectedTopic = topicsArrayList.get(position);
            Intent intent = new Intent(ChooseTopicWritingActivity.this, Writing_Topic_Exercise_Activity.class);
            intent.putExtra("levelName", levelName);
            intent.putExtra("TOPIC_ID", selectedTopic.getId()); // Pass Topic ID
            intent.putExtra("TOPIC_DISPLAY_NAME", selectedTopic.getTopicName()); // Pass Topic Display Name
            startActivity(intent);
        });
    }
}

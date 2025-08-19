package vn.enghexteam.enghex.View;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import vn.enghexteam.enghex.Controller.TopicAdapter;
import vn.enghexteam.enghex.Model.Topics;
import vn.enghexteam.enghex.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class ChooseTopicWritingActivity extends AppCompatActivity {
    private ListView lvTopics;
    private ArrayList<Topics> topicsArrayList;
    private TopicAdapter topicAdapter;
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
            Toast.makeText(this, "Error: Can't identify Levels!", Toast.LENGTH_LONG).show();
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
        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName, CURRENT_SKILL_NAME);
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
                        String topicDisplayName = topicNodeSnap.child("topicName").getValue(String.class);
                        if (topicDisplayName == null) { // Fallback if "topicName" doesn't exist, try "title"
                            topicDisplayName = topicNodeSnap.child("title").getValue(String.class);
                        }

                        if (topicId != null && topicDisplayName != null) {
                            topicsArrayList.add(new Topics(topicId, topicDisplayName));
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
                Toast.makeText(ChooseTopicWritingActivity.this, "Failed to load " + CURRENT_SKILL_NAME + " topics: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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
            Topics selectedTopic = topicsArrayList.get(position);
            Intent intent = new Intent(ChooseTopicWritingActivity.this, Writing_Topic_Exercise_Activity.class);
            intent.putExtra("levelName", levelName);
            intent.putExtra("TOPIC_ID", selectedTopic.getId());
            intent.putExtra("TOPIC_DISPLAY_NAME", selectedTopic.getTopicName());
            startActivity(intent);
        });
    }
}

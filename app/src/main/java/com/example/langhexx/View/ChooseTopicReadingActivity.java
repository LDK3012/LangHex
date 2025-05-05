package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter; // Keep this import
import android.widget.ListView;
import android.widget.Toast;

import com.example.langhexx.Controller.TopicAdapter; // Reusable Adapter
import com.example.langhexx.Model.Topics; // Reusable Model
import com.example.langhexx.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class ChooseTopicReadingActivity extends AppCompatActivity { // Renamed class
    private ListView lvTopics;
    private ArrayList<Topics> topicsArrayList;
    private TopicAdapter topicAdapter; // Reusable adapter
    private String levelName;
    private ImageView imgBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // *** Use a layout specific for Reading or reuse Listening's if identical ***
        setContentView(R.layout.activity_choose_topic_reading_acvitity); // Create this layout

        levelName = getIntent().getStringExtra("levelName");

        addControls();

        if (levelName != null) {
            loadTopicsFromFirebase(levelName);
        }

        addEvents();
    }

    private void addControls() {
        // *** Ensure these IDs exist in activity_choose_topic_reading.xml ***
        lvTopics = findViewById(R.id.lvTopics);
        imgBack = findViewById(R.id.imgBack);

        topicsArrayList = new ArrayList<>();
        // Use the same TopicAdapter and item layout (list_speaking_topic.xml or create a new one if needed)
        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList);
        lvTopics.setAdapter((ListAdapter) topicAdapter); // Cast is okay here for ListView
    }

    private void loadTopicsFromFirebase(String levelName) {
        DatabaseReference topicRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Reading") // *** Changed from "Listening" to "Reading" ***
                .child("Topics");

        topicRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                topicsArrayList.clear();
                for (DataSnapshot topicSnap : snapshot.getChildren()) {
                    String topicTitle = topicSnap.getKey();
                    if (topicTitle != null) {
                        topicsArrayList.add(new Topics(topicTitle)); // Reuse Topics model
                    }
                }
                topicAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseTopicReadingActivity.this, "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addEvents() {
        imgBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        lvTopics.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Topics selectedTopic = topicsArrayList.get(position);
                // *** Navigate to Reading_Topic_Exercise_Activity ***
                Intent intent = new Intent(ChooseTopicReadingActivity.this, Reading_Topic_Exercise_Activity.class);
                intent.putExtra("levelName", levelName);
                intent.putExtra("topicTitle", selectedTopic.getTitle());
                startActivity(intent);
            }
        });
    }
}
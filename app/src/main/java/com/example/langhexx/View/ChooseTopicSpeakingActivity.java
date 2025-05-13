package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.example.langhexx.Controller.TopicAdapter;
import com.example.langhexx.Model.Topics;
import com.example.langhexx.R;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class ChooseTopicSpeakingActivity extends AppCompatActivity {

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
        setContentView(R.layout.activity_choose_topic_speaking);

        levelName = getIntent().getStringExtra("levelName");
        if (levelName == null || levelName.isEmpty()) {
            Toast.makeText(this, "Error: Can't identify Levels!", Toast.LENGTH_LONG).show(); // Sửa lỗi chính tả
            Log.e(ACTIVITY_TAG, "levelName là null hoặc rỗng!");
            finish();
            return;
        }
        Log.d(ACTIVITY_TAG, "Level nhận được: " + levelName + " cho kỹ năng " + CURRENT_SKILL_NAME + " (Thời gian hiện tại: " + Calendar.getInstance().getTime() + ")");

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        clickedTopicsKey = levelName + "_" + CURRENT_SKILL_NAME + "_clickedTopicDisplayNames"; // Cập nhật key
        loadClickedTopicDisplayNamesForSpeaking();

        addControls();
        loadTopicsFromFirebase(levelName);
        addEvents();
    }

    private void loadClickedTopicDisplayNamesForSpeaking() {
        Set<String> loadedSet = sharedPreferences.getStringSet(clickedTopicsKey, null);
        if (loadedSet != null) {
            clickedTopicDisplayNamesForSpeaking = new HashSet<>(loadedSet);
        } else {
            clickedTopicDisplayNamesForSpeaking = new HashSet<>();
        }
        Log.d(ACTIVITY_TAG, "Đã tải " + clickedTopicDisplayNamesForSpeaking.size() + " chủ đề đã click (Speaking) từ SharedPreferences.");
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
            Log.d(ACTIVITY_TAG, "Đã lưu chủ đề '" + topicDisplayName + "' (Speaking) vào SharedPreferences.");
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
            Toast.makeText(this, "Topic '" + topicDisplayName + "'history has been deleted.", Toast.LENGTH_SHORT).show();

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

    private void loadTopicsFromFirebase(String levelName) {
        DatabaseReference topicsPathRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child(CURRENT_SKILL_NAME) // "Speaking"
                .child("Topics");

        Log.d(ACTIVITY_TAG, "Đang tải chủ đề từ: " + topicsPathRef.toString());

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
                            Log.w(ACTIVITY_TAG, "Topic ID " + topicId + " thiếu topicName, sử dụng ID làm tên hiển thị.");
                        }

                        if (topicId != null) {
                            topicsArrayList.add(new Topics(topicId, topicNameDisplay));
                        }
                    }
                    Log.d(ACTIVITY_TAG, "Đã tải " + topicsArrayList.size() + " chủ đề " + CURRENT_SKILL_NAME + ".");
                } else {
                    Log.d(ACTIVITY_TAG, "Không tìm thấy chủ đề " + CURRENT_SKILL_NAME + " nào cho level: " + levelName);
                    Toast.makeText(ChooseTopicSpeakingActivity.this, "No " + CURRENT_SKILL_NAME + " topics for this level.", Toast.LENGTH_SHORT).show();
                }
                if (topicAdapter != null) {
                    topicAdapter.setHighlightedTopicDisplayNames(clickedTopicDisplayNamesForSpeaking);
                    topicAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseTopicSpeakingActivity.this, "Failed to load: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(ACTIVITY_TAG, "Lỗi Firebase: " + error.getMessage());
            }
        });
    }

    private void addEvents() {
        if (imgBack != null) {
            imgBack.setOnClickListener(v -> finish());
        }

        if (imgHome != null) {
            imgHome.setOnClickListener(view -> {
                Intent intent = new Intent(ChooseTopicSpeakingActivity.this, MainActivity.class);
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

            Intent intent = new Intent(ChooseTopicSpeakingActivity.this, InternalSpeakingTopic.class);
            intent.putExtra("levelName", levelName);
            intent.putExtra("topicId", topicId);
            intent.putExtra("topicTitle", topicDisplayName);
            startActivity(intent);
        });
    }
}
package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
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
import java.util.Calendar; // Sử dụng Calendar cho thời gian
import java.util.HashSet;
import java.util.Set;

// KHÔNG implements interface nữa
public class ChooseTopicSpeakingActivity extends AppCompatActivity {

    // KHÔNG CÓ INNER INTERFACE Ở ĐÂY

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
    private Set<String> clickedTopicTitlesForSpeaking;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_topic_speaking);

        levelName = getIntent().getStringExtra("levelName");

        if (levelName == null || levelName.isEmpty()) {
            Toast.makeText(this, "Lỗi: Không xác định được Level.", Toast.LENGTH_LONG).show();
            Log.e(ACTIVITY_TAG, "levelName là null hoặc rỗng!");
            finish();
            return;
        }
        Log.d(ACTIVITY_TAG, "Level nhận được: " + levelName + " cho kỹ năng " + CURRENT_SKILL_NAME + " (Thời gian hiện tại: " + Calendar.getInstance().getTime() + ")");


        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        clickedTopicsKey = levelName + "_" + CURRENT_SKILL_NAME + "_clickedTopics";
        loadClickedTopicsForSpeaking();

        addControls();
        loadTopicsFromFirebase(levelName);
        addEvents();
    }

    private void loadClickedTopicsForSpeaking() {
        Set<String> loadedSet = sharedPreferences.getStringSet(clickedTopicsKey, null);
        if (loadedSet != null) {
            clickedTopicTitlesForSpeaking = new HashSet<>(loadedSet);
        } else {
            clickedTopicTitlesForSpeaking = new HashSet<>();
        }
        Log.d(ACTIVITY_TAG, "Đã tải " + clickedTopicTitlesForSpeaking.size() + " chủ đề đã click (Speaking) từ SharedPreferences.");
    }

    private void saveClickedTopicForSpeaking(String topicTitle) {
        if (clickedTopicTitlesForSpeaking == null) {
            clickedTopicTitlesForSpeaking = new HashSet<>();
        }
        boolean added = clickedTopicTitlesForSpeaking.add(topicTitle);

        if (added) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putStringSet(clickedTopicsKey, clickedTopicTitlesForSpeaking);
            editor.apply();
            Log.d(ACTIVITY_TAG, "Đã lưu chủ đề '" + topicTitle + "' (Speaking) vào SharedPreferences.");
        }
    }

    // Phương thức này phải là public để Adapter có thể gọi
    public void removeClickedTopicHistory(String topicTitle) {
        if (clickedTopicTitlesForSpeaking == null) return;

        boolean removed = clickedTopicTitlesForSpeaking.remove(topicTitle);
        if (removed) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putStringSet(clickedTopicsKey, clickedTopicTitlesForSpeaking);
            editor.apply();
            Log.d(ACTIVITY_TAG, "Đã xóa lịch sử chủ đề '" + topicTitle + "' (Speaking) khỏi SharedPreferences.");
            Toast.makeText(this, "Đã xóa lịch sử cho '" + topicTitle + "'.", Toast.LENGTH_SHORT).show();

            if (topicAdapter != null) {
                topicAdapter.setHighlightedTopicTitles(clickedTopicTitlesForSpeaking); // Vẫn cần cập nhật set trong adapter
                topicAdapter.notifyDataSetChanged();
            }
        }
    }

    private void addControls() {
        lvTopics = findViewById(R.id.lvTopics);
        topicsArrayList = new ArrayList<>();

        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName, CURRENT_SKILL_NAME);
        topicAdapter.setHighlightedTopicTitles(this.clickedTopicTitlesForSpeaking);
        // KHÔNG CÒN DÒNG NÀY: topicAdapter.setTopicItemInteractionListener(this);

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

        Log.d(ACTIVITY_TAG, "Đang tải chủ đề từ: " + topicRef.toString());

        topicRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                topicsArrayList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot topicSnap : snapshot.getChildren()) {
                        String topicTitle = topicSnap.getKey();
                        if (topicTitle != null) {
                            topicsArrayList.add(new Topics(topicTitle));
                        }
                    }
                } else {
                    Log.d(ACTIVITY_TAG, "Không tìm thấy chủ đề " + CURRENT_SKILL_NAME + " nào cho level: " + levelName);
                }
                if (topicAdapter != null) {
                    topicAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseTopicSpeakingActivity.this, "Lỗi tải chủ đề: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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
            String topicTitle = selectedTopic.getTitle();

            saveClickedTopicForSpeaking(topicTitle);

            if (topicAdapter != null) {
                topicAdapter.setHighlightedTopicTitles(clickedTopicTitlesForSpeaking);
                topicAdapter.notifyDataSetChanged();
            }

            Intent intent = new Intent(ChooseTopicSpeakingActivity.this, InternalSpeakingTopic.class);
            intent.putExtra("levelName", levelName);
            intent.putExtra("topicTitle", topicTitle);
            startActivity(intent);
        });
    }
}
package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences; // Thêm SharedPreferences
import android.graphics.Color; // Sẽ dùng để thay đổi màu nền nếu cần (hoặc dùng drawable)
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
import java.util.HashSet; // Thêm HashSet
import java.util.Set; // Thêm Set

public class ChooseTopicSpeakingActivity extends AppCompatActivity {
    private ListView lvTopics;
    private ArrayList<Topics> topicsArrayList;
    private TopicAdapter topicAdapter;
    private String levelName;
    private ImageView imgBack, imgHome;
    private static final String ACTIVITY_TAG = "ChooseTopicSpeaking"; // Thẻ log
    private final String CURRENT_SKILL_NAME = "Speaking"; // <-- Định nghĩa tên kỹ năng
    // SharedPreferences
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "TopicPrefs"; // Tên file SharedPreferences
    private String clickedTopicsKey; // Key để lưu các topic đã click, phụ thuộc vào level và skill
    private Set<String> clickedTopicTitles; // Set để lưu các title đã click

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_topic_speaking);

        levelName = getIntent().getStringExtra("levelName");

        if (levelName == null || levelName.isEmpty()) {
            Toast.makeText(this, "Lỗi: Không xác định được Level.", Toast.LENGTH_LONG).show();
            Log.e(ACTIVITY_TAG, "levelName is null or empty!");
            finish();
            return;
        }
        Log.d(ACTIVITY_TAG, "Level nhận được: " + levelName + " cho kỹ năng " + CURRENT_SKILL_NAME);

        // Khởi tạo SharedPreferences và key
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        clickedTopicsKey = levelName + "_" + CURRENT_SKILL_NAME + "_clickedTopics"; // Tạo key duy nhất
        loadClickedTopics(); // Tải các topic đã click từ SharedPreferences

        addControls(); // Gọi sau khi đã có levelName và đã tải clickedTopicTitles

        loadTopicsFromFirebase(levelName);
        addEvents();
    }

    private void loadClickedTopics() {
        // Lấy Set các topic đã click, nếu không có thì tạo mới HashSet rỗng
        clickedTopicTitles = sharedPreferences.getStringSet(clickedTopicsKey, new HashSet<>());
        Log.d(ACTIVITY_TAG, "Đã tải " + clickedTopicTitles.size() + " chủ đề đã click từ SharedPreferences cho key: " + clickedTopicsKey);
    }

    private void saveClickedTopic(String topicTitle) {
        clickedTopicTitles.add(topicTitle);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(clickedTopicsKey, clickedTopicTitles);
        editor.apply(); // Sử dụng apply() để lưu bất đồng bộ
        Log.d(ACTIVITY_TAG, "Đã lưu chủ đề '" + topicTitle + "' vào SharedPreferences. Tổng số: " + clickedTopicTitles.size());
    }

    private void addControls() {
        lvTopics = findViewById(R.id.lvTopics);
        topicsArrayList = new ArrayList<>();
        // TRUYỀN skillName "Speaking", levelName VÀ clickedTopicTitles VÀO CONSTRUCTOR CỦA TopicAdapter
        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName, CURRENT_SKILL_NAME, clickedTopicTitles);
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
                    Log.d(ACTIVITY_TAG, "Đã tải " + topicsArrayList.size() + " chủ đề " + CURRENT_SKILL_NAME + ".");
                } else {
                    Log.d(ACTIVITY_TAG, "Không tìm thấy chủ đề " + CURRENT_SKILL_NAME + " nào cho level: " + levelName);
                    Toast.makeText(ChooseTopicSpeakingActivity.this, "Không có chủ đề " + CURRENT_SKILL_NAME + " nào cho cấp độ này.", Toast.LENGTH_SHORT).show();
                }
                topicAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseTopicSpeakingActivity.this, "Lỗi tải chủ đề " + CURRENT_SKILL_NAME + ": " + error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(ACTIVITY_TAG, "Lỗi Firebase: " + error.getMessage());
            }
        });
    }

    private void addEvents() {
        if (imgBack != null) {
            imgBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        imgHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ChooseTopicSpeakingActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });

        lvTopics.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Topics selectedTopic = topicsArrayList.get(position);
                String topicTitle = selectedTopic.getTitle();
                saveClickedTopic(topicTitle);
                topicAdapter.notifyDataSetChanged();
                Intent intent = new Intent(ChooseTopicSpeakingActivity.this, InternalSpeakingTopic.class);
                intent.putExtra("levelName", levelName);
                intent.putExtra("topicTitle", topicTitle);
                startActivity(intent);
            }
        });
    }
}
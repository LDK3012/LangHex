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
import java.util.HashSet;
import java.util.Set;

public class ChooseTopicSpeakingActivity extends AppCompatActivity {
    private ListView lvTopics;
    private ArrayList<Topics> topicsArrayList;
    private TopicAdapter topicAdapter;
    private String levelName;
    private ImageView imgBack;
    private static final String ACTIVITY_TAG = "ChooseTopicSpeaking";
    private final String CURRENT_SKILL_NAME = "Speaking";

    // SharedPreferences
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "TopicPrefs";
    private String clickedTopicsKey;
    private Set<String> clickedTopicTitles; // Sẽ là một bản sao có thể chỉnh sửa

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

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        clickedTopicsKey = levelName + "_" + CURRENT_SKILL_NAME + "_clickedTopics";
        loadClickedTopics(); // Tải và đảm bảo clickedTopicTitles là bản sao có thể chỉnh sửa

        addControls();
        loadTopicsFromFirebase(levelName);
        addEvents();
    }

    private void loadClickedTopics() {
        // Lấy Set từ SharedPreferences
        Set<String> loadedSet = sharedPreferences.getStringSet(clickedTopicsKey, null);
        if (loadedSet != null) {
            // Quan trọng: Tạo một HashSet mới từ Set đã tải.
            // Điều này đảm bảo clickedTopicTitles là một bản sao có thể chỉnh sửa
            // và độc lập với instance mà SharedPreferences trả về.
            clickedTopicTitles = new HashSet<>(loadedSet);
        } else {
            // Nếu không có gì trong SharedPreferences cho key này, khởi tạo một Set rỗng mới.
            clickedTopicTitles = new HashSet<>();
        }
        Log.d(ACTIVITY_TAG, "Đã tải " + clickedTopicTitles.size() + " chủ đề đã click từ SharedPreferences cho key: " + clickedTopicsKey + ". Nội dung: " + clickedTopicTitles.toString());
    }

    private void saveClickedTopic(String topicTitle) {
        // clickedTopicTitles bây giờ là instance Set có thể chỉnh sửa mà Activity sở hữu.
        Log.d(ACTIVITY_TAG, "Trước khi thêm '" + topicTitle + "': clickedTopicTitles = " + clickedTopicTitles.toString());
        boolean added = clickedTopicTitles.add(topicTitle); // Thêm vào Set của chúng ta

        // Chỉ lưu nếu có sự thay đổi (topic thực sự được thêm mới)
        if (added) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            // SharedPreferences sẽ tạo bản sao của clickedTopicTitles để lưu trữ.
            editor.putStringSet(clickedTopicsKey, clickedTopicTitles);
            editor.apply(); // Sử dụng apply() để lưu bất đồng bộ
            Log.d(ACTIVITY_TAG, "Đã lưu chủ đề '" + topicTitle + "' vào SharedPreferences. Tổng số hiện tại: " + clickedTopicTitles.size() + ". Nội dung: " + clickedTopicTitles.toString());
        } else {
            Log.d(ACTIVITY_TAG, "Chủ đề '" + topicTitle + "' đã tồn tại trong set. Không lưu lại. Tổng số: " + clickedTopicTitles.size() + ". Nội dung: " + clickedTopicTitles.toString());
        }
    }

    private void addControls() {
        lvTopics = findViewById(R.id.lvTopics);
        topicsArrayList = new ArrayList<>();
        // clickedTopicTitles (đã được load và là bản sao an toàn) được truyền vào Adapter
        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName, CURRENT_SKILL_NAME, clickedTopicTitles);
        lvTopics.setAdapter(topicAdapter);
        imgBack = findViewById(R.id.imgBackward);
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
                topicAdapter.notifyDataSetChanged(); // Adapter sẽ sử dụng clickedTopicTitles mới nhất
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

        lvTopics.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Topics selectedTopic = topicsArrayList.get(position);
                String topicTitle = selectedTopic.getTitle();

                saveClickedTopic(topicTitle); // Lưu topic vừa click
                // topicAdapter đã có tham chiếu đến clickedTopicTitles của Activity,
                // và set đó vừa được cập nhật. notifyDataSetChanged() sẽ yêu cầu adapter vẽ lại.
                topicAdapter.notifyDataSetChanged();

                Intent intent = new Intent(ChooseTopicSpeakingActivity.this, InternalSpeakingTopic.class);
                intent.putExtra("levelName", levelName);
                intent.putExtra("topicTitle", topicTitle);
                startActivity(intent);
            }
        });
    }
}
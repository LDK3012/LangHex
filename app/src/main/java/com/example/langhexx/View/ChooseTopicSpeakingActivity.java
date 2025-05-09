package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log; // Thêm Log
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
// Bỏ ListAdapter nếu không dùng ép kiểu trực tiếp
import android.widget.ListView;
import android.widget.Toast;

import com.example.langhexx.Controller.TopicAdapter; // Đảm bảo bạn đang dùng TopicAdapter đã cập nhật
import com.example.langhexx.Model.Topics;
import com.example.langhexx.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class ChooseTopicSpeakingActivity extends AppCompatActivity {
    private ListView lvTopics;
    private ArrayList<Topics> topicsArrayList;
    private TopicAdapter topicAdapter;
    private String levelName;
    private ImageView imgBack;
    private static final String ACTIVITY_TAG = "ChooseTopicSpeaking"; // Thẻ log
    private final String CURRENT_SKILL_NAME = "Speaking"; // <-- Định nghĩa tên kỹ năng

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

        addControls(); // Gọi sau khi đã có levelName

        // loadTopicsFromFirebase nên được gọi sau khi adapter đã được khởi tạo
        loadTopicsFromFirebase(levelName);

        addEvents();
    }

    private void addControls() {
        lvTopics = findViewById(R.id.lvTopics);
        topicsArrayList = new ArrayList<>();
        // TRUYỀN skillName "Speaking" VÀO CONSTRUCTOR CỦA TopicAdapter
        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName, CURRENT_SKILL_NAME);
        lvTopics.setAdapter(topicAdapter); // Không cần ép kiểu (ListAdapter)
        imgBack = findViewById(R.id.imgBackward);
    }

    private void loadTopicsFromFirebase(String levelName) {
        // Sử dụng CURRENT_SKILL_NAME để đảm bảo đường dẫn chính xác
        DatabaseReference topicRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child(CURRENT_SKILL_NAME) // Đảm bảo đây là "Speaking"
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
                topicAdapter.notifyDataSetChanged(); // Đảm bảo cập nhật giao diện sau khi có dữ liệu
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseTopicSpeakingActivity.this, "Lỗi tải chủ đề " + CURRENT_SKILL_NAME + ": " + error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(ACTIVITY_TAG, "Lỗi Firebase: " + error.getMessage());
            }
        });
    }

    private void addEvents() {
        if (imgBack != null) { // Kiểm tra null cho imgBack
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
                // Điều hướng đến InternalSpeakingTopic (đã đúng trong code bạn cung cấp)
                Intent intent = new Intent(ChooseTopicSpeakingActivity.this, InternalSpeakingTopic.class);
                intent.putExtra("levelName", levelName);
                intent.putExtra("topicTitle", selectedTopic.getTitle());
                startActivity(intent);
            }
        });
    }
}

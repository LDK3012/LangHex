package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.example.langhexx.Controller.TopicAdapter; // Sử dụng lại TopicAdapter
import com.example.langhexx.Model.Topics;           // Sử dụng lại Topics model
import com.example.langhexx.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class ChooseTopicReadingActivity extends AppCompatActivity {
    private ListView lvTopics;
    private ArrayList<Topics> topicsArrayList;
    private TopicAdapter topicAdapter;
    private String levelName;
    private ImageView imgBack;
    private static final String ACTIVITY_TAG = "ChooseTopicReading"; // Thẻ log cho Activity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Đảm bảo bạn có layout activity_choose_topic_reading.xml
        setContentView(R.layout.activity_choose_topic_reading_acvitity);

        levelName = getIntent().getStringExtra("levelName");

        if (levelName == null || levelName.isEmpty()) {
            Toast.makeText(this, "Lỗi: Không xác định được Level.", Toast.LENGTH_LONG).show();
            Log.e(ACTIVITY_TAG, "levelName is null or empty!");
            finish();
            return;
        }
        Log.d(ACTIVITY_TAG, "Level nhận được: " + levelName);

        addControls();

        loadTopicsFromFirebase(levelName);

        addEvents();
    }

    private void addControls() {
        // Đảm bảo ID lvTopics và imgBack tồn tại trong activity_choose_topic_reading.xml
        lvTopics = findViewById(R.id.lvTopics);
        topicsArrayList = new ArrayList<>();
        // Sử dụng lại TopicAdapter, truyền levelName
        // TopicAdapter sẽ sử dụng R.layout.list_speaking_topic (hoặc bạn có thể tạo layout riêng nếu muốn)
        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName);
        lvTopics.setAdapter(topicAdapter);
        imgBack = findViewById(R.id.imgBack);
    }

    private void loadTopicsFromFirebase(String levelName) {
        // THAY ĐỔI CHÍNH: Trỏ đến "Reading" thay vì "Listening"
        DatabaseReference topicRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Reading") // <--- THAY ĐỔI Ở ĐÂY
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
                    Log.d(ACTIVITY_TAG, "Đã tải " + topicsArrayList.size() + " chủ đề đọc.");
                } else {
                    Log.d(ACTIVITY_TAG, "Không tìm thấy chủ đề đọc nào cho level: " + levelName);
                    Toast.makeText(ChooseTopicReadingActivity.this, "Không có chủ đề đọc nào cho cấp độ này.", Toast.LENGTH_SHORT).show();
                }
                topicAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseTopicReadingActivity.this, "Lỗi tải chủ đề đọc: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(ACTIVITY_TAG, "Lỗi Firebase: " + error.getMessage());
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
                // THAY ĐỔI CHÍNH: Chuyển đến Activity hiển thị bài tập đọc
                // Ví dụ: Reading_Topic_Exercise_Activity.class hoặc InternalReadingTopic.class
                Intent intent = new Intent(ChooseTopicReadingActivity.this, Reading_Topic_Exercise_Activity.class); // <--- THAY ĐỔI Activity ĐÍCH
                intent.putExtra("levelName", levelName);
                intent.putExtra("topicTitle", selectedTopic.getTitle());
                startActivity(intent);
            }
        });
    }
}
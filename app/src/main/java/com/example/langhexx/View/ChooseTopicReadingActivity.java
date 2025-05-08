package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log; // Thêm Log để kiểm tra
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

public class ChooseTopicReadingActivity extends AppCompatActivity {
    private ListView lvTopics;
    private ArrayList<Topics> topicsArrayList;
    private TopicAdapter topicAdapter; // Sẽ sử dụng constructor không có skillName
    private String levelName;
    private ImageView imgBack;
    private static final String ACTIVITY_TAG = "ChooseTopicReading"; // Thẻ log cho Activity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Đảm bảo tên layout này chính xác trong thư mục res/layout của bạn
        setContentView(R.layout.activity_choose_topic_reading_acvitity); // Giả sử tên layout là activity_choose_topic_reading.xml

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
        lvTopics = findViewById(R.id.lvTopics); // Đảm bảo ID này tồn tại trong layout
        topicsArrayList = new ArrayList<>();
        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName);
        lvTopics.setAdapter(topicAdapter); // Không cần ép kiểu (ListAdapter)
        imgBack = findViewById(R.id.imgBack); // Đảm bảo ID này tồn tại trong layout
    }

    private void loadTopicsFromFirebase(String levelName) {
        // Đường dẫn Firebase cho Reading topics
        DatabaseReference topicRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Reading") // <--- Thay đổi để trỏ đến "Reading"
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
                    Log.d(ACTIVITY_TAG, "Đã tải " + topicsArrayList.size() + " chủ đề Reading.");
                } else {
                    Log.d(ACTIVITY_TAG, "Không tìm thấy chủ đề Reading nào cho level: " + levelName);
                    Toast.makeText(ChooseTopicReadingActivity.this, "Không có chủ đề Reading nào cho cấp độ này.", Toast.LENGTH_SHORT).show();
                }
                topicAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseTopicReadingActivity.this, "Lỗi tải chủ đề Reading: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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
                // Điều hướng đến Activity cho bài tập Reading
                // Thay thế Reading_Topic_Exercise_Activity.class bằng tên Activity thực tế của bạn
                Intent intent = new Intent(ChooseTopicReadingActivity.this, Reading_Topic_Exercise_Activity.class); // <--- THAY ĐỔI Activity ĐÍCH
                intent.putExtra("levelName", levelName);
                intent.putExtra("topicTitle", selectedTopic.getTitle());
                startActivity(intent);
            }
        });
    }
}
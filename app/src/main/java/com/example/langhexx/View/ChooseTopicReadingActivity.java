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

import com.example.langhexx.Controller.TopicAdapter; // Đảm bảo bạn đang dùng TopicAdapter đã cập nhật
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
    private TopicAdapter topicAdapter;
    private String levelName;
    private ImageView imgBack;
    private static final String ACTIVITY_TAG = "ChooseTopicReading"; // Thẻ log cho Activity
    private final String CURRENT_SKILL_NAME = "Reading"; // <-- Định nghĩa tên kỹ năng

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Đảm bảo tên layout này chính xác trong thư mục res/layout của bạn.
        // Trong code bạn gửi là "activity_choose_topic_reading_acvitity",
        // nếu đó là tên đúng thì giữ nguyên, nếu không thì sửa thành tên đúng,
        // ví dụ: R.layout.activity_choose_topic_reading
        setContentView(R.layout.activity_choose_topic_reading_acvitity); // Sử dụng tên layout chuẩn, hoặc sửa lại nếu tên file của bạn khác

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
        lvTopics = findViewById(R.id.lvTopics); // Đảm bảo ID này tồn tại trong layout
        topicsArrayList = new ArrayList<>();
        // TRUYỀN skillName "Reading" VÀO CONSTRUCTOR CỦA TopicAdapter
        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList, levelName, CURRENT_SKILL_NAME);
        lvTopics.setAdapter(topicAdapter); // Không cần ép kiểu (ListAdapter)
        imgBack = findViewById(R.id.imgBackward); // Đảm bảo ID này tồn tại trong layout
    }

    private void loadTopicsFromFirebase(String levelName) {
        // Sử dụng CURRENT_SKILL_NAME để đảm bảo đường dẫn chính xác
        DatabaseReference topicRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child(CURRENT_SKILL_NAME) // Đảm bảo đây là "Reading"
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
                    Toast.makeText(ChooseTopicReadingActivity.this, "Không có chủ đề " + CURRENT_SKILL_NAME + " nào cho cấp độ này.", Toast.LENGTH_SHORT).show();
                }
                topicAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseTopicReadingActivity.this, "Lỗi tải chủ đề " + CURRENT_SKILL_NAME + ": " + error.getMessage(), Toast.LENGTH_SHORT).show();
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
                Intent intent = new Intent(ChooseTopicReadingActivity.this, Reading_Topic_Exercise_Activity.class); // <--- THAY ĐỔI Activity ĐÍCH nếu cần
                intent.putExtra("levelName", levelName);
                intent.putExtra("topicTitle", selectedTopic.getTitle());
                startActivity(intent);
            }
        });
    }
}

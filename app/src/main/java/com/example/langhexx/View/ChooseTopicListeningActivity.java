package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter;
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

public class ChooseTopicListeningActivity extends AppCompatActivity {
    private ListView lvTopics;
    private ArrayList<Topics> topicsArrayList;
    private TopicAdapter topicAdapter;
    private String levelName;
    private ImageView imgBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_topic_listening); // Sử dụng layout cho Listening

        levelName = getIntent().getStringExtra("levelName");

        addControls();

        if (levelName != null) {
            loadTopicsFromFirebase(levelName);
        }

        addEvents();
    }

    private void addControls() {
        lvTopics = findViewById(R.id.lvTopics); // Đảm bảo ID này tồn tại trong activity_choose_topic_listening.xml
        topicsArrayList = new ArrayList<>();
        topicAdapter = new TopicAdapter(this, R.layout.list_speaking_topic, topicsArrayList); // Có thể tạo layout riêng cho Listening nếu cần
        lvTopics.setAdapter((ListAdapter) topicAdapter);
        imgBack = findViewById(R.id.imgBack); // Đảm bảo ID này tồn tại trong activity_choose_topic_listening.xml
    }

    private void loadTopicsFromFirebase(String levelName) {
        DatabaseReference topicRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Listening") // Thay "Speaking" bằng "Listening"
                .child("Topics");

        topicRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                topicsArrayList.clear();
                for (DataSnapshot topicSnap : snapshot.getChildren()) {
                    String topicTitle = topicSnap.getKey();
                    if (topicTitle != null) {
                        topicsArrayList.add(new Topics(topicTitle));
                    }
                }
                topicAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChooseTopicListeningActivity.this, "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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
                Intent intent = new Intent(ChooseTopicListeningActivity.this, Listening_Topic_Exercise_Activity.class); // Tạo Activity InternalListeningTopic
                intent.putExtra("levelName", levelName);
                intent.putExtra("topicTitle", selectedTopic.getTitle());
                startActivity(intent);
            }
        });
    }
}
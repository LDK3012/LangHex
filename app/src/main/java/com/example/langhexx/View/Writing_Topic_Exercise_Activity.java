package com.example.langhexx.View;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.langhexx.Controller.WritingExerciseAdapter;
import com.example.langhexx.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class Writing_Topic_Exercise_Activity extends AppCompatActivity {
    private static final String TAG = "WritingExerciseActivity"; // Đổi TAG
    private ImageView btnClose;
    private TextView tvTitle;
    private RecyclerView rvExercises;
    private WritingExerciseAdapter exerciseAdapter; // Đổi kiểu Adapter
    private List<String> exerciseTitlesList;
    private String levelName;
    private String topicTitle;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_writing_topic_exercise);
        //
        levelName = getIntent().getStringExtra("levelName");
        topicTitle = getIntent().getStringExtra("topicTitle");
        //
        addControls();
        if (levelName != null && topicTitle != null) {
            loadExercisesFromFirebase(levelName, topicTitle);
        } else {
            Toast.makeText(this, "Fail!", Toast.LENGTH_LONG).show();
        }
        addEvents();
    }

    private void addControls() {
        btnClose = findViewById(R.id.btnClose);
        tvTitle = findViewById(R.id.tvTitle);
        if (topicTitle != null) {
            tvTitle.setText(topicTitle);
        }

        rvExercises = findViewById(R.id.rvExercises);
        rvExercises.setLayoutManager(new LinearLayoutManager(this));
        exerciseTitlesList = new ArrayList<>();
        // Khởi tạo WritingExerciseAdapter
        exerciseAdapter = new WritingExerciseAdapter(this, exerciseTitlesList, levelName, topicTitle);
        rvExercises.setAdapter(exerciseAdapter);
    }

    private void loadExercisesFromFirebase(String levelName, String topicTitle) {
        DatabaseReference exercisesRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Writing") // Thay đổi sang "Writing"
                .child("Topics")
                .child(topicTitle)
                .child("Exercises");

        exercisesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                exerciseTitlesList.clear();
                for (DataSnapshot exerciseGroupSnap : snapshot.getChildren()) {
                    String exerciseTitle = exerciseGroupSnap.getKey(); // Lấy tên bài tập (key)
                    if (exerciseTitle != null) {
                        exerciseTitlesList.add(exerciseTitle);
                    }
                }
                if (exerciseAdapter != null) {
                    exerciseAdapter.notifyDataSetChanged();
                }
                if (exerciseTitlesList.isEmpty()) {
                    Toast.makeText(Writing_Topic_Exercise_Activity.this, "Fail!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Writing_Topic_Exercise_Activity.this, "Fail!" + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void addEvents() {
        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish(); // Đóng Activity hiện tại
                }
            });
        }
    }
}
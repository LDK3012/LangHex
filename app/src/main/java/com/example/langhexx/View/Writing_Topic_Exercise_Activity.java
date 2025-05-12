package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.langhexx.Controller.WritingExerciseAdapter;
import com.example.langhexx.Model.WritingExercise;
import com.example.langhexx.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class Writing_Topic_Exercise_Activity extends AppCompatActivity {
    private static final String TAG = "WritingExerciseActivity";
    private ImageView imgClose, imgHome;
    private TextView tvScreenTitle;
    private RecyclerView rvExercises;
    private WritingExerciseAdapter exerciseAdapter;
    private List<WritingExercise> exerciseList;

    private String levelName;
    private String topicId;
    private String topicDisplayName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_writing_topic_exercise);

        levelName = getIntent().getStringExtra("levelName");
        topicId = getIntent().getStringExtra("TOPIC_ID");
        topicDisplayName = getIntent().getStringExtra("TOPIC_DISPLAY_NAME");

        addControls();

        if (levelName != null && topicId != null) {
            if (topicDisplayName != null) {
                tvScreenTitle.setText(topicDisplayName);
            } else {
                tvScreenTitle.setText("Exercises");
            }
            loadExercisesFromFirebase(levelName, topicId);
        } else {
            Toast.makeText(this, "Error: Level or Topic ID missing!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "LevelName or TopicID is null. Level: " + levelName + ", TopicID: " + topicId);
            finish();
        }
        addEvents();
    }

    private void addControls() {
        imgClose = findViewById(R.id.imgBackward);
        tvScreenTitle = findViewById(R.id.tvScreenTitle);
        imgHome = findViewById(R.id.imgHome);
        rvExercises = findViewById(R.id.rvExercises);
        rvExercises.setLayoutManager(new LinearLayoutManager(this));
        exerciseList = new ArrayList<>();
        exerciseAdapter = new WritingExerciseAdapter(this, exerciseList, levelName, topicId, topicDisplayName);
        rvExercises.setAdapter(exerciseAdapter);
    }

    private void loadExercisesFromFirebase(String levelName, String currentTopicId) {
        DatabaseReference exercisesRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Writing")
                .child("Topics")
                .child(currentTopicId)
                .child("Exercises");

        Log.d(TAG, "Loading exercises from: " + exercisesRef.toString());

        exercisesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                exerciseList.clear();
                if (snapshot.exists()){
                    for (DataSnapshot exerciseNodeSnap : snapshot.getChildren()) {
                        String exerciseId = exerciseNodeSnap.getKey();
                        String exerciseDisplayTitle = exerciseNodeSnap.child("title").getValue(String.class);
                        if (exerciseId != null && exerciseDisplayTitle != null) {
                            exerciseList.add(new WritingExercise(exerciseId, exerciseDisplayTitle, null));
                        } else {
                            Log.w(TAG, "Exercise ID or Title is null for a child under " + exercisesRef.toString());
                        }
                    }
                } else {
                    Log.d(TAG, "No exercises found for topic ID: " + currentTopicId);
                    Toast.makeText(Writing_Topic_Exercise_Activity.this, "No exercises found for this topic.", Toast.LENGTH_SHORT).show();
                }

                if (exerciseAdapter != null) {
                    exerciseAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Writing_Topic_Exercise_Activity.this, "Failed to load exercises: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Firebase error loading exercises: " + error.getMessage());
            }
        });
    }

    private void addEvents() {
        imgClose.setOnClickListener(v -> finish());

        imgHome.setOnClickListener(view -> {
            Intent intent = new Intent(Writing_Topic_Exercise_Activity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}

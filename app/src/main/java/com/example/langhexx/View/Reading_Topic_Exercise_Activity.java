package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

// *** Import the new ReadingExerciseAdapter ***
import com.example.langhexx.Controller.ReadingExerciseAdapter;
import com.example.langhexx.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

// *** Renamed class ***
public class Reading_Topic_Exercise_Activity extends AppCompatActivity {

    private static final String TAG = "ReadingExerciseActivity"; // Renamed TAG
    private ImageView imgClose, imgHome;
    private TextView tvTitle;
    private RecyclerView rvExercises;
    private ReadingExerciseAdapter exerciseAdapter; // Use ReadingExerciseAdapter
    private List<String> exerciseTitlesList;
    private String levelName;
    private String topicTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // *** Use a layout specific for Reading or reuse Listening's if identical ***
        setContentView(R.layout.activity_reading_topic_exercise); // Create this layout

        levelName = getIntent().getStringExtra("levelName");
        topicTitle = getIntent().getStringExtra("topicTitle");

        addControls();
        if (levelName != null && topicTitle != null) {
            loadExercisesFromFirebase(levelName, topicTitle);
        }
        addEvents();
    }

    private void addControls() {
        // *** Ensure these IDs exist in activity_reading_topic_exercise.xml ***
        imgClose = findViewById(R.id.imgBackward);
        tvTitle = findViewById(R.id.tvScreenTitle);
        rvExercises = findViewById(R.id.rvExercises);
        imgHome = findViewById(R.id.imgHome);
        tvTitle.setText(topicTitle); // Set topic title
        rvExercises.setLayoutManager(new LinearLayoutManager(this));
        exerciseTitlesList = new ArrayList<>();
        // *** Instantiate ReadingExerciseAdapter ***
        exerciseAdapter = new ReadingExerciseAdapter(this, exerciseTitlesList, levelName, topicTitle);
        rvExercises.setAdapter(exerciseAdapter);
    }

    private void loadExercisesFromFirebase(String levelName, String topicTitle) {
        DatabaseReference exercisesRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Reading") // *** Changed from "Listening" to "Reading" ***
                .child("Topics")
                .child(topicTitle)
                .child("Exercises");

        Log.d(TAG, "Firebase query path for exercises: " + exercisesRef.toString());

        exercisesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                exerciseTitlesList.clear();
                for (DataSnapshot exerciseGroupSnap : snapshot.getChildren()) {
                    String exerciseTitle = exerciseGroupSnap.getKey();
                    if (exerciseTitle != null) {
                        exerciseTitlesList.add(exerciseTitle);
                    }
                }
                exerciseAdapter.notifyDataSetChanged(); // Update RecyclerView
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load exercises: " + error.getMessage());
                Toast.makeText(Reading_Topic_Exercise_Activity.this, "Lỗi khi tải bài tập: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addEvents() {
        imgClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        imgHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Reading_Topic_Exercise_Activity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}
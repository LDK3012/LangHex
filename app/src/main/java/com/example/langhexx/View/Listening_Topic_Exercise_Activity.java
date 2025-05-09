package com.example.langhexx.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.langhexx.Controller.ListeningExerciseAdapter;
import com.example.langhexx.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class Listening_Topic_Exercise_Activity extends AppCompatActivity {

    private static final String TAG = "ListeningExerciseActivity";
    private ImageView btnClose;
    private TextView tvTitle;
    private RecyclerView rvExercises;
    private ListeningExerciseAdapter exerciseAdapter;
    private List<String> exerciseTitlesList;
    private String levelName;
    private String topicTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening_topic_exercise);

        levelName = getIntent().getStringExtra("levelName");
        topicTitle = getIntent().getStringExtra("topicTitle");

        addControls();
        loadExercisesFromFirebase(levelName, topicTitle);
        addEvents();
    }

    private void addControls() {
        btnClose = findViewById(R.id.imgBackward);
        tvTitle = findViewById(R.id.tvScreenTitle);
        tvTitle.setText(topicTitle);
        rvExercises = findViewById(R.id.rvExercises);
        rvExercises.setLayoutManager(new LinearLayoutManager(this));
        exerciseTitlesList = new ArrayList<>();
        exerciseAdapter = new ListeningExerciseAdapter(this, exerciseTitlesList, levelName, topicTitle);
        rvExercises.setAdapter(exerciseAdapter);
    }

    private void loadExercisesFromFirebase(String levelName, String topicTitle) {
        DatabaseReference exercisesRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Listening")
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
                exerciseAdapter.notifyDataSetChanged(); // Cập nhật RecyclerView
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load exercises: " + error.getMessage());
                Toast.makeText(Listening_Topic_Exercise_Activity.this, "Lỗi khi tải bài tập: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addEvents() {
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
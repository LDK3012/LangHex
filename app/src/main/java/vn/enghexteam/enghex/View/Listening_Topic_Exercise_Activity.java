package vn.enghexteam.enghex.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import vn.enghexteam.enghex.Controller.ListeningExerciseAdapter;
import vn.enghexteam.enghex.Model.Exercise;
import vn.enghexteam.enghex.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class Listening_Topic_Exercise_Activity extends AppCompatActivity {
    private static final String TAG = "ListeningExerciseAct"; // Rút ngắn TAG
    private ImageView imgClose, imgHome;
    private TextView tvTopicDisplayTitle;
    private RecyclerView rvExercises;
    private ListeningExerciseAdapter exerciseAdapter;
    private List<Exercise> exerciseList;
    private String levelName;
    private String topicId;
    private String topicDisplayTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening_topic_exercise);

        levelName = getIntent().getStringExtra("levelName");
        topicId = getIntent().getStringExtra("topicId");
        topicDisplayTitle = getIntent().getStringExtra("topicTitle");

        if (levelName == null || topicId == null || topicDisplayTitle == null) {
            Toast.makeText(this, "Lỗi: Thông tin level hoặc topic không đủ.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Missing levelName, topicId or topicDisplayTitle from Intent.");
            finish();
            return;
        }

        addControls();
        loadExercisesFromFirebase(levelName, topicId);
        addEvents();
    }

    private void addControls() {
        imgClose = findViewById(R.id.imgBackward);
        tvTopicDisplayTitle = findViewById(R.id.tvScreenTitle);
        imgHome = findViewById(R.id.imgHome);
        tvTopicDisplayTitle.setText(topicDisplayTitle);
        rvExercises = findViewById(R.id.rvExercises);
        rvExercises.setLayoutManager(new LinearLayoutManager(this));
        exerciseList = new ArrayList<>();
        exerciseAdapter = new ListeningExerciseAdapter(this, exerciseList, levelName, topicId, topicDisplayTitle);
        rvExercises.setAdapter(exerciseAdapter);
    }

    private void loadExercisesFromFirebase(String levelName, String currentTopicId) {
        DatabaseReference exercisesPathRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("Lessons")
                .child("Levels")
                .child(levelName)
                .child("Listening") // Skill name
                .child("Topics")
                .child(currentTopicId) // Sử dụng Topic ID
                .child("Exercises");

        Log.d(TAG, "Firebase query path for exercises: " + exercisesPathRef.toString());

        exercisesPathRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                exerciseList.clear();
                if (snapshot.exists()){
                    for (DataSnapshot exerciseSnap : snapshot.getChildren()) {
                        String exerciseId = exerciseSnap.getKey();
                        String exerciseDisplayTitle = exerciseSnap.child("title").getValue(String.class);

                        if (exerciseDisplayTitle == null || exerciseDisplayTitle.isEmpty()) {
                            exerciseDisplayTitle = exerciseId; // Fallback
                            Log.w(TAG, "Exercise ID " + exerciseId + " thiếu title, sử dụng ID.");
                        }
                        if (exerciseId != null) {
                            exerciseList.add(new Exercise(exerciseId, exerciseDisplayTitle));
                        }
                    }
                    Log.d(TAG, "Đã tải " + exerciseList.size() + " exercises cho topic ID: " + currentTopicId);
                } else {
                    Log.d(TAG, "Không tìm thấy exercises cho topic ID: " + currentTopicId);
                    Toast.makeText(Listening_Topic_Exercise_Activity.this, "No exercises for this topic.", Toast.LENGTH_SHORT).show();
                }
                exerciseAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load exercises: " + error.getMessage());
                Toast.makeText(Listening_Topic_Exercise_Activity.this, "Exercise load failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addEvents() {
        if (imgClose != null) {
            imgClose.setOnClickListener(v -> finish());
        }

        if (imgHome != null) {
            imgHome.setOnClickListener(view -> {
                Intent intent = new Intent(Listening_Topic_Exercise_Activity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }
    }
}
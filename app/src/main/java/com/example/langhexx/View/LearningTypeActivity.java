package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.langhexx.R;

public class LearningTypeActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private CardView cardListening, cardSpeaking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learning_type);
        addControls();
        addEvents();
    }

    private void addControls() {
        btnBack = findViewById(R.id.btnBack);
        cardListening = findViewById(R.id.cardListening);
        cardSpeaking = findViewById(R.id.cardSpeaking);
    }

    private void addEvents() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        cardListening.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LearningTypeActivity.this, ListeningPracticeActivity.class);
                startActivity(intent);
            }
        });
        cardSpeaking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LearningTypeActivity.this, SpeakingPracticeActivity.class);
                startActivity(intent);
            }
        });
    }
}
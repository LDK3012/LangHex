package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.langhexx.R;

public class LearningTypeActivity extends AppCompatActivity {

    private ImageView imgClose, imgHome;
    private CardView cardListening, cardSpeaking , cardReading, cardWriting;
    private String levelName ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learning_type);
        //
        levelName = getIntent().getStringExtra("levelName") ;
        //
        addControls();
        addEvents();
    }

    private void addControls() {
        imgClose = findViewById(R.id.imgBackward);
        cardListening = findViewById(R.id.cardListening);
        cardSpeaking = findViewById(R.id.cardSpeaking);
        cardReading = findViewById(R.id.cardReading) ;
        cardWriting= findViewById(R.id.cardWriting);
        imgHome = findViewById(R.id.imgHome);
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
                Intent intent = new Intent(LearningTypeActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
        cardListening.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LearningTypeActivity.this, ChooseTopicListeningActivity.class);
                intent.putExtra("levelName",levelName) ;
                startActivity(intent);
            }
        });
        cardSpeaking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LearningTypeActivity.this, ChooseTopicSpeakingActivity.class);
                intent.putExtra("levelName",levelName) ;
                startActivity(intent);
            }
        });

        cardReading.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LearningTypeActivity.this, ChooseTopicReadingActivity.class); // Navigate to ChooseTopicReadingActivity
                intent.putExtra("levelName", levelName);
                startActivity(intent);
            }
        });
        //
        cardWriting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LearningTypeActivity.this, ChooseTopicWritingActivity.class); // Navigate to ChooseTopicReadingActivity
                intent.putExtra("levelName", levelName);
                startActivity(intent);
            }
        });
    }
}
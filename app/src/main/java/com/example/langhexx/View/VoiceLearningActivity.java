package com.example.langhexx.View;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.langhexx.R;

public class VoiceLearningActivity extends AppCompatActivity {

    Button btnBackward, btnForward;
    ImageButton imgPlayAudio, imgDelete, imgRecord;
    ImageView imgClose, imgHome;
    TextView txtTitle, txtScript, txtCounter, txtTime;
    SeekBar sbrAudio;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_learning);
        addControls();
        addEvents();
    }

    private void addControls(){
        btnBackward = findViewById(R.id.btnBackward);
        btnForward = findViewById(R.id.btnForward);
        imgPlayAudio = findViewById(R.id.imgButtonPlayAudio);
        imgDelete = findViewById(R.id.imgButtonDelete);
        imgRecord = findViewById(R.id.imgButtonRecord);
        imgClose = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);
        txtTitle = findViewById(R.id.txtTitle);
        txtScript = findViewById(R.id.txtScript);
        txtCounter = findViewById(R.id.txtCounter);
        txtTime = findViewById(R.id.txtTime);
        sbrAudio = findViewById(R.id.sbrAudio);
    }

    private void addEvents() {
        if (imgClose != null) {
            imgClose.setOnClickListener(v -> finish());
        }

        if (imgHome != null) {
            imgHome.setOnClickListener(view -> {
                Intent intent = new Intent(VoiceLearningActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }
    }
}
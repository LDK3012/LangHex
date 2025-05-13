package com.example.langhexx.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.example.langhexx.R;

public class ChooseSpeakingLearningMethod extends AppCompatActivity {

    ImageView imgClose, imgHome;
    CardView crdGrammar, crdVoice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_speaking_learning_method);
        addControls();
    }

    private void addControls(){
        imgClose = findViewById(R.id.imgBackward);
        imgHome = findViewById(R.id.imgHome);
        crdGrammar = findViewById(R.id.crdGrammar);
        crdVoice = findViewById(R.id.crdVoice);
    }

    private void addEvents() {
        if (imgClose != null) {
            imgClose.setOnClickListener(v -> finish());
        }

        if (imgHome != null) {
            imgHome.setOnClickListener(view -> {
                Intent intent = new Intent(ChooseSpeakingLearningMethod.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }

        crdGrammar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ChooseSpeakingLearningMethod.this, ChooseTopicSpeakingActivity.class);
                startActivity(intent);
                finish();
            }
        });

        crdVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ChooseSpeakingLearningMethod.this, ChooseTopicSpeakingActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}
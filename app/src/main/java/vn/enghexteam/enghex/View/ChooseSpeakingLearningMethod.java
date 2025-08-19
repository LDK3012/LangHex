package vn.enghexteam.enghex.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import vn.enghexteam.enghex.R;

public class ChooseSpeakingLearningMethod extends AppCompatActivity {

    ImageView imgClose, imgHome;
    CardView crdGrammar, crdVoice;
    private String levelName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_speaking_learning_method);
        levelName = getIntent().getStringExtra("levelName");
        if (levelName == null || levelName.isEmpty()) {
            Toast.makeText(this, "Error: Level name not received.", Toast.LENGTH_LONG).show();
            Log.e("ChooseSpeakingMethod", "levelName is null or empty in onCreate.");
            finish();
            return;
        }
        addControls();
        addEvents();
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
        if (crdGrammar != null) {
            crdGrammar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(ChooseSpeakingLearningMethod.this, ChooseSpeakingGrammarTopicActivity.class);
                    intent.putExtra("levelName", levelName);
                    startActivity(intent);
                }
            });
        } else {
            Log.e("ChooseSpeakingMethod", "crdGrammar is null in addEvents.");
        }
        if (crdVoice != null) {
            crdVoice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(ChooseSpeakingLearningMethod.this, ChooseSpeakingPronunciationTopicActivity.class);
                    intent.putExtra("levelName", levelName);
                    startActivity(intent);
                }
            });
        } else {
            Log.e("ChooseSpeakingMethod", "crdVoice is null in addEvents.");
        }
    }
}
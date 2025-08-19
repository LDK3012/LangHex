package vn.enghexteam.enghex.View;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import vn.enghexteam.enghex.R;

public class ReviewActivity extends AppCompatActivity {
    private ImageView imgBack;
    private LinearLayout lnrSpeaking , lnrReading , lnrListening ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);
        //
        addControls();
        addEvents();
    }

    private void addControls (){
        imgBack = findViewById(R.id.imgBack) ;
        lnrSpeaking = findViewById(R.id.lnrSpeaking) ;
        lnrListening = findViewById(R.id.lnrListening) ;
        lnrReading = findViewById(R.id.lnrReading) ;

    }
    private void addEvents (){
        imgBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        //
//        lnrSpeaking.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(ReviewActivity.this, ChooseSpeakingGrammarTopicActivity.class) ;
//                startActivity(intent);
//            }
//        });
//        lnrListening.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(ReviewActivity.this, InternalListeningTopic.class) ;
//                startActivity(intent);
//            }
//        });
//        //
//        lnrReading.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(ReviewActivity.this, InternalReadingTopic.class) ;
//                startActivity(intent);
//            }
//        });

    }
}
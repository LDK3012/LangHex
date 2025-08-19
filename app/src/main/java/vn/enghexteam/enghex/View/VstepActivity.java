package vn.enghexteam.enghex.View;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import vn.enghexteam.enghex.R;

public class VstepActivity extends AppCompatActivity {
    private ImageView imgBack ;
    private LinearLayout lnrRevise ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vstep);
        //
        addControls();
        addEvents();
    }

    private void addControls(){
        imgBack = findViewById(R.id.imgBack) ;
        lnrRevise = findViewById(R.id.lnrRevise) ;
    }
    private void addEvents(){
        imgBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        //
        lnrRevise.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(VstepActivity.this, ReviewActivity.class) ;
                startActivity(intent);
            }
        });
    }
}
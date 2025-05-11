package com.example.langhexx.View;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;


import com.example.langhexx.R;

public class CreatePostActivity extends AppCompatActivity {

    ImageView imgBackward;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);
        addControls();
        addEvents();
    }

    public void addControls(){
        imgBackward = findViewById(R.id.imgBackward);
    }

    public void addEvents(){
        imgBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish(); // Or navigate back as needed
            }
        });
    }
}
package com.example.langhexx.View;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.langhexx.R;

public class CreatePostActivity extends AppCompatActivity {

    ImageView imgBackward;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // After attachBaseContext is called, setContentView will use the correct locale
        setContentView(R.layout.activity_create_post);
        addControls();
        addEvents();
    }

    public void addControls(){
        // Now, getString(R.id.btnBack) and other resource lookups
        // within this Activity should use the correct language.
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
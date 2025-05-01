package com.example.langhexx.View;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;

import com.example.langhexx.Controller.LocaleHelper;
import com.example.langhexx.R;

import java.util.Locale;

public class FeedbackActivity extends AppCompatActivity {

    protected void attachBaseContext(Context newBase) {
        Context context = LocaleHelper.onAttach(newBase); // Gọi LocaleHelper để cấu hình locale
        super.attachBaseContext(context);
    }

    Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);
        addControls();
        addEvents();
    }

    public void addControls(){
        btnBack = findViewById(R.id.btnBack);
    }

    public void addEvents(){
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
}
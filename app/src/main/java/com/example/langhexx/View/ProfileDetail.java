package com.example.langhexx.View;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.langhexx.Controller.AuthController;
import com.example.langhexx.R;

public class ProfileDetail extends AppCompatActivity {

    private Button btnBack;
    private Button btnLogout ;
    private AuthController authController ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_detail);
        addControls();
        addEvents();
    }

    public void addControls(){
        btnBack = findViewById(R.id.btnBack);
        btnLogout = findViewById(R.id.btnLogout) ;
    }

    public void addEvents(){
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authController = new AuthController(new AuthController.AuthCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(ProfileDetail.this, "Logout Successfully!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(ProfileDetail.this, "Logout Failed", Toast.LENGTH_SHORT).show();
                    }
                }) ;
                authController.signOut(ProfileDetail.this);
            }
        });
    }
}
package com.example.langhexx.View;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.langhexx.Controller.AuthController;
import com.example.langhexx.R;

public class LoginActivity extends AppCompatActivity implements AuthController.AuthCallback{
    private Button btnLogin, btnMicrosoft;
    private AuthController authController;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        //
       authController = new AuthController(this) ;
       //check login status
        authController.checkUserSession(this);
        //
        addControls();
        addEvents();
    }

    private void addControls() {
        btnLogin = findViewById(R.id.btnLogin);
        btnMicrosoft = findViewById(R.id.btnMicrosoft);
    }


    private void addEvents() {
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
        //
        btnMicrosoft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authController.signInWithMicrosoft(LoginActivity.this);
            }
        });
    }

    @Override
    public void onSuccess() {
        Toast.makeText(this, "Login Successfully!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFailure(String errorMessage) {
        Toast.makeText(this, "Login Failed!", Toast.LENGTH_SHORT).show();
    }
}
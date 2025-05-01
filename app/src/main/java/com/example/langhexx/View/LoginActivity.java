package com.example.langhexx.View;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.langhexx.Controller.AuthController;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.User;
import com.example.langhexx.R;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity implements AuthController.AuthCallback{
    private Button btnLogin, btnMicrosoft;
    private AuthController authController;
    private EditText edtStudentCode , edtStudentPassword ;

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
        //
        edtStudentCode = findViewById(R.id.edtStudentCode) ;
        edtStudentPassword = findViewById(R.id.edtPassword) ;
    }



    private void addEvents() {
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username = edtStudentCode.getText().toString();
                String password = edtStudentPassword.getText().toString();
                authController.handleLogin(LoginActivity.this,username,password);
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
        CustomToast.showSuccess(this, "Đăng nhập thành công!", R.drawable.success);
    }

    @Override
    public void onFailure(String errorMessage) {
        CustomToast.showSuccess(this, "Đăng nhập thất bại!", R.drawable.fail_icon);
    }
}
package vn.enghexteam.enghex.View;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import vn.enghexteam.enghex.Controller.AuthController;
import vn.enghexteam.enghex.Model.CustomToast;
import vn.enghexteam.enghex.R;

public class LoginActivity extends AppCompatActivity implements AuthController.AuthCallback {
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
        CustomToast.showSuccess(this, "Login Successfully !", R.drawable.success);
    }

    @Override
    public void onFailure(String errorMessage) {
        Log.e("AuthError", errorMessage);
        CustomToast.showFail(this, "Fail !", R.drawable.fail_icon);
    }
}
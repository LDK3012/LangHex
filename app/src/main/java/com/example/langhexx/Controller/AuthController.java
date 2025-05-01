package com.example.langhexx.Controller;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.User;
import com.example.langhexx.Model.UsernamePasswordSessionManager;
import com.example.langhexx.R;
import com.example.langhexx.View.LoginActivity;
import com.example.langhexx.View.MainActivity;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.OAuthCredential;
import com.google.firebase.auth.OAuthProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthController {
    private FirebaseAuth firebaseAuth;
    private AuthCallback authCallback;
    private Map<String, String> accountSample = new HashMap<>();

    public AuthController(AuthCallback authCallback) {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.authCallback = authCallback;
    }

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public void signInWithMicrosoft(Activity activity) {
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("microsoft.com");
        provider.addCustomParameter("prompt", "consent");
        provider.addCustomParameter("tenant", "common");

        List<String> scopes = new ArrayList<>();
        provider.setScopes(scopes);

        Task<AuthResult> pendingResultTask = firebaseAuth.getPendingAuthResult();
        if (pendingResultTask != null) {
            pendingResultTask.addOnSuccessListener(authResult -> handleSignInSuccess(activity, authResult))
                    .addOnFailureListener(e -> authCallback.onFailure(e.getMessage()));
        } else {
            firebaseAuth.startActivityForSignInWithProvider(activity, provider.build())
                    .addOnSuccessListener(authResult -> handleSignInSuccess(activity, authResult))
                    .addOnFailureListener(e -> authCallback.onFailure(e.getMessage()));
        }
    }

    private void handleSignInSuccess(Activity activity, AuthResult authResult) {
        FirebaseUser user = authResult.getUser();
        OAuthCredential credential = (OAuthCredential) authResult.getCredential();
        String accessToken = credential.getAccessToken();

        // Chuyển đến MainActivity sau khi đăng nhập thành công
        authCallback.onSuccess();
        Intent intent = new Intent(activity, MainActivity.class);
        activity.startActivity(intent);
        activity.finish();
    }

    public void signOut(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Xác nhận")
                .setMessage("Bạn có chắc muốn đăng xuất ?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(activity);
                        sessionManager.logout();
                        //
                        firebaseAuth.signOut();
                        Intent intent = new Intent(activity, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        activity.startActivity(intent);
                        activity.finish();
                    }
                })
                .setNegativeButton("No", null) // Đóng dialog nếu chọn "No"
                .show();
    }

    public void checkUserSession(Activity activity) {
        UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(activity) ;

        if (firebaseAuth.getCurrentUser() != null || sessionManager.isLoggedIn()) {
            Intent intent = new Intent(activity, MainActivity.class);
            activity.startActivity(intent);
            activity.finish();
        }
    }


    //login by username and password
    public void handleLogin(Context context, String username, String password) {
        username = username.trim();
        password = password.trim();


        // Trường hợp cả username và password đều trống
        if (username.isEmpty() && password.isEmpty()) {
            CustomToast.showFail(context, "Vui lòng nhập Mssv và Password !", R.drawable.fail_icon);
            return;
        }
        //

        // Kiểm tra input trống trước
        if (username.isEmpty()) {
            CustomToast.showFail(context, "Vui lòng nhập Mssv !", R.drawable.fail_icon);
            return;
        }

        if (password.isEmpty()) {
            CustomToast.showFail(context, "Vui lòng nhập Password !", R.drawable.fail_icon);
            return;
        }

        User user = new User(username, password);

        if (!user.isValidUsername()) {
            CustomToast.showFail(context, "Mssv hoặc Password không đúng !", R.drawable.fail_icon);
            return;
        }

        if (!user.isValidPassword()) {
            CustomToast.showFail(context, "Mssv hoặc Password không đúng !", R.drawable.fail_icon);
            return;
        }

        if (user.authenticate()) {
            //save login session
            UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(context) ;
            sessionManager.createLoginSession(username);
            //
            CustomToast.showSuccess(context, "Đăng nhập thành công", R.drawable.success);
            context.startActivity(new Intent(context, MainActivity.class));
            ((Activity) context).finish();
        } else {
            CustomToast.showFail(context, "Mssv hoặc Password không đúng !", R.drawable.fail_icon);
        }
    }

}

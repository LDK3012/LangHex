package com.example.langhexx.Controller;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import com.example.langhexx.View.LoginActivity;
import com.example.langhexx.View.MainActivity;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.OAuthCredential;
import com.google.firebase.auth.OAuthProvider;

import java.util.ArrayList;
import java.util.List;

public class AuthController {
    private FirebaseAuth firebaseAuth;
    private AuthCallback authCallback;

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
                .setTitle("Confirm Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
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
        if (firebaseAuth.getCurrentUser() != null) {
            Intent intent = new Intent(activity, MainActivity.class);
            activity.startActivity(intent);
            activity.finish();
        }
    }

}

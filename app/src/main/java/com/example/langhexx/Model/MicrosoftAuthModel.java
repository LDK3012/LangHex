package com.example.langhexx.Model;

import android.app.Activity;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.OAuthProvider;

import java.util.ArrayList;
import java.util.List;

public class MicrosoftAuthModel {
    private static final String TAG = "MicrosoftAuthModel"; // Tag cho logging
    private FirebaseAuth firebaseAuth;

    // Interface để thông báo kết quả về Controller
    public interface MicrosoftAuthListener {
        void onSignInSuccess(FirebaseUser user);
        void onSignInFailure(String errorMessage);
    }

    public MicrosoftAuthModel() {
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    public void signInWithMicrosoft(Activity activity, MicrosoftAuthListener listener) {
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("microsoft.com");
        // Các cấu hình tùy chọn (giữ nguyên như cũ)
        provider.addCustomParameter("prompt", "consent");
        provider.addCustomParameter("tenant", "common"); // Hoặc tenant ID cụ thể nếu cần
        List<String> scopes = new ArrayList<>();
        // Thêm scopes cần thiết nếu có, ví dụ:
        // scopes.add("User.Read");
        // scopes.add("Mail.Read");
        provider.setScopes(scopes);

        // 1. Kiểm tra xem có kết quả xác thực đang chờ xử lý không
        Task<AuthResult> pendingResultTask = firebaseAuth.getPendingAuthResult();
        if (pendingResultTask != null) {
            Log.d(TAG, "Handling pending auth result.");
            pendingResultTask
                    .addOnSuccessListener(authResult -> {
                        Log.d(TAG, "Pending sign-in success.");
                        listener.onSignInSuccess(authResult.getUser());
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Pending sign-in failure.", e);
                        listener.onSignInFailure(e.getMessage());
                    });
        } else {
            // 2. Nếu không có, bắt đầu quy trình đăng nhập mới
            Log.d(TAG, "Starting new sign-in flow.");
            firebaseAuth.startActivityForSignInWithProvider(activity, provider.build())
                    .addOnSuccessListener(authResult -> {
                        Log.d(TAG, "New sign-in success.");
                        // Không cần lấy AccessToken ở đây trừ khi Model cần xử lý gì đó với nó
                        // OAuthCredential credential = (OAuthCredential) authResult.getCredential();
                        // String accessToken = credential.getAccessToken();
                        listener.onSignInSuccess(authResult.getUser());
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "New sign-in failure.", e);
                        listener.onSignInFailure(e.getMessage());
                    });
        }
    }
}

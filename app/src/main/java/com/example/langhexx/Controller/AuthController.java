package com.example.langhexx.Controller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.MicrosoftAuthModel;
import com.example.langhexx.Model.UsernamePasswordUser;
import com.example.langhexx.Model.UsernamePasswordSessionManager;
import com.example.langhexx.R;
import com.example.langhexx.View.LoginActivity;
import com.example.langhexx.View.MainActivity;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class AuthController implements MicrosoftAuthModel.MicrosoftAuthListener {
    private FirebaseAuth firebaseAuth;
    private AuthCallback authCallback;
    private MicrosoftAuthModel microsoftAuthModel;
    private DatabaseReference databaseReference;
    private static final String TAG = "AuthController";


    public AuthController(AuthCallback authCallback) {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.authCallback = authCallback;
        this.microsoftAuthModel = new MicrosoftAuthModel();
        this.databaseReference = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference();
    }

    @Override
    public void onSignInSuccess(Activity activity, AuthResult authResult) {
        Log.d(TAG, "Microsoft sign-in successful via Firebase.");
        FirebaseUser firebaseUser = authResult.getUser();

        if (firebaseUser != null) {
            saveMicrosoftUserToDatabase(firebaseUser);
        } else {
            Log.e(TAG, "FirebaseUser is null after Microsoft sign-in success.");
            if (authCallback != null) {
                authCallback.onFailure("Lỗi xác thực người dùng.");
            }
            return;
        }

        if (authCallback != null) {
            authCallback.onSuccess();
        }
        navigateToMainActivity(activity);
    }

    private void saveMicrosoftUserToDatabase(FirebaseUser firebaseUser) {
        String firebaseUid = firebaseUser.getUid(); // Key sẽ là Firebase UID
        DatabaseReference microsoftUserNodeRef = databaseReference
                .child("Users")
                .child("MicrosoftUsers")
                .child(firebaseUid);

        String userEmail = firebaseUser.getEmail();
        String userName = firebaseUser.getDisplayName();
        String msGraphId = null;

        for (UserInfo profile : firebaseUser.getProviderData()) {
            if ("microsoft.com".equals(profile.getProviderId())) {
                msGraphId = profile.getUid(); // Đây là Microsoft Graph ID
                if (profile.getEmail() != null && !profile.getEmail().isEmpty()) {
                    userEmail = profile.getEmail(); // Ưu tiên email từ Microsoft provider
                }
                if (profile.getDisplayName() != null && !profile.getDisplayName().isEmpty()) {
                    userName = profile.getDisplayName(); // Ưu tiên display name từ Microsoft provider
                }
                Log.d(TAG, "Found Microsoft Graph ID from ProviderData: " + msGraphId);
                break;
            }
        }

        Map<String, Object> microsoftUserData = new HashMap<>();
        microsoftUserData.put("email", userEmail);
        if (msGraphId != null) {
            microsoftUserData.put("microsoftGraphId", msGraphId);
        } else {
            Log.w(TAG, "Microsoft Graph ID not found in provider data for Firebase UID: " + firebaseUid +
                    ". The 'microsoftGraphId' field will be missing for this user.");
        }
        microsoftUserData.put("name", userName);

        microsoftUserNodeRef.setValue(microsoftUserData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.i(TAG, "Microsoft user data saved successfully under Users/MicrosoftUsers/" + firebaseUid);
                    } else {
                        Log.e(TAG, "Failed to save Microsoft user data under Users/MicrosoftUsers/" + firebaseUid, task.getException());
                    }
                });
    }


    private void navigateToMainActivity(Activity activity) {
        if (activity != null) {
            Intent intent = new Intent(activity, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
        } else if (authCallback instanceof Activity) {
            Log.w(TAG, "Activity parameter was null, attempting fallback using AuthCallback cast.");
            Activity fallbackActivity = (Activity) authCallback;
            Intent intent = new Intent(fallbackActivity, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            fallbackActivity.startActivity(intent);
            fallbackActivity.finish();
        } else {
            Log.e(TAG, "Cannot navigate - Activity context is missing.");
            if (authCallback != null) {
                authCallback.onFailure("Lỗi điều hướng sau khi đăng nhập.");
            }
        }
    }

    @Override
    public void onSignInFailure(String errorMessage) {
        Log.e(TAG, "Microsoft sign-in failed: " + errorMessage);
        if (authCallback != null) {
            authCallback.onFailure("Đăng nhập Microsoft thất bại: " + errorMessage);
        }
    }

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public void signInWithMicrosoft(Activity activity) {
        Log.d(TAG, "Initiating Microsoft sign-in process.");
        microsoftAuthModel.signInWithMicrosoft(activity, this);
    }

    public void signOut(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Confirm")
                .setMessage("Are you sure want to logout ?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(activity);
                    sessionManager.logout();
                    clearMsGraphToken(activity);
                    firebaseAuth.signOut();
                    Log.i(TAG, "User signed out from Firebase.");
                    Intent intent = new Intent(activity, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                    activity.finish();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void clearMsGraphToken(Context context) {
        if (context == null) return;
        Log.i(TAG, "Clearing MS Graph Token from SharedPreferences on sign out.");
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences("MSGraphPrefs", Context.MODE_PRIVATE);
        prefs.edit().remove("ms_graph_token").apply();
    }

    public void checkUserSession(Activity activity) {
        UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(activity) ;
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser != null) {
            Log.d(TAG, "Firebase user session active. UID: " + currentUser.getUid() + ". Navigating to MainActivity.");
            Intent intent = new Intent(activity, MainActivity.class);
            activity.startActivity(intent);
            activity.finish();
        } else if (sessionManager.isLoggedIn()){
            Log.d(TAG, "Username/Password (non-Firebase) session active. Navigating to MainActivity.");
            Intent intent = new Intent(activity, MainActivity.class);
            activity.startActivity(intent);
            activity.finish();
        }
        else {
            Log.d(TAG, "No active user session.");
        }
    }

    public void handleLogin(Context context, String username, String password) {
        username = username.trim();
        password = password.trim();

        if (username.isEmpty() && password.isEmpty()) {
            CustomToast.showFail(context, "Enter Student Code and Password !", R.drawable.fail_icon);
            return;
        }
        if (username.isEmpty()) {
            CustomToast.showFail(context, "Enter Student Code !", R.drawable.fail_icon);
            return;
        }
        if (password.isEmpty()) {
            CustomToast.showFail(context, "Enter Password !", R.drawable.fail_icon);
            return;
        }

        UsernamePasswordUser user = new UsernamePasswordUser(username, password);

        if (!user.isValidUsername() || !user.isValidPassword()) {
            CustomToast.showFail(context, "Student Code or Password is incorrect !", R.drawable.fail_icon);
            return;
        }

        if (user.authenticate()) {
            Log.d(TAG,"Username/Password (non-Firebase) authentication successful for: " + username);
            UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(context) ;
            sessionManager.createLoginSession(username);

            if (authCallback != null) {
                authCallback.onSuccess();
            } else {
                CustomToast.showSuccess(context, "Login Successfully !", R.drawable.success);
            }
            navigateToMainActivity((Activity) context);
        } else {
            Log.w(TAG,"Username/Password (non-Firebase) authentication failed for: " + username);
            if (authCallback != null) {
                authCallback.onFailure("Student Code or Password is incorrect !");
            } else {
                CustomToast.showFail(context, "Student Code or Password is incorrect !", R.drawable.fail_icon);
            }
        }
    }
}

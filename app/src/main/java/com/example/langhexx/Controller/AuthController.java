//package com.example.langhexx.Controller;
//
//import android.app.Activity;
//import android.content.Context;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.widget.Toast;
//
//import androidx.appcompat.app.AlertDialog;
//
//import com.example.langhexx.Model.CustomToast;
//import com.example.langhexx.Model.MicrosoftAuthModel;
//import com.example.langhexx.Model.User;
//import com.example.langhexx.Model.UsernamePasswordSessionManager;
//import com.example.langhexx.R;
//import com.example.langhexx.View.LoginActivity;
//import com.example.langhexx.View.MainActivity;
//import com.google.android.gms.tasks.Task;
//import com.google.firebase.auth.AuthResult;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseUser;
//import com.google.firebase.auth.OAuthCredential;
//import com.google.firebase.auth.OAuthProvider;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class AuthController implements MicrosoftAuthModel.MicrosoftAuthListener {
//    private FirebaseAuth firebaseAuth;
//    private AuthCallback authCallback;
//    private MicrosoftAuthModel microsoftAuthModel;
//
//    public AuthController(AuthCallback authCallback) {
//        this.firebaseAuth = FirebaseAuth.getInstance();
//        this.authCallback = authCallback;
//        this.microsoftAuthModel = new MicrosoftAuthModel();
//    }
//
//    @Override
//    public void onSignInSuccess(FirebaseUser user) {
//        if (authCallback != null) {
//            authCallback.onSuccess();
//        }
//        if (authCallback instanceof Activity) {
//            Activity activity = (Activity) authCallback;
//            Intent intent = new Intent(activity, MainActivity.class);
//            activity.startActivity(intent);
//            activity.finish();
//        } else {
//            // Log hoặc xử lý lỗi nếu không lấy được Context/Activity
//            System.err.println("AuthController: Cannot navigate - AuthCallback is not an Activity");
//        }
//    }
//
//    @Override
//    public void onSignInFailure(String errorMessage) {
//        if (authCallback != null) {
//            authCallback.onFailure(errorMessage);
//        }
//    }
//
//    public interface AuthCallback {
//        void onSuccess();
//        void onFailure(String errorMessage);
//    }
//
//    public void signInWithMicrosoft(Activity activity) {
//       microsoftAuthModel.signInWithMicrosoft(activity, (MicrosoftAuthModel.MicrosoftAuthListener) this);
//    }
//
//
//
//
//    public void signOut(Activity activity) {
//        new AlertDialog.Builder(activity)
//                .setTitle("Xác nhận")
//                .setMessage("Bạn có chắc muốn đăng xuất ?")
//                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(activity);
//                        sessionManager.logout();
//                        //
//                        firebaseAuth.signOut();
//                        Intent intent = new Intent(activity, LoginActivity.class);
//                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        activity.startActivity(intent);
//                        activity.finish();
//                    }
//                })
//                .setNegativeButton("No", null) // Đóng dialog nếu chọn "No"
//                .show();
//    }
//
//    public void checkUserSession(Activity activity) {
//        UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(activity) ;
//
//        if (firebaseAuth.getCurrentUser() != null || sessionManager.isLoggedIn()) {
//            Intent intent = new Intent(activity, MainActivity.class);
//            activity.startActivity(intent);
//            activity.finish();
//        }
//    }
//
//
//    //login by username and password
//    public void handleLogin(Context context, String username, String password) {
//        username = username.trim();
//        password = password.trim();
//
//
//        // Trường hợp cả username và password đều trống
//        if (username.isEmpty() && password.isEmpty()) {
//            CustomToast.showFail(context, "Vui lòng nhập Mssv và Password !", R.drawable.fail_icon);
//            return;
//        }
//        //
//
//        // Kiểm tra input trống trước
//        if (username.isEmpty()) {
//            CustomToast.showFail(context, "Vui lòng nhập Mssv !", R.drawable.fail_icon);
//            return;
//        }
//
//        if (password.isEmpty()) {
//            CustomToast.showFail(context, "Vui lòng nhập Password !", R.drawable.fail_icon);
//            return;
//        }
//
//        User user = new User(username, password);
//
//        if (!user.isValidUsername()) {
//            CustomToast.showFail(context, "Mssv hoặc Password không đúng !", R.drawable.fail_icon);
//            return;
//        }
//
//        if (!user.isValidPassword()) {
//            CustomToast.showFail(context, "Mssv hoặc Password không đúng !", R.drawable.fail_icon);
//            return;
//        }
//
//        if (user.authenticate()) {
//            //save login session
//            UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(context) ;
//            sessionManager.createLoginSession(username);
//            //
//            CustomToast.showSuccess(context, "Đăng nhập thành công", R.drawable.success);
//            context.startActivity(new Intent(context, MainActivity.class));
//            ((Activity) context).finish();
//        } else {
//            CustomToast.showFail(context, "Mssv hoặc Password không đúng !", R.drawable.fail_icon);
//        }
//    }
//
//}
//

package com.example.langhexx.Controller;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences; // Keep imports if needed elsewhere
import android.text.TextUtils; // Keep imports if needed elsewhere
import android.util.Log; // Import Log
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.MicrosoftAuthModel;
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

// *** CHANGED: Implement the modified listener ***
public class AuthController implements MicrosoftAuthModel.MicrosoftAuthListener {
    private FirebaseAuth firebaseAuth;
    private AuthCallback authCallback; // Callback for LoginActivity (simple success/fail)
    private MicrosoftAuthModel microsoftAuthModel;
    private static final String TAG = "AuthController"; // Add Log tag


    public AuthController(AuthCallback authCallback) {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.authCallback = authCallback;
        this.microsoftAuthModel = new MicrosoftAuthModel();
    }

    // *** CHANGED: Implement the updated onSignInSuccess ***
    @Override
    public void onSignInSuccess(Activity activity, AuthResult authResult) {
        Log.d(TAG, "Microsoft sign-in successful. Navigating to MainActivity.");
        // Token saving is now handled within MicrosoftAuthModel

        // Notify the LoginActivity (using the simple callback)
        if (authCallback != null) {
            authCallback.onSuccess(); // Let LoginActivity show its success message
        }

        // Navigate to MainActivity
        if (activity != null) {
            Intent intent = new Intent(activity, MainActivity.class);
            // Add flags to clear the back stack and start MainActivity fresh
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish(); // Finish LoginActivity
        } else if (authCallback instanceof Activity) {
            // Fallback if activity param wasn't passed correctly (shouldn't happen now)
            Log.w(TAG, "Activity parameter was null, attempting fallback using AuthCallback cast.");
            Activity fallbackActivity = (Activity) authCallback;
            Intent intent = new Intent(fallbackActivity, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            fallbackActivity.startActivity(intent);
            fallbackActivity.finish();
        } else {
            Log.e(TAG, "Cannot navigate - Activity context is missing.");
            // Optionally, notify the user via the simple callback if possible
            if (authCallback != null) {
                authCallback.onFailure("Lỗi điều hướng sau khi đăng nhập.");
            }
        }
    }


    // *** CHANGED: Implement onSignInFailure ***
    @Override
    public void onSignInFailure(String errorMessage) {
        Log.e(TAG, "Microsoft sign-in failed: " + errorMessage);
        if (authCallback != null) {
            // Pass the specific error message back to LoginActivity
            authCallback.onFailure("Đăng nhập Microsoft thất bại: " + errorMessage);
        }
    }

    // Interface for LoginActivity callbacks (remains simple)
    public interface AuthCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    // Method called by LoginActivity
    public void signInWithMicrosoft(Activity activity) {
        Log.d(TAG, "Initiating Microsoft sign-in process.");
        // Pass 'this' as the listener, since AuthController implements MicrosoftAuthListener
        microsoftAuthModel.signInWithMicrosoft(activity, this);
    }

    // --- Other methods remain unchanged ---

    public void signOut(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Xác nhận")
                .setMessage("Bạn có chắc muốn đăng xuất ?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(activity);
                    sessionManager.logout();
                    // Clear MS Graph Token on sign out
                    clearMsGraphToken(activity);
                    // Sign out from Firebase
                    firebaseAuth.signOut();
                    Intent intent = new Intent(activity, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                    activity.finish();
                })
                .setNegativeButton("No", null)
                .show();
    }

    // Helper method to clear token on sign out
    private void clearMsGraphToken(Context context) {
        if (context == null) return;
        Log.i(TAG, "Clearing MS Graph Token from SharedPreferences on sign out.");
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences("MSGraphPrefs", Context.MODE_PRIVATE); // Use constant name
        prefs.edit().remove("ms_graph_token").apply(); // Use constant key
    }


    public void checkUserSession(Activity activity) {
        UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(activity) ;
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser != null || sessionManager.isLoggedIn()) {
            Log.d(TAG, "User session active. Navigating to MainActivity.");
            Intent intent = new Intent(activity, MainActivity.class);
            activity.startActivity(intent);
            activity.finish();
        } else {
            Log.d(TAG, "No active user session.");
        }
    }


    public void handleLogin(Context context, String username, String password) {
        username = username.trim();
        password = password.trim();

        if (username.isEmpty() && password.isEmpty()) {
            CustomToast.showFail(context, "Vui lòng nhập Mssv và Password !", R.drawable.fail_icon);
            return;
        }
        if (username.isEmpty()) {
            CustomToast.showFail(context, "Vui lòng nhập Mssv !", R.drawable.fail_icon);
            return;
        }
        if (password.isEmpty()) {
            CustomToast.showFail(context, "Vui lòng nhập Password !", R.drawable.fail_icon);
            return;
        }

        User user = new User(username, password);

        // Assuming User model has these validation methods - keep original logic
        if (!user.isValidUsername() || !user.isValidPassword()) {
            CustomToast.showFail(context, "Mssv hoặc Password không đúng !", R.drawable.fail_icon);
            return;
        }


        if (user.authenticate()) {
            Log.d(TAG,"Username/Password authentication successful for: " + username);
            UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(context) ;
            sessionManager.createLoginSession(username);
            // Notify LoginActivity via its callback
            if (authCallback != null) {
                authCallback.onSuccess();
            } else {
                // Fallback toast if callback is null somehow
                CustomToast.showSuccess(context, "Đăng nhập thành công", R.drawable.success);
            }

            Intent intent = new Intent(context, MainActivity.class);
            // Add flags to clear the back stack and start MainActivity fresh
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
            if (context instanceof Activity) { // Ensure context is an Activity before finishing
                ((Activity) context).finish();
            }
        } else {
            Log.w(TAG,"Username/Password authentication failed for: " + username);
            // Notify LoginActivity via its callback
            if (authCallback != null) {
                authCallback.onFailure("Mssv hoặc Password không đúng !");
            } else {
                // Fallback toast if callback is null somehow
                CustomToast.showFail(context, "Mssv hoặc Password không đúng !", R.drawable.fail_icon);
            }
        }
    }
}

package com.example.langhexx.Model;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.OAuthProvider;

import java.util.ArrayList;
import java.util.List;

public class MicrosoftAuthModel {
    private static final String TAG = "MicrosoftAuthModel";
    private FirebaseAuth firebaseAuth;

    // Constants for SharedPreferences
    private static final String MS_GRAPH_PREFS = "MSGraphPrefs";
    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token";

    // Interface để thông báo kết quả về Controller
    public interface MicrosoftAuthListener {
        // Pass the full AuthResult on success
        void onSignInSuccess(Activity activity, AuthResult authResult);
        void onSignInFailure(String errorMessage);
    }

    public MicrosoftAuthModel() {
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    public void signInWithMicrosoft(Activity activity, MicrosoftAuthListener listener) {
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("microsoft.com");
        provider.addCustomParameter("prompt", "consent");
        provider.addCustomParameter("tenant", "common");
        List<String> scopes = new ArrayList<>();
        scopes.add("User.Read");
        provider.setScopes(scopes);

        Task<AuthResult> pendingResultTask = firebaseAuth.getPendingAuthResult();
        if (pendingResultTask != null) {
            Log.d(TAG, "Handling pending auth result.");
            pendingResultTask
                    .addOnSuccessListener(authResult -> {
                        Log.d(TAG, "Pending sign-in success.");
                        saveTokenFromAuthResult(activity, authResult);
                        listener.onSignInSuccess(activity, authResult);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Pending sign-in failure.", e);
                        listener.onSignInFailure(e.getMessage());
                    });
        } else {
            Log.d(TAG, "Starting new sign-in flow.");
            firebaseAuth.startActivityForSignInWithProvider(activity, provider.build())
                    .addOnSuccessListener(authResult -> {
                        Log.d(TAG, "New sign-in success.");
                        saveTokenFromAuthResult(activity, authResult);
                        listener.onSignInSuccess(activity, authResult);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "New sign-in failure.", e);
                        listener.onSignInFailure(e.getMessage());
                    });
        }
    }

    private void saveTokenFromAuthResult(Context context, AuthResult authResult) {
        if (authResult == null || context == null) {
            Log.e(TAG, "Cannot save token, AuthResult or Context is null.");
            return;
        }

        com.google.firebase.auth.AuthCredential credential = authResult.getCredential();
        if (credential instanceof com.google.firebase.auth.OAuthCredential) {
            com.google.firebase.auth.OAuthCredential oauthCredential = (com.google.firebase.auth.OAuthCredential) credential;
            String accessToken = oauthCredential.getAccessToken();

            if (!TextUtils.isEmpty(accessToken)) {
                saveMsGraphToken(context, accessToken);
            } else {
                Log.e(TAG, "Microsoft Graph Access Token is null or empty in AuthResult.");
            }
        } else {
            Log.e(TAG, "Credential is not an OAuthCredential in AuthResult.");
        }
    }

    private void saveMsGraphToken(Context context, String token) {
        if (token == null || context == null) return;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(MS_GRAPH_TOKEN_KEY, token);
        editor.apply(); // Use apply() for asynchronous saving
        Log.i(TAG, "Microsoft Graph Access Token saved to SharedPreferences.");
    }
}

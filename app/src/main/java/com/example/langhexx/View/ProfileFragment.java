package com.example.langhexx.View;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.langhexx.Model.UsernamePasswordSessionManager;
import com.example.langhexx.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";
    TextView txtProfileDetail, txtFeedback, txtName, txtEmail, txtLanguage;
    private ImageView avatarImg ;
    private FirebaseAuth mAuth;
    private UsernamePasswordSessionManager sessionManager;
    //
    private static final String MS_GRAPH_PREFS = "MSGraphPrefs"; // SharedPreferences name
    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token"; // Key for the token
    private static final String MICROSOFT_PROVIDER_ID = "microsoft.com"; // Common Provider ID for Microsoft
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // Create single thread executor
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        //
        mAuth = FirebaseAuth.getInstance();
        sessionManager = new UsernamePasswordSessionManager(getContext());
        //
        addControls(view);
        loadUserProfileInfo();
        addEvents();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null && !executorService.isShutdown()) {
            Log.d(TAG, "Shutting down ExecutorService.");
            executorService.shutdown();
        }
    }

    public void addControls(View view) {
        txtProfileDetail = view.findViewById(R.id.txtInfo);
        txtFeedback = view.findViewById(R.id.txtFeedback);
        txtName = view.findViewById(R.id.txtName);
        txtEmail = view.findViewById(R.id.txtMail);
        txtLanguage = view.findViewById(R.id.txtLanguage);
        avatarImg = view.findViewById(R.id.profileImg) ;

    }

    public void addEvents() {
        txtProfileDetail.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ProfileDetail.class);
            startActivity(intent);
        });

        txtFeedback.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), FeedbackActivity.class);
            startActivity(intent);
        });

//        txtLanguage.setOnClickListener(v -> {
//            showLanguageDialog();
//        });
    }

    // Helper method copied/adapted from HomeFragment to extract name
    private String extractNameFromDisplayName(String displayName, String email) {
        String nameToDisplay = null;
        // Process displayName to get name part (assuming "Something - Name" format)
        if (!TextUtils.isEmpty(displayName)) {
            String[] parts = displayName.split("-");
            if (parts.length > 1 && !TextUtils.isEmpty(parts[1])) {
                nameToDisplay = parts[1].trim(); // Get the second part (name)
            } else {
                nameToDisplay = displayName; // Fallback to full display name if format is different
            }
        }

        // Fallback to email if name couldn't be extracted from displayName
        if (TextUtils.isEmpty(nameToDisplay) && !TextUtils.isEmpty(email)) {
            nameToDisplay = email;
        }

        // Final fallback to a generic name if both are empty
        if (TextUtils.isEmpty(nameToDisplay)) {
            // Use a string resource for the default name
            nameToDisplay = "Unknown"; // e.g., "User" or "Người dùng"
        }
        return nameToDisplay;
    }

    private void loadUserProfileInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Priority 1: Check Firebase User (Includes Microsoft, Google, etc.)
        if (currentUser != null) {
            Log.d(TAG, "Firebase user detected.");
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();
            Uri photoUrl = currentUser.getPhotoUrl();
            String nameToDisplay = extractNameFromDisplayName(displayName, email); // Use helper

            txtName.setText(nameToDisplay);
            txtEmail.setText(!TextUtils.isEmpty(email) ? email : "Unknown");
            //
            // Check if user is signed in with Microsoft
            boolean isMicrosoftUser = false;
            for (UserInfo profile : currentUser.getProviderData()) {
                if (MICROSOFT_PROVIDER_ID.equals(profile.getProviderId())) {
                    isMicrosoftUser = true;
                    break;
                }
            }

            if (isMicrosoftUser) {
                // Microsoft user: Try to load photo from Graph API using stored token
                String msGraphToken = getMsGraphToken();
                if (!TextUtils.isEmpty(msGraphToken)) {
                    fetchMicrosoftProfilePhoto(msGraphToken, avatarImg);
                } else {
                    Log.w(TAG, "Microsoft Graph token not found in SharedPreferences.");
                }
            } else if (photoUrl != null && getActivity() != null) {
                // Non-Microsoft user with a photo URL (e.g., Google)
                Log.d(TAG, "Non-Microsoft user with photo URL. Loading with Glide.");
                Glide.with(requireActivity())
                        .load(photoUrl)
                        .error(R.drawable.unknown_avatar)
                        .placeholder(R.drawable.unknown_avatar)
                        .circleCrop()
                        .into(avatarImg);
            } else {
                Log.d(TAG, "User logged in, but no specific photo provider handled (or photoUrl is null). Default avatar remains.");
                // Keep the default avatar if no specific handling applies or photoUrl is null
            }
        }
        // Priority 2: Check Username/Password Session Manager
        else if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Username/Password session user detected.");
            String username = sessionManager.getUsername();
            // As requested: Set name to "Learner" and email to the stored username
            txtName.setText("Leaner"); // "Learner"
            txtEmail.setText(!TextUtils.isEmpty(username) ? username : "Unknown");
            if (getActivity() != null) { // Ensure context is available for resource
                avatarImg.setImageResource(R.drawable.avatar); // Specific default for session users
            }
        }
        // Priority 3: Guest User (Not logged in either way)
        else {
            Log.d(TAG, "No logged-in user detected (Guest).");
            txtName.setText("Unknown");     // e.g., "Guest"
            txtEmail.setText("Unknown"); // e.g., "N/A"
        }
    }

    private String getMsGraphToken() {
        if (getContext() == null) return null;
        SharedPreferences prefs = getContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(MS_GRAPH_TOKEN_KEY, null);
    }
    private void fetchMicrosoftProfilePhoto(String accessToken, ImageView targetImageView) {
        // Use ExecutorService for background network operation
        executorService.execute(() -> {
            HttpURLConnection urlConnection = null;
            InputStream inputStream = null;
            ByteArrayOutputStream buffer = null;
            byte[] photoData = null;
            int responseCode = -1;

            try {
                URL url = new URL("https://graph.microsoft.com/v1.0/me/photo/$value");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                // Set the Authorization header with the Bearer token
                urlConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
                urlConnection.setConnectTimeout(15000); // 15 seconds
                urlConnection.setReadTimeout(15000); // 15 seconds

                responseCode = urlConnection.getResponseCode();
                Log.d(TAG, "MS Graph API Response Code: " + responseCode);


                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = new BufferedInputStream(urlConnection.getInputStream());
                    buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[1024];
                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    photoData = buffer.toByteArray();
                    Log.d(TAG, "Successfully fetched photo data from MS Graph API.");
                } else {
                    Log.e(TAG, "Failed to fetch photo from MS Graph API. Response code: " + responseCode);
                    // Handle common errors
                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        // Token likely expired or invalid
                        clearMsGraphToken(); // Remove the bad token
                    }
                    // For 404 Not Found, it just means the user has no photo. photoData remains null.
                }

            } catch (IOException e) {
                Log.e(TAG, "IOException while fetching MS Graph photo: ", e);
                photoData = null; // Ensure photoData is null on error
            } finally {
                // Clean up resources
                if (inputStream != null) {
                    try { inputStream.close(); } catch (IOException e) { /* ignore */ }
                }
                if (buffer != null) {
                    try { buffer.close(); } catch (IOException e) { /* ignore */ }
                }
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }

            // --- Update UI on the Main Thread ---
            final byte[] finalPhotoData = photoData; // Need final variable for lambda
            final int finalResponseCode = responseCode;

            // Ensure we have activity context before trying to update UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // Cung cấp giá trị mặc định nếu không lấy được UID
                    String signatureKey = "default_user_signature";
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    if (currentUser != null && !TextUtils.isEmpty(currentUser.getUid())) {
                        // Kết hợp UID và có thể thêm yếu tố thay đổi nếu ảnh có thể cập nhật (ví dụ: timestamp?)
                        // Hiện tại chỉ dùng UID là đủ để cache giữa các session
                        signatureKey = currentUser.getUid();
                    }

                    if (finalPhotoData != null) {
                        Log.d(TAG, "Updating ImageView with fetched MS Graph photo. Signature Key: " + signatureKey);
                        Glide.with(this)
                                .load(finalPhotoData)
                                .circleCrop()
                                // *** THÊM SIGNATURE VÀO ĐÂY ***
                                .signature(new ObjectKey(signatureKey)) // Giúp Glide cache hiệu quả hơn
                                .placeholder(R.drawable.unknown_avatar)
                                .error(R.drawable.unknown_avatar)
                                .into(targetImageView);
                    } else {
                        Log.w(TAG, "Failed to get photo data (Error code: " + finalResponseCode + "), keeping default avatar.");
                        // Vẫn nên load ảnh mặc định khi lỗi, có thể thêm signature nếu muốn
                        Glide.with(this)
                                .load(R.drawable.unknown_avatar)
                                .circleCrop()
                                .signature(new ObjectKey(signatureKey + "_error")) // Signature riêng cho trạng thái lỗi nếu cần
                                .into(targetImageView);
                    }
                });
            } else {
                Log.e(TAG, "Activity is null when trying to update UI with MS Graph photo.");
            }
        });
    }


    private void clearMsGraphToken() {
        if (getContext() == null) return;
        Log.w(TAG, "Clearing potentially invalid MS Graph Token from SharedPreferences.");
        SharedPreferences prefs = getContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
        prefs.edit().remove(MS_GRAPH_TOKEN_KEY).apply();
    }

}

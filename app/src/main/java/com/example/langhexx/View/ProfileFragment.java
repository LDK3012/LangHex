package com.example.langhexx.View;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull; // Thêm import này
import androidx.annotation.Nullable; // Thêm import này
import androidx.fragment.app.Fragment;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast; // Thêm import Toast

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.langhexx.Controller.AuthController; // Import AuthController
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";
    TextView txtForum, txtName, txtEmail,txtVersion;
    Button btnLogout;
    private ImageView avatarImg ;
    private FirebaseAuth mAuth;
    private UsernamePasswordSessionManager sessionManager;
    private AuthController authController;

    private static final String MS_GRAPH_PREFS = "MSGraphPrefs";
    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token";
    private static final String MICROSOFT_PROVIDER_ID = "microsoft.com";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new UsernamePasswordSessionManager(requireContext());


        addControls(view);
        loadUserProfileInfo();
        displayCombinedVersionInfo();
        addEvents();
        return view;
    }
        // hàm update phiên bản
        private void displayCombinedVersionInfo() {
            String versionText = "Không thể lấy thông tin phiên bản";
            try {
                PackageManager pm = requireContext().getPackageManager();
                PackageInfo pi = pm.getPackageInfo(requireContext().getPackageName(), 0);
                String versionName = pi.versionName;
                long firstInstallTime = pi.firstInstallTime;
                SimpleDateFormat sdf = new SimpleDateFormat("M/yyyy", Locale.getDefault());
                String installDate = sdf.format(new Date(firstInstallTime));
                SharedPreferences prefs = requireContext().getSharedPreferences("version_info", Context.MODE_PRIVATE);
                String lastUpdateVersion = prefs.getString("current_version", null);
                String lastUpdateDate = prefs.getString("last_update_date", null);

                StringBuilder sb = new StringBuilder("Version ");
                sb.append(versionName);

                if (lastUpdateVersion != null && lastUpdateDate != null) {
                    sb.append(" (Cập nhật: ").append(lastUpdateVersion).append(" - ").append(lastUpdateDate).append(")");
                } else {
                    sb.append(" - ").append(installDate).append(" ");
                }
                versionText = sb.toString();

            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            } finally {
                txtVersion.setText(versionText);
            }
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
        txtForum = view.findViewById(R.id.txtForum);
        txtName = view.findViewById(R.id.txtName);
        txtEmail = view.findViewById(R.id.txtMail);
        avatarImg = view.findViewById(R.id.profileImg);
        btnLogout = view.findViewById(R.id.btnLogout);
        txtVersion = view.findViewById(R.id.txtVersion);
    }

    public void addEvents() {
        txtForum.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ForumActivity.class);
            String userName = txtName.getText().toString();
            intent.putExtra("USER_NAME", userName);
            if (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getPhotoUrl() != null) {
                intent.putExtra("USER_AVATAR_URL", mAuth.getCurrentUser().getPhotoUrl().toString());
            } else {
                intent.putExtra("USER_AVATAR_URL", "");
            }
            startActivity(intent);
        });
        btnLogout.setOnClickListener(v -> {
            authController = new AuthController(new AuthController.AuthCallback() {
                @Override
                public void onSuccess() {
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "Log out Successfully!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(getActivity(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        getActivity().finish();
                    }
                }

                @Override
                public void onFailure(String errorMessage) {
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "Log out failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            });
            if (getActivity() != null) {
                authController.signOut(getActivity());
            } else {
                Log.e(TAG, "Cannot sign out, activity context is null.");
                Toast.makeText(getContext(), "Error: Can't get context!", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private String extractNameFromDisplayName(String displayName, String email) {
        String nameToDisplay = null;
        if (!TextUtils.isEmpty(displayName)) {
            String[] parts = displayName.split("-");
            if (parts.length > 1 && !TextUtils.isEmpty(parts[1])) {
                nameToDisplay = parts[1].trim();
            } else {
                nameToDisplay = displayName;
            }
        }
        if (TextUtils.isEmpty(nameToDisplay) && !TextUtils.isEmpty(email)) {
            if (email.contains("@")) {
                nameToDisplay = email.substring(0, email.indexOf('@'));
            } else {
                nameToDisplay = email;
            }
        }

        if (TextUtils.isEmpty(nameToDisplay)) {
            nameToDisplay = "User";
        }
        return nameToDisplay;
    }
    private void loadUserProfileInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "Firebase user detected.");
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();
            Uri photoUrl = currentUser.getPhotoUrl();
            String nameToDisplay = extractNameFromDisplayName(displayName, email);

            txtName.setText(nameToDisplay);
            txtEmail.setText(!TextUtils.isEmpty(email) ? email : "N/A");
            boolean isMicrosoftUser = false;
            for (UserInfo profile : currentUser.getProviderData()) {
                if (MICROSOFT_PROVIDER_ID.equals(profile.getProviderId())) {
                    isMicrosoftUser = true;
                    break;
                }
            }

            if (getActivity() == null) {
                Log.e(TAG, "Activity is null during loadUserProfileInfo.");
                return;
            }

            if (isMicrosoftUser) {
                String msGraphToken = getMsGraphToken();
                if (!TextUtils.isEmpty(msGraphToken)) {
                    Log.d(TAG, "Attempting to fetch MS Graph photo.");
                    fetchMicrosoftProfilePhoto(msGraphToken, avatarImg);
                } else {
                    Log.w(TAG, "Microsoft Graph token not found. Using default avatar.");
                    loadDefaultAvatar(R.drawable.unknown_avatar); // Ảnh mặc định
                }
            } else if (photoUrl != null) {
                Log.d(TAG, "Non-Microsoft user with photo URL. Loading with Glide.");
                Glide.with(requireActivity())
                        .load(photoUrl)
                        .error(R.drawable.unknown_avatar)
                        .placeholder(R.drawable.avatar)
                        .circleCrop()
                        .into(avatarImg);
            } else {
                Log.d(TAG, "Firebase user, but no specific photo provider or null photoUrl. Loading default avatar.");
                loadDefaultAvatar(R.drawable.unknown_avatar);
            }
        }
        // Priority 2: Check Username/Password Session Manager
        else if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Username/Password session user detected.");
            String username = sessionManager.getUsername();
            txtName.setText("Leaner"); // "Learner"
            txtEmail.setText(!TextUtils.isEmpty(username) ? username : "N/A");

            if (getActivity() != null) {
                loadDefaultAvatar(R.drawable.avatar); // Ảnh mặc định cho user session
            }
        }
        // Priority 3: Guest User (Not logged in either way)
        else {
            Log.d(TAG, "No logged-in user detected (Guest).");
            txtName.setText("Guest");     // e.g., "Guest"
            txtEmail.setText("N/A"); // e.g., "N/A"
            if (getActivity() != null) {
                loadDefaultAvatar(R.drawable.unknown_avatar);
            }
        }
    }
    private void loadDefaultAvatar(int drawableId) {
        if (getActivity() == null) {
            Log.e(TAG, "Activity is null, cannot load default avatar.");
            return;
        }
        Glide.with(requireActivity())
                .load(drawableId)
                .circleCrop()
                .placeholder(R.drawable.avatar)
                .error(R.drawable.unknown_avatar)
                .into(avatarImg);
    }


    private String getMsGraphToken() {
        if (getContext() == null) return null;
        SharedPreferences prefs = requireContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(MS_GRAPH_TOKEN_KEY, null);
    }

    private void clearMsGraphToken() {
        if (getContext() == null) return;
        Log.w(TAG, "Clearing potentially invalid MS Graph Token from SharedPreferences.");
        SharedPreferences prefs = requireContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
        prefs.edit().remove(MS_GRAPH_TOKEN_KEY).apply();
    }


    private void fetchMicrosoftProfilePhoto(String accessToken, ImageView targetImageView) {
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
                Log.d(TAG, "MS Graph API Photo Response Code: " + responseCode);


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
                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(this::clearMsGraphToken);
                        }
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "IOException while fetching MS Graph photo: ", e);
                photoData = null;
            } finally {
                // Clean up resources
                if (inputStream != null) { try { inputStream.close(); } catch (IOException e) { /* ignore */ } }
                if (buffer != null) { try { buffer.close(); } catch (IOException e) { /* ignore */ } }
                if (urlConnection != null) { urlConnection.disconnect(); }
            }

            // --- Update UI on the Main Thread ---
            final byte[] finalPhotoData = photoData;
            final int finalResponseCode = responseCode;
            if (getView() != null && getActivity() != null) {
                getView().post(() -> {
                    String signatureKey = "default_user_signature";
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    if (currentUser != null && !TextUtils.isEmpty(currentUser.getUid())) {
                        signatureKey = currentUser.getUid();
                    } else if (sessionManager.isLoggedIn() && !TextUtils.isEmpty(sessionManager.getUsername())) {
                        signatureKey = sessionManager.getUsername() + "_session"; // Tạo signature cho session user
                    }


                    if (finalPhotoData != null) {
                        Log.d(TAG, "Updating ImageView with fetched MS Graph photo. Signature Key: " + signatureKey);
                        // Kiểm tra lại context trước khi gọi Glide
                        if(isAdded() && getActivity() != null) {
                            Glide.with(this)
                                    .load(finalPhotoData)
                                    .circleCrop()
                                    .signature(new ObjectKey(signatureKey))
                                    .placeholder(R.drawable.avatar)
                                    .error(R.drawable.unknown_avatar)
                                    .into(targetImageView);
                        }
                    } else {
                        Log.w(TAG, "Failed to get photo data (Error code: " + finalResponseCode + "), loading default avatar.");
                        if(isAdded() && getActivity() != null) {
                            Glide.with(this)
                                    .load(R.drawable.unknown_avatar) // Ảnh mặc định khi lỗi
                                    .circleCrop()
                                    .signature(new ObjectKey(signatureKey + "_error")) // Signature riêng cho trạng thái lỗi
                                    .placeholder(R.drawable.avatar)
                                    .error(R.drawable.unknown_avatar)
                                    .into(targetImageView);
                        }
                    }
                });
            } else {
                Log.e(TAG, "Activity or View is null when trying to update UI with MS Graph photo.");
            }
        });
    }

}

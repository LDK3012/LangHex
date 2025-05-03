package com.example.langhexx.View;

import androidx.appcompat.app.AppCompatActivity;
// Import AppCompatButton nếu bạn dùng nó trong code (nhưng thường Button là đủ)
// import androidx.appcompat.widget.AppCompatButton;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button; // Hoặc AppCompatButton nếu cần ép kiểu
import android.widget.EditText; // Sử dụng EditText
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.langhexx.Controller.AuthController;
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

public class ProfileDetail extends AppCompatActivity {

    private static final String TAG = "ProfileDetail";

    // Khai báo View theo ID trong XML mới
    private Button btnBack; // Dùng Button hoặc AppCompatButton đều được
    private Button btnLogout;
    private ImageView imgAvatar;
    private EditText edtProfileName; // Đây là EditText
    private TextView txtEmail; // Đây là label "Email"
    private EditText edtEmail; // Đây là EditText cho email
    private TextView txtNameLabel;
    private AuthController authController;
    private FirebaseAuth mAuth;
    private UsernamePasswordSessionManager sessionManager;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Constants for Microsoft Graph API
    private static final String MS_GRAPH_PREFS = "MSGraphPrefs";
    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token";
    private static final String MICROSOFT_PROVIDER_ID = "microsoft.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_detail); // Sử dụng layout bạn cung cấp

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new UsernamePasswordSessionManager(this);

        addControls();
        loadUserProfileData();
        addEvents();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            Log.d(TAG, "Shutting down ExecutorService.");
            executorService.shutdown();
        }
    }

    private void addControls() {
        btnBack = findViewById(R.id.btnBack);
        btnLogout = findViewById(R.id.btnLogout);
        imgAvatar = findViewById(R.id.imgAvatar);
        edtProfileName = findViewById(R.id.edtProfileName);
        txtEmail = findViewById(R.id.txtEmail); // Label "Email"
        edtEmail = findViewById(R.id.edtEmail); // EditText Email
        txtNameLabel = findViewById(R.id.txtDisplay);
        // --- Vô hiệu hóa chỉnh sửa cho EditText ---
        edtProfileName.setEnabled(false);
        edtEmail.setEnabled(false);
    }

    private void addEvents() {
        btnBack.setOnClickListener(view -> finish()); // Lambda cho ngắn gọn

        btnLogout.setOnClickListener(v -> {
            authController = new AuthController(new AuthController.AuthCallback() {
                @Override
                public void onSuccess() {
                    Toast.makeText(ProfileDetail.this, "Đăng xuất thành công!", Toast.LENGTH_SHORT).show();
                    // Chuyển về màn hình Login hoặc màn hình chính nào đó
                    // Intent intent = new Intent(ProfileDetail.this, LoginActivity.class);
                    // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    // startActivity(intent);
                    finish(); // Đóng màn hình này
                }

                @Override
                public void onFailure(String errorMessage) {
                    Toast.makeText(ProfileDetail.this, "Đăng xuất thất bại: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
            authController.signOut(ProfileDetail.this);
        });

        // Có thể thêm sự kiện cho imgPlus nếu muốn (ví dụ: chọn ảnh mới)
        // ImageView imgPlus = findViewById(R.id.imgPlus);
        // imgPlus.setOnClickListener(v -> { /* Xử lý chọn ảnh */ });
    }

    private void loadUserProfileData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Ưu tiên 1: Người dùng Firebase (Google, Microsoft, ...)
        if (currentUser != null) {
            Log.d(TAG, "Firebase user detected.");
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();
            Uri photoUrl = currentUser.getPhotoUrl();
            String nameToDisplay = extractNameFromDisplayName(displayName, email);

            edtProfileName.setText(nameToDisplay);
            edtEmail.setText(!TextUtils.isEmpty(email) ? email : "N/A");

            // Hiển thị trường Email và label của nó
            edtEmail.setVisibility(View.VISIBLE);
            txtEmail.setVisibility(View.VISIBLE); // Hiển thị label "Email"

            boolean isMicrosoftUser = false;
            for (UserInfo profile : currentUser.getProviderData()) {
                if (MICROSOFT_PROVIDER_ID.equals(profile.getProviderId())) {
                    isMicrosoftUser = true;
                    break;
                }
            }

            if (isMicrosoftUser) {
                String msGraphToken = getMsGraphToken();
                if (!TextUtils.isEmpty(msGraphToken)) {
                    Log.d(TAG, "Attempting to fetch Microsoft profile photo.");
                    fetchMicrosoftProfilePhoto(msGraphToken, imgAvatar);
                } else {
                    Log.w(TAG, "Microsoft Graph token not found. Using default avatar.");
                    loadDefaultAvatar(R.drawable.unknown_avatar);
                }
            } else if (photoUrl != null) {
                Log.d(TAG, "Non-Microsoft user with photo URL. Loading with Glide.");
                Glide.with(this)
                        .load(photoUrl)
                        .error(R.drawable.unknown_avatar) // Ảnh nếu lỗi
                        .placeholder(R.drawable.avatar) // Ảnh khi đang tải (dùng avatar làm placeholder)
                        .circleCrop() // Bo tròn ảnh
                        .into(imgAvatar);
            } else {
                Log.d(TAG, "Firebase user, but no specific photo provider or null photoUrl.");
                loadDefaultAvatar(R.drawable.unknown_avatar); // Ảnh mặc định chung
            }
        }
        // Ưu tiên 2: Người dùng đăng nhập bằng Username/Password
        else if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Username/Password session user detected.");
            txtNameLabel.setText("MSSV");
            String username = sessionManager.getUsername();

            // Theo yêu cầu: tên là username, không có email, avatar mặc định
            edtProfileName.setText(!TextUtils.isEmpty(username) ? username : "Learner"); // Hiển thị username
            edtEmail.setText(""); // Xóa nội dung email

            // Ẩn trường EditText Email và label của nó
            edtEmail.setVisibility(View.GONE);
            txtEmail.setVisibility(View.GONE); // Ẩn label "Email"

            Log.d(TAG, "Loading specific default avatar for session user.");
            loadDefaultAvatar(R.drawable.avatar); // Ảnh mặc định cho user session

        }
        // Ưu tiên 3: Khách (Không đăng nhập)
        else {
            Log.d(TAG, "No logged-in user detected (Guest).");
            edtProfileName.setText("Guest"); // Tên mặc định cho khách
            edtEmail.setText("N/A");

            // Hiển thị trường Email (với giá trị N/A) và label
            edtEmail.setVisibility(View.VISIBLE);
            txtEmail.setVisibility(View.VISIBLE);

            loadDefaultAvatar(R.drawable.unknown_avatar); // Ảnh mặc định chung
        }
    }

    // Tải ảnh mặc định bằng Glide
    private void loadDefaultAvatar(int drawableId) {
        Glide.with(this)
                .load(drawableId)
                .circleCrop()
                .placeholder(R.drawable.avatar) // Có thể thêm placeholder ở đây nữa
                .error(R.drawable.unknown_avatar) // Ảnh nếu lỗi khi load drawable (ít xảy ra)
                .into(imgAvatar);
    }

    // *** CÁC PHƯƠNG THỨC HELPER (Giữ nguyên từ trước) ***

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

    private String getMsGraphToken() {
        SharedPreferences prefs = getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(MS_GRAPH_TOKEN_KEY, null);
    }

    private void clearMsGraphToken() {
        Log.w(TAG, "Clearing potentially invalid MS Graph Token from SharedPreferences.");
        SharedPreferences prefs = getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
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
                urlConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(15000);

                responseCode = urlConnection.getResponseCode();
                Log.d(TAG, "MS Graph API Photo Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = new BufferedInputStream(urlConnection.getInputStream());
                    buffer = new ByteArrayOutputStream();
                    byte[] data = new byte[1024];
                    int nRead;
                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    photoData = buffer.toByteArray();
                    Log.d(TAG, "Successfully fetched photo data from MS Graph API.");
                } else {
                    Log.e(TAG, "Failed to fetch photo from MS Graph API. Response code: " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        clearMsGraphToken();
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException while fetching MS Graph photo: ", e);
                photoData = null;
            } finally {
                if (inputStream != null) try { inputStream.close(); } catch (IOException e) { /* ignore */ }
                if (buffer != null) try { buffer.close(); } catch (IOException e) { /* ignore */ }
                if (urlConnection != null) urlConnection.disconnect();
            }

            final byte[] finalPhotoData = photoData;
            final int finalResponseCode = responseCode;

            runOnUiThread(() -> {
                String signatureKey = "default_user_signature";
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null && !TextUtils.isEmpty(currentUser.getUid())) {
                    signatureKey = currentUser.getUid();
                } else if (sessionManager.isLoggedIn() && !TextUtils.isEmpty(sessionManager.getUsername())) {
                    signatureKey = sessionManager.getUsername() + "_session";
                }

                if (finalPhotoData != null) {
                    Log.d(TAG, "Updating ImageView with fetched MS Graph photo. Signature: " + signatureKey);
                    Glide.with(this)
                            .load(finalPhotoData)
                            .circleCrop()
                            .signature(new ObjectKey(signatureKey))
                            .placeholder(R.drawable.avatar) // Placeholder
                            .error(R.drawable.unknown_avatar) // Ảnh lỗi
                            .into(targetImageView);
                } else {
                    Log.w(TAG, "Failed to get photo data (Error code: " + finalResponseCode + "), loading default avatar.");
                    Glide.with(this)
                            .load(R.drawable.unknown_avatar) // Ảnh mặc định khi lỗi
                            .circleCrop()
                            .signature(new ObjectKey(signatureKey + "_error"))
                            .placeholder(R.drawable.avatar) // Placeholder
                            .error(R.drawable.unknown_avatar) // Double check error drawable
                            .into(targetImageView);
                }
            });
        });
    }
}
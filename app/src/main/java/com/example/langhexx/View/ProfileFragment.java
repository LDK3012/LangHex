//package com.example.langhexx.View;
//
//import android.app.AlertDialog;
//import android.content.Context;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.net.Uri;
//import android.os.Bundle;
//
//import androidx.fragment.app.Fragment;
//
//import android.text.TextUtils;
//import android.util.Log;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.ImageView;
//import android.widget.TextView;
//
//import com.bumptech.glide.Glide;
//import com.bumptech.glide.signature.ObjectKey;
//import com.example.langhexx.Model.UsernamePasswordSessionManager;
//import com.example.langhexx.R;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseUser;
//import com.google.firebase.auth.UserInfo;
//
//import java.io.BufferedInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class ProfileFragment extends Fragment {
//    private static final String TAG = "ProfileFragment";
//    TextView txtProfileDetail, txtFeedback, txtName, txtEmail, txtLanguage;
//    private ImageView avatarImg ;
//    private FirebaseAuth mAuth;
//    private UsernamePasswordSessionManager sessionManager;
//    //
//    private static final String MS_GRAPH_PREFS = "MSGraphPrefs"; // SharedPreferences name
//    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token"; // Key for the token
//    private static final String MICROSOFT_PROVIDER_ID = "microsoft.com"; // Common Provider ID for Microsoft
//    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // Create single thread executor
//    @Override
//    public View onCreateView(LayoutInflater inflater, ViewGroup container,
//                             Bundle savedInstanceState) {
//        View view = inflater.inflate(R.layout.fragment_profile, container, false);
//        //
//        mAuth = FirebaseAuth.getInstance();
//        sessionManager = new UsernamePasswordSessionManager(getContext());
//        //
//        addControls(view);
//        loadUserProfileInfo();
//        addEvents();
//        return view;
//    }
//
//    @Override
//    public void onDestroyView() {
//        super.onDestroyView();
//        if (executorService != null && !executorService.isShutdown()) {
//            Log.d(TAG, "Shutting down ExecutorService.");
//            executorService.shutdown();
//        }
//    }
//
//    public void addControls(View view) {
//        txtProfileDetail = view.findViewById(R.id.txtInfo);
//        txtFeedback = view.findViewById(R.id.txtFeedback);
//        txtName = view.findViewById(R.id.txtName);
//        txtEmail = view.findViewById(R.id.txtMail);
//        txtLanguage = view.findViewById(R.id.txtLanguage);
//        avatarImg = view.findViewById(R.id.profileImg) ;
//
//    }
//
//    public void addEvents() {
//        txtProfileDetail.setOnClickListener(v -> {
//            Intent intent = new Intent(getActivity(), ProfileDetail.class);
//            startActivity(intent);
//        });
//
//        txtFeedback.setOnClickListener(v -> {
//            Intent intent = new Intent(getActivity(), FeedbackActivity.class);
//            startActivity(intent);
//        });
//
////        txtLanguage.setOnClickListener(v -> {
////            showLanguageDialog();
////        });
//    }
//
//    // Helper method copied/adapted from HomeFragment to extract name
//    private String extractNameFromDisplayName(String displayName, String email) {
//        String nameToDisplay = null;
//        // Process displayName to get name part (assuming "Something - Name" format)
//        if (!TextUtils.isEmpty(displayName)) {
//            String[] parts = displayName.split("-");
//            if (parts.length > 1 && !TextUtils.isEmpty(parts[1])) {
//                nameToDisplay = parts[1].trim(); // Get the second part (name)
//            } else {
//                nameToDisplay = displayName; // Fallback to full display name if format is different
//            }
//        }
//
//        // Fallback to email if name couldn't be extracted from displayName
//        if (TextUtils.isEmpty(nameToDisplay) && !TextUtils.isEmpty(email)) {
//            nameToDisplay = email;
//        }
//
//        // Final fallback to a generic name if both are empty
//        if (TextUtils.isEmpty(nameToDisplay)) {
//            // Use a string resource for the default name
//            nameToDisplay = "Unknown"; // e.g., "User" or "Người dùng"
//        }
//        return nameToDisplay;
//    }
//
//    private void loadUserProfileInfo() {
//        FirebaseUser currentUser = mAuth.getCurrentUser();
//
//        // Priority 1: Check Firebase User (Includes Microsoft, Google, etc.)
//        if (currentUser != null) {
//            Log.d(TAG, "Firebase user detected.");
//            String displayName = currentUser.getDisplayName();
//            String email = currentUser.getEmail();
//            Uri photoUrl = currentUser.getPhotoUrl();
//            String nameToDisplay = extractNameFromDisplayName(displayName, email); // Use helper
//
//            txtName.setText(nameToDisplay);
//            txtEmail.setText(!TextUtils.isEmpty(email) ? email : "Unknown");
//            //
//            // Check if user is signed in with Microsoft
//            boolean isMicrosoftUser = false;
//            for (UserInfo profile : currentUser.getProviderData()) {
//                if (MICROSOFT_PROVIDER_ID.equals(profile.getProviderId())) {
//                    isMicrosoftUser = true;
//                    break;
//                }
//            }
//
//            if (isMicrosoftUser) {
//                // Microsoft user: Try to load photo from Graph API using stored token
//                String msGraphToken = getMsGraphToken();
//                if (!TextUtils.isEmpty(msGraphToken)) {
//                    fetchMicrosoftProfilePhoto(msGraphToken, avatarImg);
//                } else {
//                    Log.w(TAG, "Microsoft Graph token not found in SharedPreferences.");
//                }
//            } else if (photoUrl != null && getActivity() != null) {
//                // Non-Microsoft user with a photo URL (e.g., Google)
//                Log.d(TAG, "Non-Microsoft user with photo URL. Loading with Glide.");
//                Glide.with(requireActivity())
//                        .load(photoUrl)
//                        .error(R.drawable.unknown_avatar)
//                        .placeholder(R.drawable.unknown_avatar)
//                        .circleCrop()
//                        .into(avatarImg);
//            } else {
//                Log.d(TAG, "User logged in, but no specific photo provider handled (or photoUrl is null). Default avatar remains.");
//                // Keep the default avatar if no specific handling applies or photoUrl is null
//            }
//        }
//        // Priority 2: Check Username/Password Session Manager
//        else if (sessionManager.isLoggedIn()) {
//            Log.d(TAG, "Username/Password session user detected.");
//            String username = sessionManager.getUsername();
//            // As requested: Set name to "Learner" and email to the stored username
//            txtName.setText("Leaner"); // "Learner"
//            txtEmail.setText(!TextUtils.isEmpty(username) ? username : "Unknown");
//            if (getActivity() != null) { // Ensure context is available for resource
//                avatarImg.setImageResource(R.drawable.avatar); // Specific default for session users
//            }
//        }
//        // Priority 3: Guest User (Not logged in either way)
//        else {
//            Log.d(TAG, "No logged-in user detected (Guest).");
//            txtName.setText("Unknown");     // e.g., "Guest"
//            txtEmail.setText("Unknown"); // e.g., "N/A"
//        }
//    }
//
//    private String getMsGraphToken() {
//        if (getContext() == null) return null;
//        SharedPreferences prefs = getContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
//        return prefs.getString(MS_GRAPH_TOKEN_KEY, null);
//    }
//    private void fetchMicrosoftProfilePhoto(String accessToken, ImageView targetImageView) {
//        // Use ExecutorService for background network operation
//        executorService.execute(() -> {
//            HttpURLConnection urlConnection = null;
//            InputStream inputStream = null;
//            ByteArrayOutputStream buffer = null;
//            byte[] photoData = null;
//            int responseCode = -1;
//
//            try {
//                URL url = new URL("https://graph.microsoft.com/v1.0/me/photo/$value");
//                urlConnection = (HttpURLConnection) url.openConnection();
//                urlConnection.setRequestMethod("GET");
//                // Set the Authorization header with the Bearer token
//                urlConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
//                urlConnection.setConnectTimeout(15000); // 15 seconds
//                urlConnection.setReadTimeout(15000); // 15 seconds
//
//                responseCode = urlConnection.getResponseCode();
//                Log.d(TAG, "MS Graph API Response Code: " + responseCode);
//
//
//                if (responseCode == HttpURLConnection.HTTP_OK) {
//                    inputStream = new BufferedInputStream(urlConnection.getInputStream());
//                    buffer = new ByteArrayOutputStream();
//                    int nRead;
//                    byte[] data = new byte[1024];
//                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
//                        buffer.write(data, 0, nRead);
//                    }
//                    buffer.flush();
//                    photoData = buffer.toByteArray();
//                    Log.d(TAG, "Successfully fetched photo data from MS Graph API.");
//                } else {
//                    Log.e(TAG, "Failed to fetch photo from MS Graph API. Response code: " + responseCode);
//                    // Handle common errors
//                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
//                        // Token likely expired or invalid
//                        clearMsGraphToken(); // Remove the bad token
//                    }
//                    // For 404 Not Found, it just means the user has no photo. photoData remains null.
//                }
//
//            } catch (IOException e) {
//                Log.e(TAG, "IOException while fetching MS Graph photo: ", e);
//                photoData = null; // Ensure photoData is null on error
//            } finally {
//                // Clean up resources
//                if (inputStream != null) {
//                    try { inputStream.close(); } catch (IOException e) { /* ignore */ }
//                }
//                if (buffer != null) {
//                    try { buffer.close(); } catch (IOException e) { /* ignore */ }
//                }
//                if (urlConnection != null) {
//                    urlConnection.disconnect();
//                }
//            }
//
//            // --- Update UI on the Main Thread ---
//            final byte[] finalPhotoData = photoData; // Need final variable for lambda
//            final int finalResponseCode = responseCode;
//
//            // Ensure we have activity context before trying to update UI
//            if (getActivity() != null) {
//                getActivity().runOnUiThread(() -> {
//                    // Cung cấp giá trị mặc định nếu không lấy được UID
//                    String signatureKey = "default_user_signature";
//                    FirebaseUser currentUser = mAuth.getCurrentUser();
//                    if (currentUser != null && !TextUtils.isEmpty(currentUser.getUid())) {
//                        // Kết hợp UID và có thể thêm yếu tố thay đổi nếu ảnh có thể cập nhật (ví dụ: timestamp?)
//                        // Hiện tại chỉ dùng UID là đủ để cache giữa các session
//                        signatureKey = currentUser.getUid();
//                    }
//
//                    if (finalPhotoData != null) {
//                        Log.d(TAG, "Updating ImageView with fetched MS Graph photo. Signature Key: " + signatureKey);
//                        Glide.with(this)
//                                .load(finalPhotoData)
//                                .circleCrop()
//                                // *** THÊM SIGNATURE VÀO ĐÂY ***
//                                .signature(new ObjectKey(signatureKey)) // Giúp Glide cache hiệu quả hơn
//                                .placeholder(R.drawable.unknown_avatar)
//                                .error(R.drawable.unknown_avatar)
//                                .into(targetImageView);
//                    } else {
//                        Log.w(TAG, "Failed to get photo data (Error code: " + finalResponseCode + "), keeping default avatar.");
//                        // Vẫn nên load ảnh mặc định khi lỗi, có thể thêm signature nếu muốn
//                        Glide.with(this)
//                                .load(R.drawable.unknown_avatar)
//                                .circleCrop()
//                                .signature(new ObjectKey(signatureKey + "_error")) // Signature riêng cho trạng thái lỗi nếu cần
//                                .into(targetImageView);
//                    }
//                });
//            } else {
//                Log.e(TAG, "Activity is null when trying to update UI with MS Graph photo.");
//            }
//        });
//    }
//
//
//    private void clearMsGraphToken() {
//        if (getContext() == null) return;
//        Log.w(TAG, "Clearing potentially invalid MS Graph Token from SharedPreferences.");
//        SharedPreferences prefs = getContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
//        prefs.edit().remove(MS_GRAPH_TOKEN_KEY).apply();
//    }
//
//}

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
    TextView txtProfileDetail, txtForum, txtName, txtEmail, txtLanguage,txtVersion;
    private ImageView avatarImg ;
    private TextView txtLogout ;
    private FirebaseAuth mAuth;
    private UsernamePasswordSessionManager sessionManager;
    private AuthController authController; // *** THÊM BIẾN AUTHCONTROLLER ***


    // Constants for Microsoft Graph API
    private static final String MS_GRAPH_PREFS = "MSGraphPrefs";
    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token";
    private static final String MICROSOFT_PROVIDER_ID = "microsoft.com";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Nullable // Sử dụng @Nullable vì onCreateView có thể trả về null
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        mAuth = FirebaseAuth.getInstance();
        // Sử dụng requireContext() để đảm bảo Context không null sau khi Fragment được attach
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
                // Cách 1: Lấy thông tin từ BuildConfig (chỉ có versionName mặc định)
                PackageManager pm = requireContext().getPackageManager();
                PackageInfo pi = pm.getPackageInfo(requireContext().getPackageName(), 0);
                String versionName = pi.versionName;

                // Cách 2: Lấy ngày cài đặt đầu tiên
                long firstInstallTime = pi.firstInstallTime;
                SimpleDateFormat sdf = new SimpleDateFormat("M/yyyy", Locale.getDefault());
                String installDate = sdf.format(new Date(firstInstallTime));

                // Cách 3: Lấy thông tin cập nhật (nếu đã lưu bằng SharedPreferences)
                SharedPreferences prefs = requireContext().getSharedPreferences("version_info", Context.MODE_PRIVATE);
                String lastUpdateVersion = prefs.getString("current_version", null);
                String lastUpdateDate = prefs.getString("last_update_date", null);

                StringBuilder sb = new StringBuilder("Phiên bản ");
                sb.append(versionName);

                if (lastUpdateVersion != null && lastUpdateDate != null) {
                    sb.append(" (Cập nhật: ").append(lastUpdateVersion).append(" - ").append(lastUpdateDate).append(")");
                } else {
                    sb.append(" - ").append(installDate).append(" ");
                }
                versionText = sb.toString();

            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                // Xử lý lỗi nếu không tìm thấy thông tin gói
            } finally {
                // Hiển thị thông tin lên TextView
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
        txtProfileDetail = view.findViewById(R.id.txtInfo);
        txtForum = view.findViewById(R.id.txtForum);
        txtName = view.findViewById(R.id.txtName);
        txtEmail = view.findViewById(R.id.txtMail);
        avatarImg = view.findViewById(R.id.profileImg);
        txtLogout = view.findViewById(R.id.txtLogout) ;
        txtVersion = view.findViewById(R.id.txtVersion); //update phiên bản
    }

    public void addEvents() {
        txtProfileDetail.setOnClickListener(v -> {
            // Sử dụng getActivity() hoặc requireActivity() để lấy Context cho Intent
            Intent intent = new Intent(getActivity(), ProfileDetail.class);
            startActivity(intent);
        });

        txtForum.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ForumActivity.class);
            startActivity(intent);
        });

        //        txtLanguage.setOnClickListener(v -> {
        //            showLanguageDialog();
        //        });

        // *** THÊM SỰ KIỆN CLICK CHO NÚT LOGOUT ***
        txtLogout.setOnClickListener(v -> {
            // Khởi tạo AuthController và thực hiện đăng xuất
            authController = new AuthController(new AuthController.AuthCallback() {
                @Override
                public void onSuccess() {
                    // Sử dụng getActivity() hoặc requireContext() để lấy Context
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "Đăng xuất thành công!", Toast.LENGTH_SHORT).show();
                        // Chuyển về màn hình Login và xóa các activity trước đó
                        Intent intent = new Intent(getActivity(), LoginActivity.class); // Giả sử bạn có LoginActivity
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        getActivity().finish(); // Đóng Activity chứa Fragment này
                    }
                }

                @Override
                public void onFailure(String errorMessage) {
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "Đăng xuất thất bại: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            });
            // Sử dụng getActivity() hoặc requireContext()
            if (getActivity() != null) {
                authController.signOut(getActivity());
            } else {
                Log.e(TAG, "Cannot sign out, activity context is null.");
                Toast.makeText(getContext(), "Lỗi: Không thể lấy context để đăng xuất.", Toast.LENGTH_SHORT).show();
            }
        });

    }

    // --- Các phương thức loadUserProfileInfo, extractNameFromDisplayName, fetchMicrosoftProfilePhoto, getMsGraphToken, clearMsGraphToken giữ nguyên ---
    // ... (Copy các phương thức đó vào đây nếu chúng chưa có) ...
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
            if (email.contains("@")) {
                nameToDisplay = email.substring(0, email.indexOf('@'));
            } else {
                nameToDisplay = email;
            }
        }

        // Final fallback to a generic name if both are empty
        if (TextUtils.isEmpty(nameToDisplay)) {
            // Use a string resource for the default name
            nameToDisplay = "User"; // e.g., "User" or "Người dùng"
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
            txtEmail.setText(!TextUtils.isEmpty(email) ? email : "N/A"); // Sử dụng N/A nếu email trống
            //
            // Check if user is signed in with Microsoft
            boolean isMicrosoftUser = false;
            for (UserInfo profile : currentUser.getProviderData()) {
                if (MICROSOFT_PROVIDER_ID.equals(profile.getProviderId())) {
                    isMicrosoftUser = true;
                    break;
                }
            }

            // Sử dụng requireActivity() hoặc getActivity() để đảm bảo context tồn tại
            if (getActivity() == null) {
                Log.e(TAG, "Activity is null during loadUserProfileInfo.");
                return; // Không thể load ảnh nếu không có context
            }

            if (isMicrosoftUser) {
                // Microsoft user: Try to load photo from Graph API using stored token
                String msGraphToken = getMsGraphToken();
                if (!TextUtils.isEmpty(msGraphToken)) {
                    Log.d(TAG, "Attempting to fetch MS Graph photo.");
                    fetchMicrosoftProfilePhoto(msGraphToken, avatarImg);
                } else {
                    Log.w(TAG, "Microsoft Graph token not found. Using default avatar.");
                    loadDefaultAvatar(R.drawable.unknown_avatar); // Ảnh mặc định
                }
            } else if (photoUrl != null) {
                // Non-Microsoft user with a photo URL (e.g., Google)
                Log.d(TAG, "Non-Microsoft user with photo URL. Loading with Glide.");
                Glide.with(requireActivity()) // Sử dụng requireActivity()
                        .load(photoUrl)
                        .error(R.drawable.unknown_avatar)
                        .placeholder(R.drawable.avatar) // Placeholder
                        .circleCrop()
                        .into(avatarImg);
            } else {
                Log.d(TAG, "Firebase user, but no specific photo provider or null photoUrl. Loading default avatar.");
                loadDefaultAvatar(R.drawable.unknown_avatar); // Ảnh mặc định chung
            }
        }
        // Priority 2: Check Username/Password Session Manager
        else if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Username/Password session user detected.");
            String username = sessionManager.getUsername();
            // As requested: Set name to "Learner" and email to the stored username
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
                loadDefaultAvatar(R.drawable.unknown_avatar); // Ảnh mặc định chung
            }
        }
    }

    // Tải ảnh mặc định bằng Glide (Thêm phương thức này vào Fragment)
    private void loadDefaultAvatar(int drawableId) {
        if (getActivity() == null) {
            Log.e(TAG, "Activity is null, cannot load default avatar.");
            return;
        }
        Glide.with(requireActivity()) // Dùng requireActivity()
                .load(drawableId)
                .circleCrop()
                .placeholder(R.drawable.avatar) // Placeholder
                .error(R.drawable.unknown_avatar) // Ảnh nếu lỗi
                .into(avatarImg);
    }


    private String getMsGraphToken() {
        // Sử dụng requireContext() an toàn hơn getContext() trực tiếp
        if (getContext() == null) return null; // Kiểm tra nếu context bị null (dù ít xảy ra với requireContext)
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
                    // Handle common errors
                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        // Token likely expired or invalid
                        // Chạy trên UI thread nếu cần tương tác với SharedPreferences từ đây
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(this::clearMsGraphToken);
                        }
                    }
                    // For 404 Not Found, it just means the user has no photo. photoData remains null.
                }

            } catch (IOException e) {
                Log.e(TAG, "IOException while fetching MS Graph photo: ", e);
                photoData = null; // Ensure photoData is null on error
            } finally {
                // Clean up resources
                if (inputStream != null) { try { inputStream.close(); } catch (IOException e) { /* ignore */ } }
                if (buffer != null) { try { buffer.close(); } catch (IOException e) { /* ignore */ } }
                if (urlConnection != null) { urlConnection.disconnect(); }
            }

            // --- Update UI on the Main Thread ---
            final byte[] finalPhotoData = photoData; // Need final variable for lambda
            final int finalResponseCode = responseCode;

            // Ensure we have activity context before trying to update UI
            // Sử dụng post hoặc runOnUiThread để đảm bảo chạy trên main thread
            if (getView() != null && getActivity() != null) { // Kiểm tra cả view và activity
                getView().post(() -> { // Sử dụng post của View
                    // Cung cấp giá trị mặc định nếu không lấy được UID
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
                        if(isAdded() && getActivity() != null) { // isAdded() kiểm tra fragment còn được attach không
                            Glide.with(this) // 'this' là Fragment
                                    .load(finalPhotoData)
                                    .circleCrop()
                                    .signature(new ObjectKey(signatureKey)) // Giúp Glide cache hiệu quả hơn
                                    .placeholder(R.drawable.avatar) // Placeholder
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
                                    .error(R.drawable.unknown_avatar) // Double check error drawable
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

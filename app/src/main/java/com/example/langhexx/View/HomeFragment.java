package com.example.langhexx.View;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.langhexx.Controller.NotificationAdapter;
import com.example.langhexx.Model.Levels;
import com.example.langhexx.Model.UsernamePasswordSessionManager;
import com.example.langhexx.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.bumptech.glide.signature.ObjectKey;

public class HomeFragment extends Fragment {
    private ListView lstLevel;
    private Button btnNotifiy;
    private TextView txtName;
    private ImageView imgAvatar;
    private ArrayList<Levels> levelsList;
    private LevelsAdapter levelsAdapter;
    private FirebaseAuth mAuth ;
    private UsernamePasswordSessionManager sessionManager;

    private static final String MS_GRAPH_PREFS = "MSGraphPrefs"; // SharedPreferences name
    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token"; // Key for the token
    private static final String MICROSOFT_PROVIDER_ID = "microsoft.com"; // Common Provider ID for Microsoft

    // Executor for background tasks
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // Create single thread executor

    private static final String TAG = "HomeFragment";
    public HomeFragment(){

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_fragement, container, false);
        mAuth = FirebaseAuth.getInstance() ;
        sessionManager = new UsernamePasswordSessionManager(getContext()) ;
        addControls(view);
        loadUserProfile(); // Load profile info
        addEvents();
        setUpListView();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }


    public void addControls(View view){
        lstLevel = view.findViewById(R.id.lstLevels);
        btnNotifiy = view.findViewById(R.id.btnNotifications);
        txtName = view.findViewById(R.id.txtName);
        imgAvatar = view.findViewById(R.id.imgAvatar);
    }

    private void setUpListView(){
        levelsList = new ArrayList<>();
        levelsList.add(new Levels("General English - Vstep", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A1", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A2", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A3", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A4", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A5", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A6", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A7", String.valueOf(R.drawable.example)));

        // Ensure context is valid before creating adapter
        if (getActivity() != null) {
            levelsAdapter = new LevelsAdapter(getActivity(), R.layout.custom_levels_lst, levelsList);
            lstLevel.setAdapter(levelsAdapter);
            lstLevel.setOnItemClickListener((parent, view, position, id) -> {
                String levelName = levelsList.get(position).getTxtLevels();
                Intent intent;
                if ("General English - Vstep".equals(levelName)) {
                    intent = new Intent(getActivity(), VstepActivity.class);
                } else {
                    intent = new Intent(getActivity(), LearningTypeActivity.class);
                    intent.putExtra("levelName", levelsList.get(position).getTxtLevels());
                }
                startActivity(intent);
            });
        } else {
            Log.e(TAG, "Activity is null in setUpListView, cannot create adapter.");
        }
    }

    private void addEvents(){
        btnNotifiy.setOnClickListener(v -> showNotificationDialog());
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        // Priority 1: Check Firebase User
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();
            Uri photoUrl = currentUser.getPhotoUrl(); // Will be null for Microsoft
            String nameToDisplay = extractNameFromDisplayName(displayName, email);

            txtName.setText(nameToDisplay);

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
                    fetchMicrosoftProfilePhoto(msGraphToken, imgAvatar);
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
                        .into(imgAvatar);
            } else {
                Log.d(TAG, "User logged in, but no specific photo provider handled (or photoUrl is null). Default avatar remains.");
            }

        } else if (sessionManager.isLoggedIn()) {
            // Priority 2: Handle Username/Password Session
            Log.d(TAG, "User logged in via Session Manager.");
            String username = sessionManager.getUsername();
            txtName.setText(!TextUtils.isEmpty(username) ? username : "Người dùng");
            if (getActivity() != null) { // Ensure context is available for resource
                imgAvatar.setImageResource(R.drawable.avatar); // Specific default for session users
            }

        } else {
            // Priority 3: Guest User
            Log.d(TAG, "No user logged in (Guest).");
            txtName.setText("Khách");
            if (getActivity() != null) { // Ensure context is available for resource
                imgAvatar.setImageResource(R.drawable.avatar); // Specific default for guests
            }
        }
    }

    // Helper to extract name, prioritizing DisplayName format then email
    private String extractNameFromDisplayName(String displayName, String email) {
        String nameToDisplay = null;
        // Process displayName to get name part
        if (!TextUtils.isEmpty(displayName)) {
            String[] parts = displayName.split("-");
            if (parts.length > 1 && !TextUtils.isEmpty(parts[1])) {
                nameToDisplay = parts[1].trim();
            } else {
                nameToDisplay = displayName;
            }
        }

        // Fallback to email if name couldn't be extracted
        if (TextUtils.isEmpty(nameToDisplay) && !TextUtils.isEmpty(email)) {
            nameToDisplay = email;
        }

        // Final fallback
        if (TextUtils.isEmpty(nameToDisplay)) {
            nameToDisplay = "Người dùng";
        }
        return nameToDisplay;
    }


    // --- Microsoft Graph API Photo Fetching Logic ---

    private String getMsGraphToken() {
        if (getContext() == null) return null;
        SharedPreferences prefs = getContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(MS_GRAPH_TOKEN_KEY, null);
    }

    // Method to clear the potentially invalid token
    private void clearMsGraphToken() {
        if (getContext() == null) return ;
        Log.w(TAG, "Clearing potentially invalid MS Graph Token from SharedPreferences.");
        SharedPreferences prefs = getContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
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

    // --- End Microsoft Graph API Logic ---


    private void showNotificationDialog() {
        if (getContext() == null || getActivity() == null) {
            Log.e(TAG, "Cannot show notification dialog, context or activity is null.");
            return; // Prevent crash if context is not available
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        // Ensure the layout resource name is correct
        View dialogView = inflater.inflate(R.layout.notification_dialog, null);

        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerNotifications);
        TextView txtEmpty = dialogView.findViewById(R.id.txtEmptyNotifications);
        Button btnClose = dialogView.findViewById(R.id.btnCloseDialog);

        ArrayList<String> notifications = new ArrayList<>();
        notifications.add("Chào mừng đến với ứng dụng LangHexx!");
        // notifications.clear(); // Test empty state

        if (notifications.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            txtEmpty.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            txtEmpty.setVisibility(View.GONE);

            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            NotificationAdapter notificationAdapter = new NotificationAdapter(notifications);
            recyclerView.setAdapter(notificationAdapter);
        }

        builder.setView(dialogView); // Set the custom view
        AlertDialog dialog = builder.create(); // Create the dialog

        btnClose.setOnClickListener(v -> dialog.dismiss()); // Set close action

        dialog.show(); // Show the dialog
    }
}
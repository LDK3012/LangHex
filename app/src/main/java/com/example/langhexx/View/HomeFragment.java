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
import com.bumptech.glide.signature.ObjectKey; // Đảm bảo import này đúng
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

public class HomeFragment extends Fragment {
    private ListView lstLevel;
    private Button btnNotifiy;
    private TextView txtName;
    private ImageView imgAvatar;
    private ArrayList<Levels> levelsList;
    private LevelsAdapter levelsAdapter;
    private FirebaseAuth mAuth ;
    private UsernamePasswordSessionManager sessionManager;

    private static final String MS_GRAPH_PREFS = "MSGraphPrefs";
    private static final String MS_GRAPH_TOKEN_KEY = "ms_graph_token";
    private static final String MICROSOFT_PROVIDER_ID = "microsoft.com";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private static final String TAG = "HomeFragment";

    public HomeFragment(){
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_fragement, container, false);
        mAuth = FirebaseAuth.getInstance();
        sessionManager = new UsernamePasswordSessionManager(getContext());
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
        // ... (các levels khác)

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

        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();
            String nameToDisplay = extractNameFromDisplayName(displayName, email);
            txtName.setText(nameToDisplay);

            boolean isMicrosoftUser = false;
            for (UserInfo profile : currentUser.getProviderData()) {
                if (MICROSOFT_PROVIDER_ID.equals(profile.getProviderId())) {
                    isMicrosoftUser = true;
                    break;
                }
            }

            if (isMicrosoftUser) {
                String msGraphToken = getMsGraphToken();
                // Luôn đặt ảnh placeholder ban đầu cho người dùng Microsoft
                if (getContext() != null) {
                    Glide.with(requireContext())
                            .load(R.drawable.unknown_avatar) // Ảnh mặc định/placeholder
                            .circleCrop()
                            .placeholder(R.drawable.unknown_avatar)
                            .into(imgAvatar);
                }

                if (!TextUtils.isEmpty(msGraphToken)) {
                    fetchMicrosoftProfilePhoto(msGraphToken, imgAvatar);
                } else {
                    Log.w(TAG, "Microsoft Graph token not found. Avatar will remain default.");
                    // Ảnh mặc định đã được đặt ở trên, không cần làm gì thêm ở đây
                    // nếu không muốn ghi đè lại bằng Glide một lần nữa.
                }
            } else { // Non-Microsoft Firebase user (e.g., Google, Email/Password from Firebase Auth)
                Uri photoUrl = currentUser.getPhotoUrl();
                if (photoUrl != null && getContext() != null) {
                    Log.d(TAG, "Non-Microsoft user with photo URL. Loading with Glide.");
                    Glide.with(requireContext())
                            .load(photoUrl)
                            .error(R.drawable.unknown_avatar)
                            .placeholder(R.drawable.unknown_avatar)
                            .circleCrop()
                            .into(imgAvatar);
                } else {
                    Log.d(TAG, "Non-Microsoft Firebase user without photo URL, or context is null. Setting default avatar.");
                    if (getContext() != null) {
                        Glide.with(requireContext())
                                .load(R.drawable.unknown_avatar)
                                .circleCrop()
                                .placeholder(R.drawable.unknown_avatar) // Thêm placeholder ở đây nữa cho nhất quán
                                .into(imgAvatar);
                    } else if (imgAvatar != null) { // Fallback an toàn nếu context null
                        imgAvatar.setImageResource(R.drawable.unknown_avatar);
                    }
                }
            }
        } else if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "User logged in via Session Manager.");
            String username = sessionManager.getUsername();
            txtName.setText(!TextUtils.isEmpty(username) ? username : "Người dùng");
            if (imgAvatar != null) {
                imgAvatar.setImageResource(R.drawable.avatar); // Sử dụng avatar riêng cho session users
            }
        } else {
            Log.d(TAG, "No user logged in (Guest).");
            txtName.setText("Khách");
            if (imgAvatar != null) {
                imgAvatar.setImageResource(R.drawable.avatar); // Sử dụng avatar riêng cho guests
            }
        }
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
            nameToDisplay = email;
        }
        if (TextUtils.isEmpty(nameToDisplay)) {
            nameToDisplay = "Người dùng";
        }
        return nameToDisplay;
    }

    private String getMsGraphToken() {
        if (getContext() == null) return null;
        SharedPreferences prefs = getContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(MS_GRAPH_TOKEN_KEY, null);
    }

    private void clearMsGraphToken() {
        if (getContext() == null) return ;
        Log.w(TAG, "Clearing potentially invalid MS Graph Token from SharedPreferences.");
        SharedPreferences prefs = getContext().getSharedPreferences(MS_GRAPH_PREFS, Context.MODE_PRIVATE);
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
                    Log.d(TAG, "Successfully fetched photo data from MS Graph API. Length: " + (photoData != null ? photoData.length : "null"));
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

            // Sử dụng requireActivity() hoặc getContext() nếu đã kiểm tra null
            // getActivity() đã được kiểm tra bên ngoài luồng này một lần,
            // nhưng để an toàn hơn khi lambda được thực thi, kiểm tra lại context và trạng thái fragment.
            if (getActivity() != null) { // Vẫn giữ check getActivity() cho runOnUiThread
                getActivity().runOnUiThread(() -> {
                    // Đảm bảo Fragment vẫn còn attached và view vẫn tồn tại
                    if (getContext() == null || !isAdded() || getView() == null) {
                        Log.w(TAG, "Fragment not attached, context is null, or view destroyed. Cannot update MS Graph photo.");
                        return;
                    }

                    String signatureKey = "default_user_signature";
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    if (currentUser != null && !TextUtils.isEmpty(currentUser.getUid())) {
                        signatureKey = currentUser.getUid();
                    }

                    // **SỬA LỖI QUAN TRỌNG: Kiểm tra finalPhotoData không null VÀ có độ dài > 0**
                    if (finalPhotoData != null && finalPhotoData.length > 0) {
                        Log.d(TAG, "Updating ImageView with fetched MS Graph photo. Signature Key: " + signatureKey);
                        Glide.with(requireContext()) // **SỬA: Sử dụng requireContext()**
                                .load(finalPhotoData)
                                .circleCrop()
                                .signature(new ObjectKey(signatureKey))
                                .placeholder(R.drawable.unknown_avatar)
                                .error(R.drawable.unknown_avatar) // Quan trọng: Xử lý lỗi khi Glide không load được photoData
                                .into(targetImageView);
                    } else {
                        if (finalPhotoData == null) {
                            Log.w(TAG, "Photo data is null (Error code: " + finalResponseCode + "), loading default avatar.");
                        } else { // finalPhotoData không null nhưng length == 0
                            Log.w(TAG, "Photo data is empty (Error code: " + finalResponseCode + "), loading default avatar.");
                        }
                        // Luôn hiển thị ảnh mặc định nếu không có ảnh hợp lệ
                        Glide.with(requireContext()) // **SỬA: Sử dụng requireContext()**
                                .load(R.drawable.unknown_avatar)
                                .circleCrop()
                                .placeholder(R.drawable.unknown_avatar) // Thêm placeholder
                                .error(R.drawable.unknown_avatar)       // Fallback cho trường hợp R.drawable.unknown_avatar có vấn đề
                                .signature(new ObjectKey(signatureKey + "_error_fallback_" + finalResponseCode))
                                .into(targetImageView);
                    }
                });
            } else {
                Log.e(TAG, "Activity is null when trying to update UI with MS Graph photo (post-fetch).");
            }
        });
    }

    private void showNotificationDialog() {
        if (getContext() == null || getActivity() == null) {
            Log.e(TAG, "Cannot show notification dialog, context or activity is null.");
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View dialogView = inflater.inflate(R.layout.notification_dialog, null);

        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerNotifications);
        TextView txtEmpty = dialogView.findViewById(R.id.txtEmptyNotifications);
        Button btnClose = dialogView.findViewById(R.id.btnCloseDialog);

        ArrayList<String> notifications = new ArrayList<>();
        notifications.add("Chào mừng đến với ứng dụng LangHexx!");

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

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
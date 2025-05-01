package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.example.langhexx.Controller.NotificationAdapter;
import com.example.langhexx.Model.Levels;
import com.example.langhexx.Model.UsernamePasswordSessionManager;
import com.example.langhexx.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

public class HomeFragment extends Fragment {
    private ListView lstLevel;
    private Button btnNotifiy;
    private TextView txtName;
    private ImageView imgAvatar;
    private ArrayList<Levels> levelsList;
    private LevelsAdapter levelsAdapter;

    public HomeFragment(){

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_fragement, container, false);
        addControls(view);
        //
        UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(getContext()) ;
        if (sessionManager.isLoggedIn()){
            String username = sessionManager.getUsername() ;
            txtName.setText(username);
        }
        //
        addEvents();
        setUpListView();
        return view;
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
        levelsAdapter = new LevelsAdapter(getActivity(), R.layout.custom_levels_lst, levelsList);
        lstLevel.setAdapter(levelsAdapter);
        lstLevel.setOnItemClickListener((parent, view, position, id) -> {
            String levelName = levelsList.get(position).getTxtLevels() ;
            if(levelName.equals("General English - Vstep")){
                Intent intent = new Intent(getActivity(), VstepActivity.class) ;
                startActivity(intent);
            }else {
                Intent intent = new Intent(getActivity(), LearningTypeActivity.class);
                intent.putExtra("levelName", levelsList.get(position).getTxtLevels());
                startActivity(intent);
            }
        });
    }

    private void addEvents(){
        btnNotifiy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNotificationDialog();
            }
        });
    }


    private void showNotificationDialog() {
        // Dùng MaterialAlertDialogBuilder để có style Material mặc định
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View dialogView = inflater.inflate(R.layout.notification_dialog, null); // Dùng layout mới

        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerNotifications);
        TextView txtEmpty = dialogView.findViewById(R.id.txtEmptyNotifications);
        Button btnClose = dialogView.findViewById(R.id.btnCloseDialog); // Vẫn là Button hoặc MaterialButton

        // Danh sách thông báo - hiện tại có 1 thông báo mặc định
        ArrayList<String> notifications = new ArrayList<>();
        notifications.add("Chào mừng đến với ứng dụng LangHexx!");
        // notifications.add("Một thông báo khác để test scroll."); // Thêm để test
        // notifications.clear(); // Test khi không có thông báo

        if (notifications.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            txtEmpty.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            txtEmpty.setVisibility(View.GONE);

            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            // Sử dụng Adapter đã định nghĩa (đảm bảo NotificationAdapter được định nghĩa đúng)
            NotificationAdapter notificationAdapter = new NotificationAdapter(notifications);
            recyclerView.setAdapter(notificationAdapter);
        }

        // *** BƯỚC QUAN TRỌNG BỊ THIẾU ***
        builder.setView(dialogView); // Gắn layout tùy chỉnh vào builder

        // Tạo dialog từ builder
        AlertDialog dialog = builder.create();

        // Xử lý sự kiện đóng dialog cho nút btnClose
        btnClose.setOnClickListener(v -> dialog.dismiss()); // Lambda cho gọn

        // *** BƯỚC QUAN TRỌNG BỊ THIẾU ***
        dialog.show(); // Hiển thị dialog
    }
}
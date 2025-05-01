package com.example.langhexx.View;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.langhexx.Controller.LocaleHelper;
import com.example.langhexx.Model.UsernamePasswordSessionManager;
import com.example.langhexx.R;

import java.util.prefs.Preferences;

public class ProfileFragment extends Fragment {

    TextView txtProfileDetail, txtFeedback, txtName, txtEmail, txtLanguage;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        addControls(view);
        setName();
        addEvents();
        return view;
    }

    public void addControls(View view) {
        txtProfileDetail = view.findViewById(R.id.txtInfo);
        txtFeedback = view.findViewById(R.id.txtFeedback);
        txtName = view.findViewById(R.id.txtName);
        txtEmail = view.findViewById(R.id.txtMail);
        txtLanguage = view.findViewById(R.id.txtLanguage);
        // Đặt text ban đầu cho txtLanguage (có thể lấy từ LocaleHelper)
        Context context = getContext();
        if (context != null) {
            String currentLang = LocaleHelper.getLanguage(context);
            if (currentLang.equals("vi")) {
                txtLanguage.setText("Tiếng Việt");
            } else if (currentLang.equals("en")) {
                txtLanguage.setText("English");
            } else {
                txtLanguage.setText("Ngôn ngữ"); // Mặc định
            }
        }
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

        txtLanguage.setOnClickListener(v -> {
            showLanguageChangeDialog();
        });
    }

    private void showLanguageChangeDialog() {
        String[] langNames = {"Tiếng Việt", "English"};
        String[] langCodes = {"vi", "en"};

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.select_language);

        builder.setItems(langNames, (dialog, which) -> {
            String selectedLangCode = langCodes[which];
            LocaleHelper.setLocale(requireActivity(), selectedLangCode);

            // Lưu ngôn ngữ đã chọn vào SharedPreferences
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("user_language", selectedLangCode);
            editor.apply();

            // Cập nhật text của txtLanguage ngay lập tức
            if (selectedLangCode.equals("vi")) {
                txtLanguage.setText("Tiếng Việt");
            } else if (selectedLangCode.equals("en")) {
                txtLanguage.setText("English");
            }

            // Khởi động lại toàn bộ ứng dụng
            Intent intent = requireActivity().getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(requireActivity().getBaseContext().getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            requireActivity().finish(); // Kết thúc Activity hiện tại
        });
        builder.show();
    }

    private void setName() {
        UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(getContext());
        txtName.setText("Leaner"); // Bạn có thể lấy tên người dùng thực tế từ session
        txtEmail.setText(sessionManager.getUsername());
    }
}
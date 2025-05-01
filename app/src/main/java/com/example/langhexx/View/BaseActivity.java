package com.example.langhexx.View;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.langhexx.Controller.LocaleHelper;

import java.util.Locale;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(newBase);
        String savedLanguage = prefs.getString("user_language", Locale.getDefault().getLanguage());
        super.attachBaseContext(LocaleHelper.onAttach(newBase, savedLanguage));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Bạn có thể thêm logic chung cho tất cả Activity ở đây nếu cần
    }

    // Để xử lý việc thay đổi cấu hình (bao gồm cả ngôn ngữ) mà không cần restart Activity hoàn toàn
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedLanguage = prefs.getString("user_language", Locale.getDefault().getLanguage());
        LocaleHelper.setLocale(this, savedLanguage);
        recreate();
    }
}

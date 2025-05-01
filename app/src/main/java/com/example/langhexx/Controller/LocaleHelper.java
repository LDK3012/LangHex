package com.example.langhexx.Controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {
    public static void setLocale(Context context, String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());

        SharedPreferences prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        prefs.edit().putString("app_lang", langCode).apply();
    }

    public static String getSavedLocale(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        return prefs.getString("app_lang", "vi");
    }

}

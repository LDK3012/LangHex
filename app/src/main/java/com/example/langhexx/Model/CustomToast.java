package com.example.langhexx.Model;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.langhexx.R;

public class CustomToast {

    private CustomToast() {
        // Private constructor để không ai new được class này
    }

    public static void showSuccess(Context context, String message, int iconResId) {
        showCustomToast(context, message, iconResId, R.layout.custom_success_toast_layout);
    }

    public static void showFail(Context context, String message, int iconResId) {
        showCustomToast(context, message, iconResId, R.layout.custome_fail_toast_layout);
    }

    private static void showCustomToast(Context context, String message, int iconResId, int layoutResId) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(layoutResId, null);

        ImageView toastIcon = layout.findViewById(R.id.toast_icon);
        TextView toastText = layout.findViewById(R.id.toast_text);

        toastText.setText(message);
        if (iconResId != 0) {
            toastIcon.setImageResource(iconResId);
            toastIcon.setVisibility(View.VISIBLE);
        } else {
            toastIcon.setVisibility(View.GONE);
        }

        Toast toast = new Toast(context.getApplicationContext());
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 50);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }
}

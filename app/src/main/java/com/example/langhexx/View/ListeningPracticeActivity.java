package com.example.langhexx.View;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.langhexx.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ListeningPracticeActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private MaterialButton continueButton;
    private TextView tvStartVideo;
    private TextInputEditText userInput;
    private VideoView videoView;
    private MaterialButton toggleHintButton;
    private View subtitleMask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening_practice);
        addControls();
        addEvents();
    }

    private void addControls() {
        btnBack = findViewById(R.id.btnBack);
        continueButton = findViewById(R.id.btnContinue);
        tvStartVideo = findViewById(R.id.tvStartVideo);
        userInput = findViewById(R.id.userInputAnswer);
        videoView = findViewById(R.id.videoView);
        toggleHintButton = findViewById(R.id.btnToggleHint);
        subtitleMask = findViewById(R.id.subtitleMask);
    }

    private void addEvents() {
        btnBack.setOnClickListener(v -> finish());
        continueButton.setOnClickListener(v -> {
            String input = userInput.getText().toString().trim();
            if (input.equalsIgnoreCase("This podcast is for people who")) {
                showCongratsDialog();
            } else {
                showErrorDialog();
            }
        });
        tvStartVideo.setOnClickListener(v -> {
            tvStartVideo.setVisibility(View.INVISIBLE);
            subtitleMask.setVisibility(View.VISIBLE);
            startVideo();
        });
        toggleHintButton.setOnClickListener(v -> {
            if (subtitleMask.getVisibility() == View.INVISIBLE) {
                subtitleMask.setVisibility(View.VISIBLE);
                toggleHintButton.setText("Ẩn gợi ý");
            } else {
                subtitleMask.setVisibility(View.INVISIBLE);
                toggleHintButton.setText("Gợi ý");
            }
        });
        videoView.setOnCompletionListener(mp -> {
            tvStartVideo.setVisibility(View.VISIBLE);
            subtitleMask.setVisibility(View.INVISIBLE);
        });
    }

    private void startVideo() {
        // Lấy đường dẫn video trong thư mục raw
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.demo_practice_listening);
        videoView.setVideoURI(videoUri);
        videoView.start();

    }

    private void showCongratsDialog() {
        final Dialog dialog = new Dialog(ListeningPracticeActivity.this);
        dialog.setContentView(R.layout.dialog_congratulations);
        MaterialButton btnOk = dialog.findViewById(R.id.btnOk);
        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(ListeningPracticeActivity.this, MainActivity.class); // Quay lại trang chủ
            startActivity(intent);
            finish();
        });
        dialog.show();
    }

    private void showErrorDialog() {
        final Dialog dialog = new Dialog(ListeningPracticeActivity.this);
        dialog.setContentView(R.layout.dialog_wrong);
        MaterialButton btnRetry = dialog.findViewById(R.id.btnRetry);
        btnRetry.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}

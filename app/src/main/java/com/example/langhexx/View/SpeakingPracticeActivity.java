package com.example.langhexx.View;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.langhexx.R;

import java.io.IOException;

public class SpeakingPracticeActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private ImageButton btnBack, btnMic;
    private TextView tvTimer, tvQuestion;
    private Button btnComplete;

    private MediaRecorder recorder = null;
    private boolean isRecording = false;
    private String fileName = null;

    private CountDownTimer countDownTimer;
    private long timeLeftInMillis = 300000; // 5 phút

    // Xác định liệu ứng dụng có cần bắt đầu ghi âm sau khi quyền được cấp
    private boolean shouldStartRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speaking_practice);
        addControls();
        addEvents();
    }

    private void addControls() {
        btnBack = findViewById(R.id.btnBack);
        btnMic = findViewById(R.id.btnMicro);
        tvTimer = findViewById(R.id.tvTimer);
        tvQuestion = findViewById(R.id.tvQuestion);
        btnComplete = findViewById(R.id.btnComplete);
        tvQuestion.setText("Introducing yourself");
        startTimer();
    }

    private void addEvents() {
        btnBack.setOnClickListener(v -> onBackPressed());
        btnMic.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                // Kiểm tra quyền ghi âm
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startRecording();
                } else {
                    // Nếu chưa cấp quyền, yêu cầu quyền và đặt cờ để bắt đầu ghi âm sau khi được cấp
                    shouldStartRecording = true;
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_RECORD_AUDIO_PERMISSION);
                }
            }
        });

        btnComplete.setOnClickListener(v -> {
            Toast.makeText(this, "Đã hoàn thành bài tập", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(SpeakingPracticeActivity.this, MainActivity.class); // Quay lại trang chủ
            startActivity(intent);
            finish();
        });
    }

    private void startTimer() {
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateCountDownText();
            }
            public void onFinish() {
                tvTimer.setText("Hết giờ");
                if (isRecording) {
                    stopRecording();
                }
            }
        }.start();
    }

    private void updateCountDownText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        String timeFormatted = String.format("%02d:%02d", minutes, seconds);
        tvTimer.setText(timeFormatted);
    }

    private void startRecording() {
        // Thiết lập tên tệp ghi âm
        fileName = getExternalCacheDir().getAbsolutePath();
        fileName += "/audiorecord.3gp";
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
            btnMic.setImageResource(R.drawable.record);
            Toast.makeText(this, "Đang ghi âm...", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Không thể ghi âm", Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi bắt đầu ghi âm", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        try {
            recorder.stop();
            recorder.release();
            recorder = null;
            isRecording = false;
            btnMic.setImageResource(R.drawable.micro);
            Toast.makeText(this, "Đã dừng ghi âm", Toast.LENGTH_SHORT).show();

            // Xử lý tệp âm thanh đã ghi (fileName)
            // Ví dụ: lưu vào cơ sở dữ liệu hoặc phát lại
        } catch (RuntimeException stopException) {
            // Xử lý trường hợp stop khi chưa start
            stopException.printStackTrace();
            Toast.makeText(this, "Lỗi khi dừng ghi âm", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording) {
            stopRecording();
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Quyền ghi âm đã được cấp", Toast.LENGTH_SHORT).show();
                if (shouldStartRecording) {
                    shouldStartRecording = false;
                    startRecording();
                }
            } else {
                Toast.makeText(this, "Ứng dụng cần quyền ghi âm để hoạt động", Toast.LENGTH_SHORT).show();
                btnMic.setEnabled(false);
            }
        }
    }
}

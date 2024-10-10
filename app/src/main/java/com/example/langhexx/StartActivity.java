package com.example.langhexx;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class StartActivity extends AppCompatActivity {

    ProgressBar progressBar;
    TextView welcomeTextView;
    private int progressStatus = 0;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        // Tham chiếu tới các thành phần trong layout
        progressBar = findViewById(R.id.progressBar);
        welcomeTextView = findViewById(R.id.txtWelcome);

        // Đặt alpha của welcomeTextView về 0 (ẩn)
        welcomeTextView.setAlpha(0f);

        // Chạy tiến trình progress bar
        new Thread(new Runnable() {
            public void run() {
                while (progressStatus < 100) {
                    progressStatus += 1;
                    // Cập nhật tiến trình của progress bar
                    handler.post(new Runnable() {
                        public void run() {
                            progressBar.setProgress(progressStatus);
                        }
                    });
                    try {
                        // Điều chỉnh thời gian để mô phỏng quá trình tải
                        Thread.sleep(50); // Điều chỉnh thời gian theo nhu cầu
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // Khi progress bar hoàn thành, hiển thị welcomeTextView từ từ
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        // Hiển thị dòng chữ welcome từ từ bằng hiệu ứng fade-in
                        welcomeTextView.animate().alpha(1f).setDuration(2000); // Hiệu ứng trong 2 giây

                        // Sau khi hiển thị welcome, đợi 2 giây rồi chuyển sang Activity mới
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Khởi chạy Activity mới
                                Intent intent = new Intent(StartActivity.this, LoginActivity.class);
                                startActivity(intent);
                                finish(); // Đóng Activity hiện tại
                            }
                        }, 4000); // Chờ 2 giây trước khi chuyển sang Activity mới
                    }
                });
            }
        }).start();
    }
}
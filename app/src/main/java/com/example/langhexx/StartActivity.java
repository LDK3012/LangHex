package com.example.langhexx;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
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
        progressBar = findViewById(R.id.progressBar); // Mặc dù progressBar bị ẩn, nó vẫn chạy ngầm
        welcomeTextView = findViewById(R.id.txtWelcome);

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
                        // Điều chỉnh tốc độ của progress bar
                        Thread.sleep(1200); // Điều chỉnh thời gian theo nhu cầu
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // Khi progress bar hoàn thành, hiển thị welcome và chuyển sang Activity mới
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        // Hiển thị dòng chữ welcome
                        welcomeTextView.setVisibility(View.VISIBLE);
                        welcomeTextView.setAlpha(0f);
                        welcomeTextView.animate().alpha(1f).setDuration(1000); // 1 giây hiệu ứng fade-in

                        // Sau khi hiển thị welcome, đợi 2 giây rồi chuyển sang Activity mới
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Khởi chạy Activity mới
                                Intent intent = new Intent(StartActivity.this, MainActivity.class);
                                startActivity(intent);
                                finish(); // Đóng Activity hiện tại nếu cần
                            }
                        }, 2000); // Chờ 2 giây trước khi chuyển sang Activity mới
                    }
                });
            }
        }).start();
    }
}
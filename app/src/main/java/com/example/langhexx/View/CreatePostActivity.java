package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.R;

public class CreatePostActivity extends AppCompatActivity {

    ImageView imgBackward, imgUserAvatar;
    TextView txtAuthor;
    EditText edtPostTitle, edtPostContent;
    Button btnCreatePost;
    private String currentUserAvatarUrl;
    private String currentUserName;
    public static final String EXTRA_POST_TITLE = "com.example.langhexx.View.POST_TITLE";
    public static final String EXTRA_POST_AUTHOR_NAME = "com.example.langhexx.View.POST_AUTHOR_NAME";
    public static final String EXTRA_POST_AVATAR_URL = "com.example.langhexx.View.POST_AVATAR_URL";
    public static final String EXTRA_POST_CONTENT = "com.example.langhexx.View.POST_CONTENT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);
        addControls();
        Intent intent = getIntent();
        currentUserName = intent.getStringExtra("USER_NAME");
        currentUserAvatarUrl = intent.getStringExtra("USER_AVATAR_URL");
        if (currentUserName != null) {
            txtAuthor.setText(currentUserName);
        } else {
            txtAuthor.setText("Người dùng ẩn danh");
        }
        if (currentUserAvatarUrl != null && !currentUserAvatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(currentUserAvatarUrl)
                    .placeholder(R.drawable.avatar) // Ảnh mặc định khi đang tải
                    .error(R.drawable.unknown_avatar)   // Ảnh mặc định nếu lỗi tải
                    .circleCrop()
                    .into(imgUserAvatar);
        } else {
            imgUserAvatar.setImageResource(R.drawable.avatar); // Hoặc unknown_avatar
        }
        addEvents();
    }

    public void addControls(){
        imgBackward = findViewById(R.id.imgBackward);
        imgUserAvatar = findViewById(R.id.imgUserAvatar);
        txtAuthor = findViewById(R.id.txtAuthor);
        edtPostTitle = findViewById(R.id.edtPostTitle);
        edtPostContent = findViewById(R.id.edtPostContent);
        btnCreatePost = findViewById(R.id.btnCreatePost);
    }

    public void addEvents(){
        imgBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        btnCreatePost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String title = edtPostTitle.getText().toString().trim();
                String content = edtPostContent.getText().toString().trim(); // Lấy cả content
                if (TextUtils.isEmpty(title)) {
                    CustomToast.showFail(CreatePostActivity.this, "Please enter article title", R.drawable.fail_icon);
                    edtPostTitle.requestFocus();
                    return;
                }
                if (TextUtils.isEmpty(content)) {
                    CustomToast.showFail(CreatePostActivity.this, "Please enter article content", R.drawable.fail_icon);
                    edtPostContent.requestFocus();
                    return;
                }
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_POST_TITLE, title);
                // Gửi lại tên và avatar đã nhận được từ ForumActivity
                resultIntent.putExtra(EXTRA_POST_AUTHOR_NAME, currentUserName != null ? currentUserName : "Người dùng ẩn danh");
                resultIntent.putExtra(EXTRA_POST_AVATAR_URL, currentUserAvatarUrl != null ? currentUserAvatarUrl : "");
                resultIntent.putExtra(EXTRA_POST_CONTENT, content);
                setResult(CreatePostActivity.RESULT_OK, resultIntent);
                finish();
            }
        });
    }
    @Override
    public void onBackPressed() {
        setResult(CreatePostActivity.RESULT_CANCELED);
        super.onBackPressed();
    }
}
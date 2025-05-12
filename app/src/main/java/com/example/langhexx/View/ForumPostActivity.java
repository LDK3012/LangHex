package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.langhexx.Controller.CommentAdapter;
import com.example.langhexx.Controller.PostAdapter;
import com.example.langhexx.Model.Comment;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.Post;
import com.example.langhexx.R;

import java.util.ArrayList;

public class ForumPostActivity extends AppCompatActivity {

    ImageView imgBackward, imgUserAvatar;
    ImageButton btnLike, btnComment;
    TextView txtLikeNum,txtAuthor, txtTitle, txtTime, txtContent;
    ListView lvComments;
    private ArrayList<Comment> commentList;
    private CommentAdapter commentAdapter;
    public boolean isLiked = false;
    public int likeCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forum_post);
        addControls();
        // Nhận dữ liệu từ Intent
        Intent intent = getIntent();
        String postAvatarPath = intent.getStringExtra("POST_AVATAR_PATH");
        String postAuthorName = intent.getStringExtra("POST_AUTHOR_NAME");
        String postTitle = intent.getStringExtra("POST_TITLE");
        String postContent = intent.getStringExtra("POST_CONTENT");
        String postTime = intent.getStringExtra("POST_TIME");
        likeCount = intent.getIntExtra("POST_INITIAL_LIKE_COUNT", 0);
        isLiked = intent.getBooleanExtra("POST_INITIAL_IS_LIKED", false);
        if (txtAuthor!= null) {
            txtAuthor.setText(postAuthorName != null ? postAuthorName : "");
        }
        if (txtTitle != null) {
            txtTitle.setText(postTitle != null ? postTitle : "Tiêu đề không có sẵn");
        }
        if (txtContent != null) {
            txtContent.setText(postContent != null ? postContent : "Nội dung không có sẵn.");
        }
        if (txtTime != null) {
            txtTime.setText(postTime != null ? postTime : "");
        }
        if (imgUserAvatar != null) {
            if (!TextUtils.isEmpty(postAvatarPath)) {
                try {
                    // Thử parse thành Integer (cho trường hợp ID resource dạng String)
                    int resId = Integer.parseInt(postAvatarPath);
                    Glide.with(this)
                            .load(resId)
                            .placeholder(R.drawable.avatar)
                            .error(R.drawable.unknown_avatar)
                            .circleCrop()
                            .into(imgUserAvatar);
                } catch (NumberFormatException e) {
                    // Nếu không phải số, coi là URL
                    Glide.with(this)
                            .load(postAvatarPath)
                            .placeholder(R.drawable.avatar)
                            .error(R.drawable.unknown_avatar)
                            .circleCrop()
                            .into(imgUserAvatar);
                }
            } else {
                imgUserAvatar.setImageResource(R.drawable.avatar); // Ảnh mặc định
            }
        }
        txtLikeNum.setText(String.valueOf(likeCount));
        updateLikeButtonState();
        addEvents();
        setUpListView();
    }

    public void addControls(){
        imgBackward = findViewById(R.id.imgBackward);
        imgUserAvatar = findViewById(R.id.imgUserAvatar);
        txtAuthor = findViewById(R.id.txtAuthor);
        txtTitle = findViewById(R.id.txtTitle);
        txtTime = findViewById(R.id.txtTime);
        txtContent = findViewById(R.id.txtContent);
        btnLike = findViewById(R.id.btnLike);
        txtLikeNum = findViewById(R.id.txtLikeNum);
        btnComment = findViewById(R.id.btnComment);
        lvComments = findViewById(R.id.lvComments);
    }

    public void addEvents(){
        imgBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        btnComment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCommentDialog();
            }
        });
        btnLike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isLiked) {
                    likeCount--;
                    isLiked = false;
                } else {
                    likeCount++;
                    isLiked = true;
                }
                txtLikeNum.setText(String.valueOf(likeCount));
                updateLikeButtonState();
                CustomToast.showSuccess(ForumPostActivity.this, isLiked ? "Đã thích bài viết" : "Đã hủy thích bài viết", R.drawable.success);
                // TODO: Gửi kết quả likeCount và isLiked về ForumActivity để cập nhật ListView nếu cần
                // Intent resultIntent = new Intent();
                // resultIntent.putExtra("UPDATED_LIKE_COUNT", likeCount);
                // resultIntent.putExtra("UPDATED_IS_LIKED", isLiked);
                // resultIntent.putExtra("POST_ID", postId); // Cần ID của bài viết để cập nhật đúng item
                // setResult(Activity.RESULT_OK, resultIntent);
            }
        });
    }

    private void setUpListView(){
        commentList = new ArrayList<>();
        commentList.add(new Comment(String.valueOf(R.drawable.avatar), "Trần Thị B", "Đỉnh nóc, kịch trần, bay phấp phới"));
        commentList.add(new Comment(String.valueOf(R.drawable.avatar), "Trần Thị B", "Đỉnh nóc, kịch trần, bay phấp phới"));
        commentList.add(new Comment(String.valueOf(R.drawable.avatar), "Trần Thị B", "Đỉnh nóc, kịch trần, bay phấp phới"));
        commentList.add(new Comment(String.valueOf(R.drawable.avatar), "Trần Thị B", "Đỉnh nóc, kịch trần, bay phấp phới"));
        commentList.add(new Comment(String.valueOf(R.drawable.avatar), "Trần Thị B", "Đỉnh nóc, kịch trần, bay phấp phới"));
        commentList.add(new Comment(String.valueOf(R.drawable.avatar), "Trần Thị B", "Đỉnh nóc, kịch trần, bay phấp phới"));
        commentList.add(new Comment(String.valueOf(R.drawable.avatar), "Trần Thị B", "Đỉnh nóc, kịch trần, bay phấp phới"));
        commentAdapter = new CommentAdapter(ForumPostActivity.this, R.layout.custom_comment_list, commentList);
        lvComments.setAdapter(commentAdapter);
    }

    private void showCommentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ForumPostActivity.this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.custom_dialog_add_comment, null);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        EditText edtPost = dialogView.findViewById(R.id.edtPost);
        Button btnSend = dialogView.findViewById(R.id.btnSend);
        Button btnClose = dialogView.findViewById(R.id.btnClose);
//        btnSendCommentDialog.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                String commentText = etCommentInput.getText().toString().trim();
//                if (commentText.isEmpty()) {
//                    etCommentInput.setError("Vui lòng nhập bình luận của bạn");
//                } else {
//                    Toast.makeText(YourActivityOrFragment.this, "Đã gửi: " + commentText, Toast.LENGTH_LONG).show();
//                    dialog.dismiss();
//                }
//            }
//        });
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }
    private void updateLikeButtonState() {
        if (btnLike == null) return;
        if (isLiked) {
            btnLike.setColorFilter(ContextCompat.getColor(this, R.color.DarkGreen));
        } else {
            btnLike.clearColorFilter();
        }
    }
}
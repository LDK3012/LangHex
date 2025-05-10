package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.langhexx.Controller.CommentAdapter;
import com.example.langhexx.Controller.PostAdapter;
import com.example.langhexx.Model.Comment;
import com.example.langhexx.Model.Post;
import com.example.langhexx.R;

import java.util.ArrayList;

public class ForumPostActivity extends AppCompatActivity {

    ImageView imgBackward;
    ImageButton btnComment;
    ListView lvComments;
    private ArrayList<Comment> commentList;
    private CommentAdapter commentAdapter;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forum_post);
        addControls();
        addEvents();
        setUpListView();
    }

    public void addControls(){
        imgBackward = findViewById(R.id.imgBackward);
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
}
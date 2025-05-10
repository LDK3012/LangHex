package com.example.langhexx.View;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;

import com.example.langhexx.Controller.PostAdapter;
import com.example.langhexx.Model.Post;
import com.example.langhexx.R;

import java.util.ArrayList;


public class ForumActivity extends AppCompatActivity  {


    ImageView imgBackward;
    EditText edtPost;
    ListView lvRecentPosts;
    private ArrayList<Post> postList;
    private PostAdapter postAdapter;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forum);
        addControls();
        addEvents();
        setUpListView();
    }

    public void addControls(){
        imgBackward = findViewById(R.id.imgBackward);
        edtPost = findViewById(R.id.edtPost);
        lvRecentPosts = findViewById(R.id.lvRecentPosts);
    }

    public void addEvents(){
        imgBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        edtPost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ForumActivity.this, CreatePostActivity.class);
                startActivity(intent);
            }
        });
        lvRecentPosts.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(ForumActivity.this, ForumPostActivity.class);
            startActivity(intent);
        });
    }
    private void setUpListView(){
        postList = new ArrayList<>();
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postList.add(new Post(String.valueOf(R.drawable.avatar), "Lỗi âm thanh ở listening", "Nguyễn Văn A", "1 giờ trước"));
        postAdapter = new PostAdapter(ForumActivity.this, R.layout.custom_post_list, postList);
        lvRecentPosts.setAdapter(postAdapter);
    }
}

package vn.enghexteam.enghex.View;

import androidx.annotation.Nullable; // Thêm import này
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity; // Thêm import này
import android.content.Intent;
import android.os.Bundle;
import android.util.Log; // Thêm import
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView; // Thêm import

import com.bumptech.glide.Glide; // Thêm import
import vn.enghexteam.enghex.Controller.PostAdapter;
import vn.enghexteam.enghex.Model.CustomToast;
import vn.enghexteam.enghex.Model.Post;
import vn.enghexteam.enghex.R;

import java.text.SimpleDateFormat; // Thêm import
import java.util.ArrayList;
import java.util.Date;           // Thêm import
import java.util.Locale;         // Thêm import


public class ForumActivity extends AppCompatActivity {

    private static final String TAG = "ForumActivity"; // Để log
    ImageView imgBackward, imgUserAvatar;
    EditText edtPost;
    ListView lvRecentPosts;
    private ArrayList<Post> postList;
    private PostAdapter postAdapter;
    TextView txtUserName;
    private String currentGlobalUserName;
    private String currentGlobalUserAvatarUrl;
    private static final int CREATE_POST_REQUEST_CODE = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forum);
        addControls();
        Intent intent = getIntent();
        currentGlobalUserName = intent.getStringExtra("USER_NAME");
        currentGlobalUserAvatarUrl = intent.getStringExtra("USER_AVATAR_URL");
        if (currentGlobalUserName != null) {
            txtUserName.setText(currentGlobalUserName);
        } else {
            txtUserName.setText("");
        }

        if (currentGlobalUserAvatarUrl != null && !currentGlobalUserAvatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(currentGlobalUserAvatarUrl)
                    .placeholder(R.drawable.avatar)
                    .error(R.drawable.unknown_avatar)
                    .circleCrop()
                    .into(imgUserAvatar);
        } else {
            imgUserAvatar.setImageResource(R.drawable.avatar);
        }
        addEvents();
        setUpListView();
    }

    public void addControls() {
        imgBackward = findViewById(R.id.imgBackward);
        edtPost = findViewById(R.id.edtPost);
        lvRecentPosts = findViewById(R.id.lvRecentPosts);
        imgUserAvatar = findViewById(R.id.imgUserAvatar);
        txtUserName = findViewById(R.id.txtUserName);
    }

    public void addEvents() {
        imgBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        edtPost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentToCreatePost = new Intent(ForumActivity.this, CreatePostActivity.class);
                intentToCreatePost.putExtra("USER_NAME", currentGlobalUserName);
                intentToCreatePost.putExtra("USER_AVATAR_URL", currentGlobalUserAvatarUrl);
                startActivityForResult(intentToCreatePost, CREATE_POST_REQUEST_CODE);
            }
        });
        lvRecentPosts.setOnItemClickListener((parent, view, position, id) -> {
            Post clickedPost = postList.get(position);
            if (clickedPost == null) {
                CustomToast.showFail(ForumActivity.this, "Can't open content", R.drawable.fail_icon);
                return;
            }

            Intent intentToPostDetail = new Intent(ForumActivity.this, ForumPostActivity.class);

            Object avatarData = clickedPost.getImgAvatar();
            if (avatarData instanceof String) {
                intentToPostDetail.putExtra("POST_AVATAR_PATH", (String) avatarData);
            } else if (avatarData instanceof Integer) {
                intentToPostDetail.putExtra("POST_AVATAR_PATH", String.valueOf(avatarData));
            } else {
                intentToPostDetail.putExtra("POST_AVATAR_PATH", "");
            }
            intentToPostDetail.putExtra("POST_AUTHOR_NAME", clickedPost.getTxtAuthor());
            intentToPostDetail.putExtra("POST_TITLE", clickedPost.getTxtPostTitle());
            intentToPostDetail.putExtra("POST_CONTENT", clickedPost.getTxtContent()); // Truyền nội dung
            intentToPostDetail.putExtra("POST_TIME", clickedPost.getTxtTime());
            intentToPostDetail.putExtra("POST_INITIAL_LIKE_COUNT", clickedPost.getLikeCountData()); // Truyền số like ban đầu
            intentToPostDetail.putExtra("POST_INITIAL_IS_LIKED", clickedPost.isLikedByUser()); // Truyền trạng thái thích ban đầu
            // (Truyền thông tin người dùng hiện tại (đang xem) nếu cần cho chức năng comment
            // intentToPostDetail.putExtra("CURRENT_VIEWING_USER_NAME", currentGlobalUserName);
            // intentToPostDetail.putExtra("CURRENT_VIEWING_USER_AVATAR_URL", currentGlobalUserAvatarUrl);
            startActivity(intentToPostDetail);
        });
    }

    private void setUpListView() {
        postList = new ArrayList<>();
        postAdapter = new PostAdapter(ForumActivity.this, R.layout.custom_post_list, postList);
        lvRecentPosts.setAdapter(postAdapter);
        if (postList.isEmpty()){
            CustomToast.showFail(ForumActivity.this, "No posts yet!", R.drawable.fail_icon);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_POST_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String postTitle = data.getStringExtra(CreatePostActivity.EXTRA_POST_TITLE);
                String postAuthorName = data.getStringExtra(CreatePostActivity.EXTRA_POST_AUTHOR_NAME);
                String postAvatarUrl = data.getStringExtra(CreatePostActivity.EXTRA_POST_AVATAR_URL);
                String postContent = data.getStringExtra(CreatePostActivity.EXTRA_POST_CONTENT);
                if (postTitle != null && postAuthorName != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    String currentTime = sdf.format(new Date());
                    Post newPost = new Post(
                            postAvatarUrl,
                            postTitle,
                            postAuthorName,
                            currentTime,
                            postContent != null ? postContent : "",
                            0,
                            false
                    );
                    postList.add(0, newPost);
                    postAdapter.notifyDataSetChanged();
                    lvRecentPosts.smoothScrollToPosition(0);
                    CustomToast.showSuccess(ForumActivity.this, "Posted article!", R.drawable.success);
                    Log.d(TAG, "New post added: " + postTitle + " by " + postAuthorName);
                } else {
                    Log.e(TAG, "Received null data from CreatePostActivity");
                    CustomToast.showFail(ForumActivity.this, "Error receiving post data!", R.drawable.fail_icon);
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.d(TAG, "Post creation cancelled by user.");
                // Toast.makeText(this, "Đã hủy tạo bài viết.", Toast.LENGTH_SHORT).show(); // Không bắt buộc
            }
        }
    }
}
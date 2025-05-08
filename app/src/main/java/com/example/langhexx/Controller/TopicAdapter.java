package com.example.langhexx.Controller;

import android.content.Context;
import android.util.Log; // Import Log để theo dõi
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
// Bỏ ImageView nếu bạn không dùng nó trong ViewHolder này
// import android.widget.ImageView;

import androidx.annotation.NonNull; // Import NonNull

// Import các lớp Firebase cần thiết
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.example.langhexx.Model.Topics;
import com.example.langhexx.R;

import java.util.List;
import java.util.Locale; // Để sử dụng String.format với Locale

public class TopicAdapter extends BaseAdapter {
    private Context context;
    private int layout; // Đây là R.layout.list_speaking_topic
    private List<Topics> topicList;
    private String levelName; // Thêm biến để lưu levelName
    private DatabaseReference firebaseRootRef; // Tham chiếu gốc tới Firebase

    private static final String TAG = "TopicAdapter"; // Thẻ để log

    // Sửa constructor để nhận và lưu levelName
    public TopicAdapter(Context context, int layout, List<Topics> topicList, String levelName) {
        this.context = context;
        this.layout = layout;
        this.topicList = topicList;
        this.levelName = levelName; // Lưu levelName
        // Khởi tạo tham chiếu gốc một lần
        this.firebaseRootRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
    }

    @Override
    public int getCount() {
        return topicList.size();
    }

    @Override
    public Object getItem(int i) {
        return topicList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    // Cập nhật ViewHolder để chứa cả tvTopicTracker
    static class ViewHolder {
        TextView txtTopicTitle;
        TextView tvTopicTracker; // Thêm TextView cho tracker
        // ImageView imgIcon; // Nếu bạn muốn kiểm soát icon từ adapter
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder holder;

        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            view = inflater.inflate(layout, viewGroup, false); // layout ở đây là R.layout.list_speaking_topic
            holder = new ViewHolder();
            holder.txtTopicTitle = view.findViewById(R.id.tvTopicName);
            holder.tvTopicTracker = view.findViewById(R.id.tvTopicTracker); // Ánh xạ tvTopicTracker
            // holder.imgIcon = view.findViewById(R.id.imgIcon); // Nếu bạn có imgIcon trong list_speaking_topic và muốn quản lý
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        Topics topic = topicList.get(i);
        holder.txtTopicTitle.setText(topic.getTitle());

        // Đặt giá trị ban đầu cho tvTopicTracker (ví dụ: "Done: 0/...")
        holder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%s", "..."));

        // Tạo tham chiếu đến node "Exercises" của topic hiện tại
        DatabaseReference exercisesRef = firebaseRootRef
                .child("Lessons")
                .child("Levels")
                .child(levelName)       // Sử dụng levelName đã lưu
                .child("Listening")     // Mục "Listening"
                .child("Topics")
                .child(topic.getTitle()) // Tên của topic hiện tại
                .child("Exercises");

        // Gán holder và topic title vào biến final để sử dụng trong listener
        // Điều này quan trọng vì listener có thể được thực thi sau khi getView đã chạy cho item khác
        final ViewHolder currentHolder = holder;
        final String currentTopicTitle = topic.getTitle();

        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                long exerciseCount = 0;
                if (dataSnapshot.exists()) {
                    exerciseCount = dataSnapshot.getChildrenCount(); // Đếm số lượng child (exercises)
                }

                // Kiểm tra xem holder này có còn đang hiển thị đúng topic không trước khi cập nhật UI
                // (Quan trọng đối với ListView và việc tái sử dụng view)
                if (currentHolder.txtTopicTitle.getText().toString().equals(currentTopicTitle)) {
                    currentHolder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%d", exerciseCount));
                }
                Log.d(TAG, "Chủ đề: " + currentTopicTitle + ", Số bài tập: " + exerciseCount);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Lỗi khi tải số lượng bài tập cho chủ đề " + currentTopicTitle + ": " + databaseError.getMessage());
                if (currentHolder.txtTopicTitle.getText().toString().equals(currentTopicTitle)) {
                    currentHolder.tvTopicTracker.setText("Done: 0/N/A"); // Hiển thị lỗi
                }
            }
        });

        return view;
    }
}
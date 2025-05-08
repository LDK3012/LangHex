package com.example.langhexx.Controller;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.example.langhexx.Model.Topics;
import com.example.langhexx.R;

import java.util.List;
import java.util.Locale;

public class TopicAdapter extends BaseAdapter {
    private Context context;
    private int layoutId; // ID của layout item (ví dụ R.layout.list_speaking_topic)
    private List<Topics> topicList;
    private String levelName;
    private String skillName; // <-- Tham số để xác định kỹ năng (Listening, Reading, Speaking, Writing)
    private DatabaseReference firebaseRootRef;

    private static final String TAG = "TopicAdapter";

    // Constructor nhận thêm skillName
    public TopicAdapter(Context context, int layoutId, List<Topics> topicList, String levelName, String skillName) {
        this.context = context;
        this.layoutId = layoutId;
        this.topicList = topicList;
        this.levelName = levelName;
        this.skillName = skillName; // Lưu lại skillName
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

    static class ViewHolder {
        TextView txtTopicTitle;
        TextView tvTopicTracker;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder holder;

        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            view = inflater.inflate(layoutId, viewGroup, false);
            holder = new ViewHolder();
            holder.txtTopicTitle = view.findViewById(R.id.tvTopicName);
            holder.tvTopicTracker = view.findViewById(R.id.tvTopicTracker);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        Topics topic = topicList.get(i);
        holder.txtTopicTitle.setText(topic.getTitle());

        // Kiểm tra nếu skillName là "Speaking" thì ẩn tracker
        if ("Speaking".equalsIgnoreCase(skillName)) {
            if (holder.tvTopicTracker != null) {
                holder.tvTopicTracker.setVisibility(View.GONE);
            }
        } else {
            // Nếu không phải Speaking, hiển thị tracker và lấy số lượng bài tập
            if (holder.tvTopicTracker != null) {
                holder.tvTopicTracker.setVisibility(View.VISIBLE);
                holder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%s", "...")); // Trạng thái chờ

                // Sử dụng skillName để xây dựng đường dẫn Firebase chính xác
                DatabaseReference exercisesRef = firebaseRootRef
                        .child("Lessons")
                        .child("Levels")
                        .child(levelName)
                        .child(skillName) // <-- SỬ DỤNG skillName ở đây
                        .child("Topics")
                        .child(topic.getTitle())
                        .child("Exercises");

                final ViewHolder currentHolder = holder;
                final String currentTopicTitle = topic.getTitle();
                final String currentSkillForListener = skillName; // Lưu lại cho listener

                exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        long exerciseCount = 0;
                        if (dataSnapshot.exists()) {
                            exerciseCount = dataSnapshot.getChildrenCount();
                        }

                        // Đảm bảo holder vẫn đang hiển thị đúng item và tvTopicTracker không null
                        if (currentHolder.txtTopicTitle.getText().toString().equals(currentTopicTitle) &&
                                currentHolder.tvTopicTracker != null &&
                                !"Speaking".equalsIgnoreCase(currentSkillForListener) ) { // Kiểm tra lại để chắc chắn không cập nhật cho Speaking
                            currentHolder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%d", exerciseCount));
                        }
                        Log.d(TAG, "Kỹ năng: " + currentSkillForListener + ", Chủ đề: " + currentTopicTitle + ", Số bài tập: " + exerciseCount);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Lỗi khi tải số lượng bài tập cho [" + currentSkillForListener + "] " + currentTopicTitle + ": " + databaseError.getMessage());
                        if (currentHolder.txtTopicTitle.getText().toString().equals(currentTopicTitle) &&
                                currentHolder.tvTopicTracker != null &&
                                !"Speaking".equalsIgnoreCase(currentSkillForListener)) {
                            currentHolder.tvTopicTracker.setText("Done: 0/N/A");
                        }
                    }
                });
            }
        }
        return view;
    }
}
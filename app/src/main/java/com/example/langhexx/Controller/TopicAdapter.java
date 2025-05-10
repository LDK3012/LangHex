package com.example.langhexx.Controller;

import android.content.Context;
import android.graphics.drawable.Drawable; // Thêm import này
import android.graphics.drawable.GradientDrawable; // Thêm import này
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout; // Thêm import này
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.example.langhexx.Model.Topics;
import com.example.langhexx.R;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TopicAdapter extends BaseAdapter {
    private Context context;
    private int layoutId;
    private List<Topics> topicList;
    private String levelName;
    private String skillName;
    private DatabaseReference firebaseRootRef;
    private Set<String> clickedTopicTitles;

    private static final String TAG = "TopicAdapter";

    public TopicAdapter(Context context, int layoutId, List<Topics> topicList, String levelName, String skillName) {
        this.context = context;
        this.layoutId = layoutId;
        this.topicList = topicList;
        this.levelName = levelName;
        this.skillName = skillName;
        this.firebaseRootRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        this.clickedTopicTitles = new HashSet<>();
    }

    public TopicAdapter(Context context, int layoutId, List<Topics> topicList, String levelName, String skillName, Set<String> clickedTopicTitles) {
        this.context = context;
        this.layoutId = layoutId;
        this.topicList = topicList;
        this.levelName = levelName;
        this.skillName = skillName;
        this.firebaseRootRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        this.clickedTopicTitles = clickedTopicTitles != null ? clickedTopicTitles : new HashSet<>();
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
        FrameLayout speakingCardViewContainer; // Đã thay đổi
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
            // ID này phải khớp với ID của FrameLayout trong XML của bạn
            holder.speakingCardViewContainer = view.findViewById(R.id.speakingCardView); // Đã thay đổi
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        Topics topic = topicList.get(i);
        holder.txtTopicTitle.setText(topic.getTitle());

        // Kiểm tra xem topic này đã được click chưa
        if (clickedTopicTitles != null && clickedTopicTitles.contains(topic.getTitle())) {
            // Thay đổi màu nền của FrameLayout thành màu đỏ, cố gắng giữ góc bo tròn
            Drawable background = holder.speakingCardViewContainer.getBackground();
            if (background != null) {
                // Quan trọng: gọi mutate() để đảm bảo bạn thay đổi một bản sao của drawable,
                // không ảnh hưởng đến các view khác có thể đang dùng chung drawable này.
                Drawable mutatedBackground = background.mutate();
                if (mutatedBackground instanceof GradientDrawable) {
                    ((GradientDrawable) mutatedBackground).setColor(ContextCompat.getColor(context, R.color.topic_clicked_background));
                } else {
                    // Nếu không phải GradientDrawable, hoặc để đơn giản, bạn có thể chỉ đặt màu nền
                    // (sẽ làm mất góc bo tròn nếu chúng đến từ drawable gốc).
                    // Hoặc bạn có thể tạo một GradientDrawable mới màu đỏ với góc bo tròn ở đây.
                    holder.speakingCardViewContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.topic_clicked_background));
                }
            } else {
                // Nếu không có background nào, chỉ đặt màu (sẽ không có góc bo tròn)
                holder.speakingCardViewContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.topic_clicked_background));
            }
        } else {
            // Đặt lại màu nền mặc định (từ custom_card_background.xml, thường là màu trắng với góc bo tròn)
            // Cách tốt nhất để đảm bảo cả hình dạng và màu sắc được khôi phục là đặt lại drawable gốc.
            holder.speakingCardViewContainer.setBackground(ContextCompat.getDrawable(context, R.drawable.custom_card_background));
        }


        if ("Speaking".equalsIgnoreCase(skillName)) {
            if (holder.tvTopicTracker != null) {
                holder.tvTopicTracker.setVisibility(View.GONE);
            }
        } else {
            if (holder.tvTopicTracker != null) {
                holder.tvTopicTracker.setVisibility(View.VISIBLE);
                holder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%s", "..."));

                DatabaseReference exercisesRef = firebaseRootRef
                        .child("Lessons")
                        .child("Levels")
                        .child(levelName)
                        .child(skillName)
                        .child("Topics")
                        .child(topic.getTitle())
                        .child("Exercises");

                final ViewHolder currentHolder = holder;
                final String currentTopicTitle = topic.getTitle();
                final String currentSkillForListener = skillName;

                exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        long exerciseCount = 0;
                        if (dataSnapshot.exists()) {
                            exerciseCount = dataSnapshot.getChildrenCount();
                        }

                        if (currentHolder.txtTopicTitle.getText().toString().equals(currentTopicTitle) &&
                                currentHolder.tvTopicTracker != null &&
                                !"Speaking".equalsIgnoreCase(currentSkillForListener) ) {
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
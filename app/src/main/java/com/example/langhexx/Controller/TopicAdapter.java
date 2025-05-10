package com.example.langhexx.Controller;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast; // Có thể cần Toast ở đây để báo lỗi nếu context không đúng

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.langhexx.Model.Topics;
import com.example.langhexx.R;
// Import trực tiếp Activity nếu bạn muốn cast tới nó
import com.example.langhexx.View.ChooseTopicSpeakingActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TopicAdapter extends BaseAdapter {
    private Context context; // Context này sẽ được dùng để cast
    private int layoutId;
    private List<Topics> topicList;
    private String levelName;
    private String currentSkillNameAdapter;
    private DatabaseReference firebaseRootRef;

    private Set<String> highlightedTopicTitles;
    // KHÔNG CÒN itemInteractionListener

    private static final String TAG = "TopicAdapter";

    public TopicAdapter(Context context, int layoutId, List<Topics> topicList, String levelName, String skillName) {
        this.context = context; // Lưu context
        this.layoutId = layoutId;
        this.topicList = topicList;
        this.levelName = levelName;
        this.currentSkillNameAdapter = skillName;
        this.firebaseRootRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        this.highlightedTopicTitles = new HashSet<>();
    }

    public void setHighlightedTopicTitles(@Nullable Set<String> titles) {
        if (titles != null) {
            this.highlightedTopicTitles = titles;
        } else {
            this.highlightedTopicTitles = new HashSet<>();
        }
    }

    // KHÔNG CÒN setTopicItemInteractionListener

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
        FrameLayout speakingCardViewContainer;
        ImageView imgTopicOptions;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder holder;

        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            view = inflater.inflate(layoutId, viewGroup, false);
            holder = new ViewHolder();
            // ... (khởi tạo holder views như cũ)
            holder.txtTopicTitle = view.findViewById(R.id.tvTopicName);
            holder.tvTopicTracker = view.findViewById(R.id.tvTopicTracker);
            holder.speakingCardViewContainer = view.findViewById(R.id.speakingCardView);
            holder.imgTopicOptions = view.findViewById(R.id.imgTopicOptions);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        final Topics topic = topicList.get(i);
        holder.txtTopicTitle.setText(topic.getTitle());

        boolean isSpeakingSkillCurrently = "Speaking".equalsIgnoreCase(currentSkillNameAdapter);
        boolean isTopicHighlighted = highlightedTopicTitles != null && highlightedTopicTitles.contains(topic.getTitle());

        // ... (logic tô màu nền giữ nguyên)
        if (isTopicHighlighted) {
            Drawable background = holder.speakingCardViewContainer.getBackground();
            if (background != null) {
                Drawable mutatedBackground = background.mutate();
                if (mutatedBackground instanceof GradientDrawable) {
                    ((GradientDrawable) mutatedBackground).setColor(ContextCompat.getColor(context, R.color.topic_clicked_background));
                } else {
                    holder.speakingCardViewContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.topic_clicked_background));
                }
            } else {
                holder.speakingCardViewContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.topic_clicked_background));
            }
        } else {
            holder.speakingCardViewContainer.setBackground(ContextCompat.getDrawable(context, R.drawable.custom_card_background));
        }


        if (holder.tvTopicTracker != null) {
            if (isSpeakingSkillCurrently) {
                holder.tvTopicTracker.setVisibility(View.GONE);
            } else {
                holder.tvTopicTracker.setVisibility(View.VISIBLE);
                holder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%s", "..."));
                loadExerciseCount(holder, topic.getTitle(), currentSkillNameAdapter);
            }
        }

        if (holder.imgTopicOptions != null) {
            // Menu 3 chấm CHỈ hiển thị cho Speaking VÀ context là ChooseTopicSpeakingActivity VÀ topic đó đã được click/highlight
            if (isSpeakingSkillCurrently && (context instanceof ChooseTopicSpeakingActivity) && isTopicHighlighted) {
                holder.imgTopicOptions.setVisibility(View.VISIBLE);
                holder.imgTopicOptions.setOnClickListener(v -> showPopupMenu(v, topic.getTitle()));
            } else {
                holder.imgTopicOptions.setVisibility(View.GONE);
                holder.imgTopicOptions.setOnClickListener(null);
            }
        }
        return view;
    }

    private void loadExerciseCount(final ViewHolder holder, final String topicTitle, final String skillNameForFirebase) {
        // ... (giữ nguyên hàm này)
        DatabaseReference exercisesRef = firebaseRootRef
                .child("Lessons")
                .child("Levels")
                .child(levelName)
                .child(skillNameForFirebase)
                .child("Topics")
                .child(topicTitle)
                .child("Exercises");

        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                long exerciseCount = 0;
                if (dataSnapshot.exists()) {
                    exerciseCount = dataSnapshot.getChildrenCount();
                }
                if (holder.txtTopicTitle.getText().toString().equals(topicTitle) &&
                        holder.tvTopicTracker.getVisibility() == View.VISIBLE) {
                    holder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%d", exerciseCount));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading exercise count for [" + skillNameForFirebase + "] " + topicTitle + ": " + databaseError.getMessage());
                if (holder.txtTopicTitle.getText().toString().equals(topicTitle) &&
                        holder.tvTopicTracker.getVisibility() == View.VISIBLE) {
                    holder.tvTopicTracker.setText("Done: 0/N/A");
                }
            }
        });
    }

    private void showPopupMenu(View anchorView, final String topicTitle) {
        // Kiểm tra xem context có phải là instance của ChooseTopicSpeakingActivity không
        if (!(context instanceof ChooseTopicSpeakingActivity)) {
            Log.e(TAG, "Context không phải là instance của ChooseTopicSpeakingActivity, không thể hiển thị menu xóa lịch sử.");
            // Hoặc Toast.makeText(context, "Không thể thực hiện hành động này.", Toast.LENGTH_SHORT).show();
            return;
        }

        PopupMenu popup = new PopupMenu(context, anchorView);
        popup.getMenuInflater().inflate(R.menu.topic_options_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete_history) {
                // Cast context và gọi phương thức public
                ((ChooseTopicSpeakingActivity) context).removeClickedTopicHistory(topicTitle);
                return true;
            }
            return false;
        });
        popup.show();
    }
}
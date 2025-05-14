
package com.example.langhexx.Controller;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.langhexx.Model.Topics;
import com.example.langhexx.R;
import com.example.langhexx.View.ChooseSpeakingGrammarTopicActivity;

import com.example.langhexx.View.ChooseSpeakingVoiceTopicActivity;
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
    private Context context;
    private int layoutId;
    private List<Topics> topicList;
    private String levelName;
    private String currentSkillNameAdapter;
    private DatabaseReference firebaseRootRef;
    private Set<String> highlightedTopicDisplayNames;
    private static final String TAG = "TopicAdapter";

    public TopicAdapter(Context context, int layoutId, List<Topics> topicList, String levelName, String skillName) {
        this.context = context;
        this.layoutId = layoutId;
        this.topicList = topicList;
        this.levelName = levelName;
        this.currentSkillNameAdapter = skillName;
        this.firebaseRootRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        this.highlightedTopicDisplayNames = new HashSet<>();
    }

    public void setHighlightedTopicDisplayNames(@Nullable Set<String> names) {
        this.highlightedTopicDisplayNames = (names != null) ? names : new HashSet<>();
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
        Topics topic = topicList.get(i);
        return topic.getId() != null ? topic.getId().hashCode() : i;
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
            holder.txtTopicTitle = view.findViewById(R.id.tvTopicName);
            holder.tvTopicTracker = view.findViewById(R.id.tvTopicTracker);
            holder.speakingCardViewContainer = view.findViewById(R.id.speakingCardView);
            holder.imgTopicOptions = view.findViewById(R.id.imgTopicOptions);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        final Topics topic = topicList.get(i);
        holder.txtTopicTitle.setText(topic.getTopicName());

        boolean isSpeakingSkillCurrently = "Speaking".equalsIgnoreCase(currentSkillNameAdapter);
        boolean isTopicHighlighted = highlightedTopicDisplayNames != null && highlightedTopicDisplayNames.contains(topic.getTopicName());

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
            } else { // Cho Reading, Writing, Listening
                holder.tvTopicTracker.setVisibility(View.VISIBLE);
                holder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%s", "..."));
                loadExerciseCount(holder, topic.getId(), topic.getTopicName(), currentSkillNameAdapter);
            }
        }

        if (holder.imgTopicOptions != null) {
            boolean canShowOptions = isSpeakingSkillCurrently && isTopicHighlighted &&
                    ((context instanceof ChooseSpeakingGrammarTopicActivity) || (context instanceof ChooseSpeakingVoiceTopicActivity));
            if (canShowOptions) {
                holder.imgTopicOptions.setVisibility(View.VISIBLE);
                holder.imgTopicOptions.setOnClickListener(v -> showPopupMenu(v, topic.getTopicName()));
            } else {
                holder.imgTopicOptions.setVisibility(View.GONE);
                holder.imgTopicOptions.setOnClickListener(null);
            }
        }
        return view;
    }

    private void loadExerciseCount(final ViewHolder holder, final String topicId, final String topicDisplayName, final String skillNameForFirebase) {
        DatabaseReference exercisesRef = firebaseRootRef
                .child("Lessons")
                .child("Levels")
                .child(levelName)
                .child(skillNameForFirebase)
                .child("Topics")
                .child(topicId) // Sử dụng Topic ID
                .child("Exercises");

        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                long exerciseCount = 0;
                if (dataSnapshot.exists()) {
                    exerciseCount = dataSnapshot.getChildrenCount();
                }
                if (holder.txtTopicTitle.getText().toString().equals(topicDisplayName) &&
                        holder.tvTopicTracker != null && // Thêm kiểm tra null
                        holder.tvTopicTracker.getVisibility() == View.VISIBLE) {
                    holder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%d", exerciseCount));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading exercise count for [" + skillNameForFirebase + "] Topic ID " + topicId + ": " + databaseError.getMessage());
                if (holder.txtTopicTitle.getText().toString().equals(topicDisplayName) &&
                        holder.tvTopicTracker != null && // Thêm kiểm tra null
                        holder.tvTopicTracker.getVisibility() == View.VISIBLE) {
                    holder.tvTopicTracker.setText("Done: 0/N/A");
                }
            }
        });
    }

    private void showPopupMenu(View anchorView, final String topicDisplayName) {
        if (!((context instanceof ChooseSpeakingGrammarTopicActivity) || (context instanceof ChooseSpeakingVoiceTopicActivity))) {
            Log.e(TAG, "Context not ChooseSpeakingGrammarTopicActivity or ChooseSpeakingPronunciationTopicActivity, cannot show delete history menu.");
            return;
        }
        PopupMenu popup = new PopupMenu(context, anchorView);
        popup.getMenuInflater().inflate(R.menu.topic_options_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete_history) {
                if (context instanceof ChooseSpeakingGrammarTopicActivity) {
                    ((ChooseSpeakingGrammarTopicActivity) context).removeClickedTopicHistory(topicDisplayName);
                } else if (context instanceof ChooseSpeakingVoiceTopicActivity) {
                    ((ChooseSpeakingVoiceTopicActivity) context).removeClickedTopicHistory(topicDisplayName);
                }
                return true;
            }
            return false;
        });
        popup.show();
    }
}
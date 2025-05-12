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
// import android.widget.PopupMenu; // Not used in Writing flow based on previous context
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.langhexx.Model.Topics; // Your updated Topics model
import com.example.langhexx.Model.WritingTopics;
import com.example.langhexx.R;
// import com.example.langhexx.View.ChooseTopicSpeakingActivity; // Not relevant for Writing

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WritingTopicAdapter extends BaseAdapter {
    private Context context;
    private int layoutId;
    private List<WritingTopics> topicList; // List of new Topics objects
    private String levelName;
    private String currentSkillNameAdapter;
    private DatabaseReference firebaseRootRef;

    // Highlighting logic - assuming it's based on topicName (display name)
    private Set<String> highlightedTopicDisplayNames;
    private static final String TAG = "TopicAdapter";

    public WritingTopicAdapter(Context context, int layoutId, List<WritingTopics> topicList, String levelName, String skillName) {
        this.context = context;
        this.layoutId = layoutId;
        this.topicList = topicList;
        this.levelName = levelName;
        this.currentSkillNameAdapter = skillName;
        this.firebaseRootRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        this.highlightedTopicDisplayNames = new HashSet<>();
    }

    public void setHighlightedTopicDisplayNames(@Nullable Set<String> names) {
        if (names != null) {
            this.highlightedTopicDisplayNames = names;
        } else {
            this.highlightedTopicDisplayNames = new HashSet<>();
        }
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
        // Return a stable ID if possible, here using position or hash of topic.getId()
        WritingTopics topic = topicList.get(i);
        return topic.getId() != null ? topic.getId().hashCode() : i;
    }

    static class ViewHolder {
        TextView txtTopicTitle; // This will display topic.getTopicName()
        TextView tvTopicTracker;
        FrameLayout speakingCardViewContainer; // Assuming same layout file is used
        ImageView imgTopicOptions; // Assuming same layout file is used
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
            holder.imgTopicOptions = view.findViewById(R.id.imgTopicOptions); // If it exists in layout
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        final WritingTopics topic = topicList.get(i);
        holder.txtTopicTitle.setText(topic.getTopicName()); // Use getTopicName()

        // Highlighting logic (if applicable for Writing, based on topicName)
        boolean isSpeakingSkillCurrently = "Speaking".equalsIgnoreCase(currentSkillNameAdapter); // Keep for conditional UI
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
            // Ensure you have a default background drawable named custom_card_background
            holder.speakingCardViewContainer.setBackground(ContextCompat.getDrawable(context, R.drawable.custom_card_background));
        }


        if (holder.tvTopicTracker != null) {
            if (isSpeakingSkillCurrently) { // If you want different UI for speaking vs writing
                holder.tvTopicTracker.setVisibility(View.GONE);
            } else { // For Writing
                holder.tvTopicTracker.setVisibility(View.VISIBLE);
                holder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%s", "..."));
                // Load exercise count using topic.getId()
                loadExerciseCount(holder, topic.getId(), topic.getTopicName(), currentSkillNameAdapter);
            }
        }

        // Options menu logic (likely not needed for Writing topics list, but kept if layout is shared)
        if (holder.imgTopicOptions != null) {
            holder.imgTopicOptions.setVisibility(View.GONE); // Typically no options menu here for Writing topics
            holder.imgTopicOptions.setOnClickListener(null);
        }
        return view;
    }

    private void loadExerciseCount(final ViewHolder holder, final String topicId, final String topicDisplayName, final String skillNameForFirebase) {
        // Path now uses topicId
        DatabaseReference exercisesRef = firebaseRootRef
                .child("Lessons")
                .child("Levels")
                .child(levelName)
                .child(skillNameForFirebase) // Should be "Writing"
                .child("Topics")
                .child(topicId) // Use Topic ID here
                .child("Exercises");

        exercisesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                long exerciseCount = 0;
                if (dataSnapshot.exists()) {
                    exerciseCount = dataSnapshot.getChildrenCount();
                }
                // Check against topicDisplayName for UI update, as topicId might not be directly on holder
                if (holder.txtTopicTitle.getText().toString().equals(topicDisplayName) &&
                        holder.tvTopicTracker.getVisibility() == View.VISIBLE) {
                    holder.tvTopicTracker.setText(String.format(Locale.getDefault(), "Done: 0/%d", exerciseCount));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading exercise count for [" + skillNameForFirebase + "] Topic ID " + topicId + ": " + databaseError.getMessage());
                if (holder.txtTopicTitle.getText().toString().equals(topicDisplayName) &&
                        holder.tvTopicTracker.getVisibility() == View.VISIBLE) {
                    holder.tvTopicTracker.setText("Done: 0/N/A");
                }
            }
        });
    }

    // showPopupMenu might not be relevant for Writing topics list, but if it is, it should use topicId or topicName as appropriate.
    // private void showPopupMenu(View anchorView, final String topicTitle) { ... }
}

package com.example.langhexx.Controller;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.langhexx.Model.WritingExercise;
import com.example.langhexx.R;
import com.example.langhexx.View.InternalWritingTopic;

import java.util.List;

public class WritingExerciseAdapter extends RecyclerView.Adapter<WritingExerciseAdapter.ViewHolder> {

    private Context context;
    private List<WritingExercise> exerciseList;
    private String levelName;
    private String topicId;
    private String topicDisplayName;

    public WritingExerciseAdapter(Context context, List<WritingExercise> exerciseList, String levelName, String topicId, String topicDisplayName) {
        this.context = context;
        this.exerciseList = exerciseList;
        this.levelName = levelName;
        this.topicId = topicId;
        this.topicDisplayName = topicDisplayName;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_exercise, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WritingExercise currentExercise = exerciseList.get(position);
        int displayPosition = position + 1;
        String numberedExerciseTitle = displayPosition + ". " + currentExercise.getTitle();

        holder.tvExerciseItemTitle.setText(numberedExerciseTitle);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, InternalWritingTopic.class);
            intent.putExtra("LEVEL_NAME", levelName);
            intent.putExtra("TOPIC_ID", topicId);
            intent.putExtra("TOPIC_DISPLAY_NAME", topicDisplayName);
            intent.putExtra("EXERCISE_ID", currentExercise.getId());
            intent.putExtra("EXERCISE_DISPLAY_TITLE", currentExercise.getTitle());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return exerciseList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvExerciseItemTitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvExerciseItemTitle = itemView.findViewById(R.id.tvExerciseTitle);
        }
    }
}

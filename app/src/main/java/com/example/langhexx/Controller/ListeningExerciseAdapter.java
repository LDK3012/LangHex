package com.example.langhexx.Controller;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.langhexx.Model.Exercise;
import com.example.langhexx.R;
import com.example.langhexx.View.InternalListeningTopic;
import java.util.List;

public class ListeningExerciseAdapter extends RecyclerView.Adapter<ListeningExerciseAdapter.ViewHolder> {

    private Context context;
    private List<Exercise> exerciseList;
    private String levelName;
    private String topicId;
    private String topicDisplayTitle;

    public ListeningExerciseAdapter(Context context, List<Exercise> exerciseList, String levelName, String topicId, String topicDisplayTitle) {
        this.context = context;
        this.exerciseList = exerciseList;
        this.levelName = levelName;
        this.topicId = topicId;
        this.topicDisplayTitle = topicDisplayTitle;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_exercise, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Exercise exercise = exerciseList.get(position); // Lấy object Exercise
        int displayPosition = position + 1;
        String numberedExerciseTitle = displayPosition + ". " + exercise.getTitle();
        holder.tvExerciseItemTitle.setText(numberedExerciseTitle);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, InternalListeningTopic.class);
            intent.putExtra("LEVEL_NAME", levelName);
            intent.putExtra("TOPIC_ID", topicId);
            intent.putExtra("EXERCISE_ID", exercise.getId());
            intent.putExtra("TOPIC_TITLE", topicDisplayTitle);
            intent.putExtra("EXERCISE_TITLE", exercise.getTitle());
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
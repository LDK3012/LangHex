package com.example.langhexx.Controller;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.langhexx.R;
// *** Import the new InternalReadingTopic activity ***
import com.example.langhexx.View.InternalReadingTopic;
import java.util.List;

// *** Renamed class ***
public class ReadingExerciseAdapter extends RecyclerView.Adapter<ReadingExerciseAdapter.ViewHolder> {

    private Context context;
    private List<String> exerciseTitlesList;
    private String levelName;
    private String topicTitle;

    public ReadingExerciseAdapter(Context context, List<String> exerciseTitlesList, String levelName, String topicTitle) {
        this.context = context;
        this.exerciseTitlesList = exerciseTitlesList;
        this.levelName = levelName;
        this.topicTitle = topicTitle;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Reuse item_exercise.xml or create a specific one
        View view = LayoutInflater.from(context).inflate(R.layout.item_exercise, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String originalExerciseTitle = exerciseTitlesList.get(position);
        int displayPosition = position + 1;
        String numberedExerciseTitle = displayPosition + ". " + originalExerciseTitle;

        holder.tvExerciseItemTitle.setText(numberedExerciseTitle);

        holder.itemView.setOnClickListener(v -> {
            // *** Navigate to InternalReadingTopic ***
            Intent intent = new Intent(context, InternalReadingTopic.class);
            intent.putExtra("LEVEL_NAME", levelName); // Consistent key naming convention recommended
            intent.putExtra("TOPIC_TITLE", topicTitle);
            intent.putExtra("EXERCISE_TITLE", originalExerciseTitle); // Pass the original title
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return exerciseTitlesList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvExerciseItemTitle; // Ensure this ID exists in item_exercise.xml

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // *** Ensure this ID exists in item_exercise.xml ***
            tvExerciseItemTitle = itemView.findViewById(R.id.tvExerciseTitle);
        }
    }
}
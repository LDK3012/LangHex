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
//import com.example.langhexx.View.InternalWritingTopic; // Đổi sang Activity đích cho Writing

import java.util.List;

public class WritingExerciseAdapter extends RecyclerView.Adapter<WritingExerciseAdapter.ViewHolder> {

    private Context context;
    private List<String> exerciseTitlesList;
    private String levelName;
    private String topicTitle;

    public WritingExerciseAdapter(Context context, List<String> exerciseTitlesList, String levelName, String topicTitle) {
        this.context = context;
        this.exerciseTitlesList = exerciseTitlesList;
        this.levelName = levelName;
        this.topicTitle = topicTitle;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Sử dụng layout item chung hoặc tạo layout item riêng cho writing exercise nếu cần
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
//            Intent intent = new Intent(context, InternalWritingTopic.class); // Chuyển đến InternalWritingTopic
//            intent.putExtra("LEVEL_NAME", levelName);
//            intent.putExtra("TOPIC_TITLE", topicTitle);
//            intent.putExtra("EXERCISE_TITLE", originalExerciseTitle); // Gửi tên bài tập gốc
//            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return exerciseTitlesList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvExerciseItemTitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvExerciseItemTitle = itemView.findViewById(R.id.tvExerciseTitle); // Đảm bảo ID này có trong item_exercise.xml
        }
    }
}
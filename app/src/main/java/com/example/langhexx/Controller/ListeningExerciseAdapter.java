package com.example.langhexx.Controller;

import android.content.Context;
import android.content.Intent; // Thêm import Intent
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView; // Giả sử bạn có TextView để hiển thị title

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.langhexx.R;
import com.example.langhexx.View.InternalListeningTopic; // Thêm import Activity đích
import java.util.List;

public class ListeningExerciseAdapter extends RecyclerView.Adapter<ListeningExerciseAdapter.ViewHolder> {

    private Context context;
    private List<String> exerciseTitlesList;
    private String levelName;  // Thêm biến để lưu
    private String topicTitle; // Thêm biến để lưu

    // Constructor cần nhận levelName và topicTitle
    public ListeningExerciseAdapter(Context context, List<String> exerciseTitlesList, String levelName, String topicTitle) {
        this.context = context;
        this.exerciseTitlesList = exerciseTitlesList;
        this.levelName = levelName;   // Lưu lại
        this.topicTitle = topicTitle; // Lưu lại
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate layout cho item của bạn, ví dụ: R.layout.item_exercise_title
        View view = LayoutInflater.from(context).inflate(R.layout.item_exercise, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String originalExerciseTitle = exerciseTitlesList.get(position);

        // Tạo số thứ tự hiển thị (position bắt đầu từ 0 nên cần +1)
        int displayPosition = position + 1;

        // Tạo chuỗi hiển thị mới bao gồm số thứ tự
        // Ví dụ: "1. Exercise Introduction", "2. Practice Part 1"
        String numberedExerciseTitle = displayPosition + ". " + originalExerciseTitle;
        // Hiển thị chuỗi đã đánh số trong TextView
        holder.tvExerciseItemTitle.setText(numberedExerciseTitle);
        // *** Xử lý Click để chuyển Activity ***
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, InternalListeningTopic.class);
            // Đóng gói dữ liệu cần gửi
            intent.putExtra("LEVEL_NAME", levelName);
            intent.putExtra("TOPIC_TITLE", topicTitle);
            intent.putExtra("EXERCISE_TITLE", originalExerciseTitle);
            context.startActivity(intent);
        });
        // **********************************************
    }

    @Override
    public int getItemCount() {
        return exerciseTitlesList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvExerciseItemTitle; // Ví dụ TextView trong item layout

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ view trong item layout, ví dụ:
            tvExerciseItemTitle = itemView.findViewById(R.id.tvExerciseTitle);
        }
    }
}
package com.example.langhexx.Controller;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.langhexx.R;

import java.util.ArrayList;

/**
 * Adapter cho RecyclerView hiển thị danh sách thông báo (dạng String).
 * Sử dụng layout item_notification.xml cho mỗi mục.
 */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final ArrayList<String> notificationList;

    /**
     * Cung cấp một tham chiếu đến các view cho mỗi item dữ liệu.
     * ViewHolder chứa các thành phần giao diện của một item trong RecyclerView.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // TextView để hiển thị nội dung thông báo
        private final TextView notificationTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            notificationTextView = itemView.findViewById(R.id.txtNotificationItem);
        }

        /**
         * Gắn dữ liệu (nội dung thông báo) vào TextView.
         * @param notificationText Nội dung thông báo cần hiển thị.
         */
        public void bind(String notificationText) {
            if (notificationText != null) {
                notificationTextView.setText(notificationText);
            } else {
                notificationTextView.setText("");
            }
        }
    }

    /**
     * Constructor của Adapter.
     * @param notificationList Danh sách các chuỗi thông báo cần hiển thị.
     */
    public NotificationAdapter(ArrayList<String> notificationList) {
        this.notificationList = (notificationList != null) ? notificationList : new ArrayList<>();
    }

    /**
     * Được gọi khi RecyclerView cần tạo một ViewHolder mới.
     * Nó tạo và khởi tạo ViewHolder nhưng *không* gắn dữ liệu vào view.
     * @param parent ViewGroup chứa các view item, thường là RecyclerView.
     * @param viewType Loại view của item mới (hữu ích nếu có nhiều loại item).
     * @return Một ViewHolder mới đã được khởi tạo với layout item.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    /**
     * Được gọi bởi RecyclerView để hiển thị dữ liệu tại vị trí cụ thể.
     * Nó cập nhật nội dung của ViewHolder để phản ánh item tại vị trí đã cho.
     * @param holder ViewHolder cần được cập nhật.
     * @param position Vị trí của item trong tập dữ liệu.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String currentNotification = notificationList.get(position);
        holder.bind(currentNotification);
    }

    /**
     * Trả về tổng số item trong tập dữ liệu được giữ bởi adapter.
     * @return Số lượng thông báo trong danh sách.
     */
    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    /**
     * (Tùy chọn) Phương thức để cập nhật dữ liệu cho Adapter và thông báo thay đổi.
     * @param newNotifications Danh sách thông báo mới.
     */
    public void updateData(ArrayList<String> newNotifications) {
        this.notificationList.clear();
        if (newNotifications != null) {
            this.notificationList.addAll(newNotifications);
        }
        notifyDataSetChanged();
    }
}
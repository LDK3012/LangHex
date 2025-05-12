package com.example.langhexx.Controller;

import android.content.Context;
import android.text.TextUtils; // Thêm import này
import android.util.Log; // Thêm import này
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

// Thêm import cho Glide nếu chưa có ở đây
import com.bumptech.glide.Glide;
import com.example.langhexx.Model.Post; // Đảm bảo Model.Post là đúng
import com.example.langhexx.R;

import java.util.List;

public class PostAdapter extends ArrayAdapter<Post> {
    private Context context;
    private int resourceLayoutId;
    private List<Post> postList;
    private static final String TAG = "PostAdapterUser";

    public PostAdapter(Context context, int resource, List<Post> postList) {
        super(context, resource, postList);
        this.context = context;
        this.resourceLayoutId = resource;
        this.postList = postList;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(resourceLayoutId, parent, false);
            holder = new ViewHolder();
            try {
                holder.imgAvatar = convertView.findViewById(R.id.imgAvatar);
                holder.txtPostTitle = convertView.findViewById(R.id.txtPostTitle);
                holder.txtAuthor = convertView.findViewById(R.id.txtAuthor);
                holder.txtTime = convertView.findViewById(R.id.txtTime);

                if (holder.imgAvatar == null || holder.txtPostTitle == null ||
                        holder.txtAuthor == null || holder.txtTime == null) {
                    Log.e(TAG, "Một hoặc nhiều Views trong ViewHolder là null. Kiểm tra IDs trong XML item listview!");
                }
            } catch (Exception e) {
                Log.e(TAG, "Lỗi findViewById: " + e.getMessage() + ". XML item có thể bị sai.");
                if (convertView == null) return new View(context);
            }
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Post post = postList.get(position);

        if (post == null) {
            Log.e(TAG, "Post object at position " + position + " is null.");
            if(holder.txtPostTitle != null) holder.txtPostTitle.setText("Lỗi dữ liệu");
            if(holder.txtAuthor != null) holder.txtAuthor.setText("");
            if(holder.txtTime != null) holder.txtTime.setText("");
            if(holder.imgAvatar != null) holder.imgAvatar.setImageResource(R.drawable.unknown_avatar); // Ảnh lỗi chung
            return convertView;
        }

        Object avatarDataSource = post.getImgAvatar(); // Lấy dữ liệu avatar, kiểu có thể là String hoặc int
        String avatarPathString = null;

        if (avatarDataSource instanceof String) {
            avatarPathString = (String) avatarDataSource;
        } else if (avatarDataSource instanceof Integer) {
            if(holder.imgAvatar != null) {
                try {
                    holder.imgAvatar.setImageResource((Integer) avatarDataSource);
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi khi setImageResource với Integer: " + avatarDataSource, e);
                    holder.imgAvatar.setImageResource(R.drawable.unknown_avatar); // Fallback
                }
            }
            Log.d(TAG, "Avatar data source is Integer: " + avatarDataSource);
        } else if (avatarDataSource != null) {
            avatarPathString = String.valueOf(avatarDataSource);
            Log.d(TAG, "Avatar data source is other type, converted to String: " + avatarPathString);
        }


        if (holder.imgAvatar != null) {
            if (!TextUtils.isEmpty(avatarPathString)) {
                try {
                    int resId = Integer.parseInt(avatarPathString);
                    holder.imgAvatar.setImageResource(resId);
                    Log.d(TAG, "Set avatar from parsed String resource ID: " + resId);
                } catch (NumberFormatException e) {
                    Glide.with(context)
                            .load(avatarPathString)
                            .placeholder(R.drawable.avatar)
                            .error(R.drawable.unknown_avatar)
                            .circleCrop()
                            .into(holder.imgAvatar);
                    Log.d(TAG, "Set avatar from URL (Glide): " + avatarPathString);
                }
            } else if (!(avatarDataSource instanceof Integer)) {
                holder.imgAvatar.setImageResource(R.drawable.avatar);
                Log.d(TAG, "Avatar path is empty or null, using default image.");
            }
        }


        // Hiển thị các thông tin khác
        if (holder.txtPostTitle != null) {
            holder.txtPostTitle.setText(post.getTxtPostTitle() != null ? post.getTxtPostTitle() : "N/A");
        }
        if (holder.txtAuthor != null) {
            holder.txtAuthor.setText(post.getTxtAuthor() != null ? post.getTxtAuthor() : "N/A");
        }
        if (holder.txtTime != null) {
            holder.txtTime.setText(post.getTxtTime() != null ? post.getTxtTime() : "");
        }

        return convertView;
    }

    private static class ViewHolder {
        ImageView imgAvatar;
        TextView txtPostTitle;
        TextView txtAuthor;
        TextView txtTime;
    }
}
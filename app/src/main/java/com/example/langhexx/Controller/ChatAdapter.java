package com.example.langhexx.Controller;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.langhexx.Model.ChatMessage;
import com.example.langhexx.R;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    public static final int TYPE_USER = 0;
    public static final int TYPE_BOT = 1;

    private final List<ChatMessage> chatList;

    public ChatAdapter(List<ChatMessage> chatList) {
        this.chatList = chatList;
    }

    @Override
    public int getItemViewType(int position) {
        return chatList.get(position).getSenderType();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == TYPE_USER) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_message, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bot_message, parent, false);
        }
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.txtMessage.setText(chatList.get(position).getMessage());
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView txtMessage;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            txtMessage = itemView.findViewById(R.id.txtMessage);
        }
    }
}

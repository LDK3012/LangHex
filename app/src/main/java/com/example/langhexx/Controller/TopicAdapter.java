package com.example.langhexx.Controller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.langhexx.Model.Topics;
import com.example.langhexx.R;

import java.util.List;

public class TopicAdapter extends BaseAdapter {
    private Context context;
    private int layout;
    private List<Topics> topicList;

    public TopicAdapter(Context context, int layout, List<Topics> topicList) {
        this.context = context;
        this.layout = layout;
        this.topicList = topicList;
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
        return i;
    }

    static class ViewHolder {
        TextView txtTopicTitle;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder holder;

        if (view == null) {
            view = LayoutInflater.from(context).inflate(layout, viewGroup, false);
            holder = new ViewHolder();
            holder.txtTopicTitle = view.findViewById(R.id.tvTopicName);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        Topics topic = topicList.get(i);
        holder.txtTopicTitle.setText(topic.getTitle());

        return view;
    }
}

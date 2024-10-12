package com.example.langhexx;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class LevelsAdapter extends BaseAdapter {
    private Context context;
    private List<Levels> levelsList;
    private LayoutInflater inflater;

    public LevelsAdapter(Context context, int custom_levels_lst, List<Levels> newsList) {
        this.context = context;
        this.levelsList = newsList;
        this.inflater = LayoutInflater.from(context);
    }



    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.custom_levels_lst, parent, false);
        }
        ImageView imgAvatar = convertView.findViewById(R.id.imgAvatar);
        TextView txtLevels = convertView.findViewById(R.id.txtLevels);
        ImageView imgLevels = convertView.findViewById(R.id.imgLevels);
        return convertView;
    }
}

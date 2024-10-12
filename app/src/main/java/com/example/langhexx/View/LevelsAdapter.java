package com.example.langhexx.View;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.langhexx.Model.Levels;
import com.example.langhexx.R;

import java.util.List;

public class LevelsAdapter extends ArrayAdapter<Levels> {
    private Context context;
    private int resource;
    private List<Levels> levelsList;


    public LevelsAdapter(Context context, int resource, List<Levels> levelsList) {
        super(context, resource, levelsList);
        this.context = context;
        this.resource = resource;
        this.levelsList = levelsList;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(resource, null);
        }

        // Liên kết dữ liệu với view
        Levels level = levelsList.get(position);

        TextView txtLevelName = convertView.findViewById(R.id.txtLevels);
        txtLevelName.setText(level.getTxtLevels());

        ImageView imgLevel = convertView.findViewById(R.id.imgLevels) ;
        imgLevel.setImageResource(Integer.parseInt(String.valueOf(level.getImgLevels())));

        return convertView;
    }
}

package com.example.langhexx.Controller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.langhexx.Model.Levels;
import com.example.langhexx.Model.Post;
import com.example.langhexx.R;

import java.util.List;


public class PostAdapter extends ArrayAdapter<Post> {
    private Context context;
    private int resource;
    private List<Post> postList;

    public PostAdapter(Context context, int resource, List<Post> postList) {
        super(context, resource, postList);
        this.context = context;
        this.resource = resource;
        this.postList = postList;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(resource, null);
        }
        Post post = postList.get(position);

        ImageView imgAvatar = convertView.findViewById(R.id.imgAvatar) ;
        imgAvatar.setImageResource(Integer.parseInt(String.valueOf(post.getImgAvatar())));

        TextView txtPostTitle = convertView.findViewById(R.id.txtPostTitle);
        txtPostTitle.setText(post.getTxtPostTitle());

        TextView txtAuthor = convertView.findViewById(R.id.txtAuthor);
        txtAuthor.setText(post.getTxtAuthor());

        TextView txtTime = convertView.findViewById(R.id.txtTime);
        txtTime.setText(post.getTxtTime());
        return convertView;
    }
}

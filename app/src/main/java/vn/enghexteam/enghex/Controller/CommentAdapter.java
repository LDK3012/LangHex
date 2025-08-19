package vn.enghexteam.enghex.Controller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import vn.enghexteam.enghex.Model.Comment;

import vn.enghexteam.enghex.R;

import java.util.List;


public class CommentAdapter extends ArrayAdapter<Comment> {
    private Context context;
    private int resource;
    private List<Comment> commentList;

    public CommentAdapter(Context context, int resource, List<Comment> commentList) {
        super(context, resource, commentList);
        this.context = context;
        this.resource = resource;
        this.commentList = commentList;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(resource, null);
        }
        Comment comment = commentList.get(position);

        ImageView imgCommentAvatar = convertView.findViewById(R.id.imgCommentAvatar) ;
        imgCommentAvatar.setImageResource(Integer.parseInt(String.valueOf(comment.getImgCommentAvatar())));

        TextView txtCommenterName = convertView.findViewById(R.id.txtCommenterName);
        txtCommenterName.setText(comment.getTxtCommenterName());

        TextView txtCommentContent = convertView.findViewById(R.id.txtCommentContent);
        txtCommentContent.setText(comment.getTxtCommentContent());

        return convertView;
    }
}

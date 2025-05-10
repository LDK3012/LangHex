package com.example.langhexx.Model;

public class Comment {
    String imgCommentAvatar, txtCommenterName, txtCommentContent;

    public Comment(String imgCommentAvatar, String txtCommenterName, String txtCommentContent) {
        this.imgCommentAvatar = imgCommentAvatar;
        this.txtCommenterName = txtCommenterName;
        this.txtCommentContent = txtCommentContent;
    }

    public String getImgCommentAvatar() {
        return imgCommentAvatar;
    }

    public void setImgCommentAvatar(String imgCommentAvatar) {
        this.imgCommentAvatar = imgCommentAvatar;
    }

    public String getTxtCommenterName() {
        return txtCommenterName;
    }

    public void setTxtCommenterName(String txtCommenterName) {
        this.txtCommenterName = txtCommenterName;
    }

    public String getTxtCommentContent() {
        return txtCommentContent;
    }

    public void setTxtCommentContent(String txtCommentContent) {
        this.txtCommentContent = txtCommentContent;
    }
}

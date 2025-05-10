package com.example.langhexx.Model;

public class Post {
    String imgAvatar, txtPostTitle, txtAuthor, txtTime;

    public Post(String imgAvatar, String txtPostTitle, String txtAuthor, String txtTime) {
        this.imgAvatar = imgAvatar;
        this.txtPostTitle = txtPostTitle;
        this.txtAuthor = txtAuthor;
        this.txtTime = txtTime;
    }

    public String getImgAvatar() {
        return imgAvatar;
    }

    public void setImgAvatar(String imgAvatar) {
        this.imgAvatar = imgAvatar;
    }

    public String getTxtPostTitle() {
        return txtPostTitle;
    }

    public void setTxtPostTitle(String txtPostTitle) {
        this.txtPostTitle = txtPostTitle;
    }

    public String getTxtAuthor() {
        return txtAuthor;
    }

    public void setTxtAuthor(String txtAuthor) {
        this.txtAuthor = txtAuthor;
    }

    public String getTxtTime() {
        return txtTime;
    }

    public void setTxtTime(String txtTime) {
        this.txtTime = txtTime;
    }
}

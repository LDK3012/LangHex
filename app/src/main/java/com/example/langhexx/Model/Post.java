package com.example.langhexx.Model;

public class Post {
    private Object imgAvatar;
    private String txtPostTitle;
    private String txtAuthor;
    private String txtTime;
    private String txtContent;
    private int likeCountData;
    private boolean likedByUser;

    // Constructor
    public Post(Object imgAvatar, String txtPostTitle, String txtAuthor, String txtTime, String txtContent, int likeCountData, boolean likedByUser) {
        this.imgAvatar = imgAvatar;
        this.txtPostTitle = txtPostTitle;
        this.txtAuthor = txtAuthor;
        this.txtTime = txtTime;
        this.txtContent = txtContent;
        this.likeCountData = likeCountData;
        this.likedByUser = likedByUser;
    }

    public Object getImgAvatar() {
        return imgAvatar;
    }

    public String getTxtPostTitle() {
        return txtPostTitle;
    }

    public String getTxtAuthor() {
        return txtAuthor;
    }

    public String getTxtTime() {
        return txtTime;
    }

    public String getTxtContent() {
        return txtContent;
    }

    public int getLikeCountData() {
        return likeCountData;
    }

    public boolean isLikedByUser() {
        return likedByUser;
    }

    public void setLikeCountData(int likeCountData) {
        this.likeCountData = likeCountData;
    }

    public void setLikedByUser(boolean likedByUser) {
        this.likedByUser = likedByUser;
    }
}
package com.programming.videoService.model;

import java.util.List;

public class Video {

    private String filename;
    private String fileType;
    private String fileSize;
    private byte[] file;
    private String description;
    private String userID;
    private byte[] thumbnail;
    private String userName;
    private String videoName;
    private List<String> genres;

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public String getUserID() {
        return userID;
    }
    public void setUserID(String userID) {
        this.userID = userID;
    }
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Video() {
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public byte[] getFile() {
        return file;
    }

    public void setFile(byte[] file) {
        this.file = file;
    }

    public byte[] getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(byte[] thumbnail) {
        this.thumbnail = thumbnail;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }
    public String getVideoName() {
        return videoName;
    }

    // public int getViews() {
    //     return views;
    // }

    // public void setViews(int views) {
    //     this.views = views;
    // }

    // public int getLikes() {
    //     return likes;
    // }

    // public void setLikes(int likes) {
    //     this.likes = likes;
    // }

    // public int getDislikes() {
    //     return dislikes;
    // }
    // public void setDislikes(int dislikes) {
    //     this.dislikes = dislikes;
    // }
}
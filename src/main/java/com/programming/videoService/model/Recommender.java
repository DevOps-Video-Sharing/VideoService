package com.programming.videoService.model;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document(collection = "recommendations")
public class Recommender {
    // @Id
    private String userId;
    private List<String> genres;

    public Recommender() {
    }
    public Recommender(String userId, List<String> genres) {
        this.userId = userId;
        this.genres = genres;
    }
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public List<String> getGenres() {
        return genres;
    }
    public void setGenres(List<String> genres) {
        this.genres = genres;
    }
}

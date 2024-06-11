package com.programming.videoService.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.programming.videoService.model.Like;
public interface LikeRepository extends MongoRepository<Like, String>{
    void deleteByLikerToIdAndLikedToId(String likerToId, String likedToId);

    boolean existsByLikerToIdAndLikedToId(String likerToId, String likedToId);
    
    long countByLikedToId(String likedToId);
    
} 
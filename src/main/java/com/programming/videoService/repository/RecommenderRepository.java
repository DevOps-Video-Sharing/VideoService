package com.programming.videoService.repository;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.programming.videoService.model.Recommender;
public interface RecommenderRepository extends MongoRepository<Recommender, String> {
    
}

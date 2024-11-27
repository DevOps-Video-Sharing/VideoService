package com.programming.videoService.repository;
import com.programming.videoService.model.History;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface HistoryRepository  extends MongoRepository<History, String> {



    
} 
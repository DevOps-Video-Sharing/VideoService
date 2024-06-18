package com.programming.videoService.repository;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.programming.videoService.model.History;
public interface HistoryRepository extends MongoRepository<History, String> {

}
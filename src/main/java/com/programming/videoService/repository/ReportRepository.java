package com.programming.videoService.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.programming.videoService.model.Report;
public interface ReportRepository extends MongoRepository <Report, String> {
    
}

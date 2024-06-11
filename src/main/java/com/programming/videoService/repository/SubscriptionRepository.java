package com.programming.streaming.repository;

import com.programming.streaming.model.Subscription;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface SubscriptionRepository extends MongoRepository<Subscription, String> {
    void deleteBySubscriberIdAndSubscribedToId(String subscriberId, String subscribedToId);

    boolean existsBySubscriberIdAndSubscribedToId(String subscriberId, String subscribedToId);

    long countBySubscribedToId(String subscribedToId);
}
package com.programming.videoService.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.programming.videoService.model.History;
import com.programming.videoService.model.Like;
import com.programming.videoService.model.Report;
import com.programming.videoService.model.Subscription;
import com.programming.videoService.model.Video;
import com.programming.videoService.model.Recommender;
import com.programming.videoService.repository.HistoryRepository;
import com.programming.videoService.repository.LikeRepository;
import com.programming.videoService.repository.RecommenderRepository;
import com.programming.videoService.repository.SubscriptionRepository;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.time.Duration;
import java.util.Properties;


@Service
public class VideoService {

    @Autowired
    private GridFsTemplate template;

    @Autowired
    private GridFsOperations operations;

    @Autowired
    private MongoTemplate mongoTemplate;

    private KafkaProducer<String, String> kafkaProducer;
    private KafkaConsumer<String, String> kafkaConsumer;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public VideoService() {
        // Kafka Producer Configuration
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.kafkaProducer = new KafkaProducer<>(producerProps);

        // Kafka Consumer Configuration
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "video-genres-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        this.kafkaConsumer = new KafkaConsumer<>(consumerProps);
        this.kafkaConsumer.subscribe(Collections.singletonList("video-genres-topic"));
    }


    private static final Logger logger = LoggerFactory.getLogger(VideoService.class);
    public String addVideo(MultipartFile upload, String userID, byte[] thumbnail, Timestamp timestamp, String description, String userName, String videoName)
            throws IOException {
        DBObject videoMetadata = new BasicDBObject();
        videoMetadata.put("fileSize", upload.getSize());
        videoMetadata.put("userID", userID);
        videoMetadata.put("videoId", new ObjectId().toString());
        videoMetadata.put("timestamp", timestamp.toString());
        videoMetadata.put("description", description);
        videoMetadata.put("userName", userName);
        videoMetadata.put("videoName", videoName);
        Object videoID = template.store(upload.getInputStream(), upload.getOriginalFilename(), upload.getContentType(),
                videoMetadata);

        DBObject thumbnailMetadata = new BasicDBObject();
        thumbnailMetadata.put("fileSize", thumbnail.length);
        thumbnailMetadata.put("userID", userID);
        thumbnailMetadata.put("videoId", videoID.toString());
        thumbnailMetadata.put("timestamp", timestamp.toString());
        thumbnailMetadata.put("description", description);
        thumbnailMetadata.put("userName", userName);
        thumbnailMetadata.put("videoName", videoName);
        template.store(new ByteArrayInputStream(thumbnail), upload.getOriginalFilename() + "_thumbnail", "image/png",
                thumbnailMetadata);


    
        JSONObject kafkaMessage = new JSONObject();
        kafkaMessage.put("userID", userID);
        kafkaMessage.put("videoID", videoID.toString());
        kafkaMessage.put("description", description);

        kafkaProducer.send(new ProducerRecord<>("video-description-topic", videoID.toString(), kafkaMessage.toString()));

        processGenres();

        MDC.put("type", "videoservice");
        MDC.put("action", "upload");
        MDC.put("videoid", videoID.toString());
        logger.info("User " + userID.toString() + " upload a new video");
        return videoID.toString();
    }

    public void processGenres() {
        new Thread(() -> {
            while (true) {
                ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        String value = record.value();
                        JSONObject json = new JSONObject(value);

                        String videoID = json.getString("videoID");
                        List<String> genres = json.getJSONArray("genres").toList().stream()
                                .map(Object::toString).toList();

                        // Update genres in the database
                        DBObject query = new BasicDBObject("videoId", videoID);
                        DBObject update = new BasicDBObject("$set", new BasicDBObject("genres", genres));
                        // template.getCollection("videos").updateOne(query, update);
                        Query query1 = new Query(Criteria.where("_id").is(videoID));
                        Update update1 = new Update().set("genres", genres);
                        mongoTemplate.updateFirst(query1, update1, "fs.files");


                        logger.info("Updated genres for videoID: " + videoID + " with genres: " + genres);
                    } catch (Exception e) {
                        logger.error("Error processing genres from Kafka", e);
                    }
                }
            }
        }).start();
    }

    


    public List<String> getAllVideoIDs() {
        Query query = new Query();
        return template.find(query).map(GridFSFile::getObjectId).map(ObjectId::toString).into(new ArrayList<>());
    }

    public List<String> listIdThumbnail() {
        Query query = Query.query(Criteria.where("metadata._contentType").is("image/png"));
        return template.find(query).map(GridFSFile::getObjectId).map(ObjectId::toString).into(new ArrayList<>());
    }

    public List<String> getThumbnailIdByUserId(String userId) {
        Query query = Query.query(Criteria.where("metadata._contentType").is("image/png"));
        query = query.addCriteria(Criteria.where("metadata.userID").is(userId));
        return template.find(query).map(GridFSFile::getObjectId).map(ObjectId::toString).into(new ArrayList<>());
    }
    
    public ArrayList<String> getDetailsByUserId(String userId) {
        Query query = Query.query(Criteria.where("metadata._contentType").is("image/png"));
        query = query.addCriteria(Criteria.where("metadata.userID").is(userId));
        return template.find(query).map(GridFSFile::getObjectId).map(ObjectId::toString).into(new ArrayList<>());

    }

    public String getVideoIdFromThumbnailId(String thumbnailId) {
        Query query = Query.query(Criteria.where("_id").is(thumbnailId));
        GridFSFile gridFSFile = template.findOne(query);
        return gridFSFile.getMetadata().get("videoId").toString();
    }

    public List<String> getThumbnailIdsByVideoId(String videoId) {
        Query query = Query.query(Criteria.where("metadata.videoId").is(videoId));
        List<GridFSFile> gridFSFiles = template.find(query).into(new ArrayList<>());
    
        List<String> thumbnailIds = new ArrayList<>();
        for (GridFSFile file : gridFSFiles) {
            thumbnailIds.add(file.getObjectId().toString());
        }
    
        return thumbnailIds;
    }
    

    public VideoWithStream getVideoWithStream(String id) throws IOException {
        GridFSFile gridFSFile = template.findOne(new Query(Criteria.where("_id").is(id)));
        MDC.put("type", "videoservice");
        MDC.put("action", "play-video");
        logger.info("VideoId: " + id);
        if (gridFSFile != null) {
            return new VideoWithStream(gridFSFile, operations.getResource(gridFSFile).getInputStream());
        }
        return null;
    }

    //Hanle view

    public void updateViews(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().inc("views", 1);
        mongoTemplate.updateFirst(query, update, "fs.files");
    }

    public void updateLikes(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().inc("likes", 1);
        mongoTemplate.updateFirst(query, update, "fs.files");
    }

    public void updateDislikes(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().inc("dislikes", 1);
        mongoTemplate.updateFirst(query, update, "fs.files");
    }

    public static class VideoWithStream {
        private GridFSFile gridFSFile;
        private InputStream inputStream;

        public VideoWithStream(GridFSFile gridFSFile, InputStream inputStream) {
            this.gridFSFile = gridFSFile;
            this.inputStream = inputStream;
        }

        public GridFSFile getGridFSFile() {
            return gridFSFile;
        }

        public void setGridFSFile(GridFSFile gridFSFile) {
            this.gridFSFile = gridFSFile;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public void setInputStream(InputStream inputStream) {
            this.inputStream = inputStream;
        }
    }

    public Map<String, Object> getDetails(String videoId) {
        Query query = new Query(Criteria.where("_id").is(videoId));
        DBObject dbObject = mongoTemplate.findOne(query, DBObject.class, "fs.files");
        if (dbObject != null) {
            return dbObject.toMap();
        }
        return null;
    }





    // Handle Subcribe
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    public void subscribe(String subscriberId, String subscribedToId) {
        Subscription subscription = new Subscription(subscriberId, subscribedToId);
        subscriptionRepository.save(subscription);
    }

    public void unsubscribe(String subscriberId, String subscribedToId) {
        subscriptionRepository.deleteBySubscriberIdAndSubscribedToId(subscriberId, subscribedToId);
    }

    public boolean isSubscribed(String subscriberId, String subscribedToId) {
        return subscriptionRepository.existsBySubscriberIdAndSubscribedToId(subscriberId, subscribedToId);
    }
    
    public long getSubscriberCount(String userId) {
        return subscriptionRepository.countBySubscribedToId(userId);
    }

    // Handle Like
    @Autowired
    private LikeRepository likeRepository;

    public void like(String likerToId, String likedToId) {
        Like like = new Like(likerToId, likedToId);
        likeRepository.save(like);
    }
    
    public void unlike(String likerToId, String likedToId) {
        likeRepository.deleteByLikerToIdAndLikedToId(likerToId, likedToId);
    }

    public boolean isLiked(String likerToId, String likedToId) {
        return likeRepository.existsByLikerToIdAndLikedToId(likerToId, likedToId);
    }

    public long getLikeCount(String videoId) {
        return likeRepository.countByLikedToId(videoId);
    }


    public List<String> getLikedToIdsFromLikerToId(String likerToId) {
        Query query = Query.query(Criteria.where("likerToId").is(likerToId));
        List<Like> likes = mongoTemplate.find(query, Like.class);

        List<String> likedToIds = new ArrayList<>();
        for (Like like : likes) {
            likedToIds.add(like.getLikedToId());
        }

        return likedToIds;
    }

    //Hanlde History

    @Autowired
    private HistoryRepository historyRepository;
    
    public void addHistory(String userId, String thumbId) {
        History history = new History(userId, thumbId);
        historyRepository.save(history);
    }

    public List<String> getHistoryByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC, "timestamp"));
        List<History> histories = mongoTemplate.find(query, History.class);

        Map<String, History> thumbIdMap = new LinkedHashMap<>();
        for (History history : histories) {
            thumbIdMap.putIfAbsent(history.getThumbId(), history);
        }

        List<String> thumbIds = new ArrayList<>(thumbIdMap.keySet());

        return thumbIds;
    }

    //Hanle genres
    public List<String> getGenresByVideoId(String videoId){
        Query query = new Query(Criteria.where("_id").is(videoId));
        DBObject dbObject = mongoTemplate.findOne(query, DBObject.class, "fs.files");
        if (dbObject != null) {
            return (List<String>) dbObject.get("genres");
        }
        return null;
    }

    public Map<String, Integer> getGenresByUserId(String userId) {
        // 1. Lấy danh sách thumbnailId từ History
        List<String> thumbnailIds = getHistoryByUserId(userId);
    
        Map<String, Integer> genreCounts = new HashMap<>();
    
        // 2. Duyệt qua từng thumbnailId
        for (String thumbnailId : thumbnailIds) {
            try {
                // Lấy videoId từ thumbnailId
                String videoId = getVideoIdFromThumbnailId(thumbnailId);
    
                // Lấy genres từ videoId
                List<String> genres = getGenresByVideoId(videoId);
    
                // Đếm số lần xuất hiện của từng genre
                for (String genre : genres) {
                    genreCounts.put(genre, genreCounts.getOrDefault(genre, 0) + 1);
                }
            } catch (Exception e) {
                // Nếu lỗi, ghi log và tiếp tục
                System.err.println("Error processing thumbnailId: " + thumbnailId + ", error: " + e.getMessage());
            }
        }
    
        // 3. Sắp xếp genres theo số lượng giảm dần và giới hạn 12 genres
        return genreCounts.entrySet().stream()
            .sorted((entry1, entry2) -> Integer.compare(entry2.getValue(), entry1.getValue())) // Sắp xếp giảm dần theo giá trị
            .limit(12) // Lấy tối đa 12 genres
            .collect(LinkedHashMap::new, 
                     (map, entry) -> map.put(entry.getKey(), entry.getValue()), 
                     LinkedHashMap::putAll);
    }

    //handle recommend
    public List<String> getUniqueVideoIdsByGenres(String userId) {
        // 1. Lấy danh sách genres và số lần xuất hiện từ API /getGenresByUserId/{userId}
        Map<String, Integer> genreCounts = getGenresByUserId(userId);

        Set<String> uniqueVideoIds = new LinkedHashSet<>();

        // 2. Tìm videoId theo từng genre
        for (String genre : genreCounts.keySet()) {
            Query query = Query.query(Criteria.where("genres").is(genre));
            List<DBObject> videoFiles = mongoTemplate.find(query, DBObject.class, "fs.files");

            for (DBObject video : videoFiles) {
                String videoId = video.get("_id").toString();
                uniqueVideoIds.add(videoId);
            }
        }

        // 3. Trả về danh sách videoId (duy nhất, không trùng lặp)
        return new ArrayList<>(uniqueVideoIds);
    }

    public List<String> getThumbnailIdsByUserGenres(String userId) {
        // Bước 1: Lấy genres từ userId
        Map<String, Integer> genreCounts = getGenresByUserId(userId);
    
        // Dùng Set để đảm bảo không có `thumbnailId` trùng lặp
        Set<String> uniqueThumbnailIds = new LinkedHashSet<>();
    
        // Bước 2: Lấy danh sách videoId từ từng genre
        for (String genre : genreCounts.keySet()) {
            Query query = Query.query(Criteria.where("genres").is(genre));
            List<DBObject> videos = mongoTemplate.find(query, DBObject.class, "fs.files");
            for (DBObject video : videos) {
                String videoId = video.get("_id").toString();
    
                // Bước 3: Lấy danh sách thumbnailId từ videoId
                List<String> thumbnailIds = getThumbnailIdsByVideoId(videoId);
                uniqueThumbnailIds.addAll(thumbnailIds);
            }
        }
    
        // Trả về danh sách thumbnailId duy nhất
        return new ArrayList<>(uniqueThumbnailIds);
    }


    private static final String KAFKA_TOPIC = "synonyms_topic";
    public void processVideoData(String userId, Map<String, Integer> videoData) {
        try {
            // Đóng gói dữ liệu thành Map
            Map<String, Object> message = new HashMap<>();
            message.put("userId", userId);
            message.put("videoData", videoData);

            // Sử dụng Jackson để chuyển đổi thành JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonMessage = objectMapper.writeValueAsString(message);

            // Gửi JSON đến Kafka
            kafkaTemplate.send(KAFKA_TOPIC, jsonMessage);

            System.out.println("Processing video data: " + jsonMessage);
        } catch (Exception e) {
            System.err.println("Error converting to JSON: " + e.getMessage());
        }
    }
        

    @Autowired
    private RecommenderRepository recommenderRepository;
    @KafkaListener(topics = "recommendation_topic")
    public void consumeMessage(String message) {
        try {
            // Manually convert the string to Recommender object
            JSONObject json = new JSONObject(message);
            Recommender recommender = new ObjectMapper().readValue(json.toString(), Recommender.class);
            
            // Process the recommender object as needed (you can also add more logic here if required)
            System.out.println("Processing recommendation message: " + recommender);

            // Save the recommender object to the database
            saveToDatabase(recommender);
        } catch (Exception e) {
            logger.error("Error processing recommendation message", e);
        }
    }

    private void saveToDatabase(Recommender recommender) {
        try {
            // Assuming recommenderRepository is autowired and set up for the Recommender entity
            recommenderRepository.save(recommender);
            System.out.println("Saved to DB: " + recommender);
        } catch (Exception e) {
            System.err.println("Error saving to DB: " + e.getMessage());
        }
    }


   

        //hanlde Report
    public void uploadReport(String videoId, String msg, String userId) {
        Report report = new Report(videoId, msg, userId);
        mongoTemplate.save(report);
    }

    public List<String> getReportsWithHighFrequencyVideoIds() {
        List<Report> reports = mongoTemplate.findAll(Report.class);
        Map<String, Integer> videoIdCount = new HashMap<>();

        for (Report report : reports) {
            String videoId = report.getVideoId();
            videoIdCount.put(videoId, videoIdCount.getOrDefault(videoId, 0) + 1);
        }

        List<String> highFrequencyVideoIds = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : videoIdCount.entrySet()) {
            if (entry.getValue() > 5) {
                highFrequencyVideoIds.add(entry.getKey());
            }
        }

        return highFrequencyVideoIds;
    }
    
}
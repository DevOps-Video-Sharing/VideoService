package com.programming.videoService.controller;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.programming.videoService.model.Video;
import com.programming.videoService.service.VideoService;
import com.programming.videoService.service.VideoService.VideoWithStream;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@RestController
@RequestMapping("/video")
public class VideoController {

    @Autowired
    private VideoService videoService;
    private static final Logger logger = LoggerFactory.getLogger(VideoController.class);
    @GetMapping("/")
    public String getServiceName(){
        MDC.put("type", "videoservice");
        logger.info("Video Service Start");
        return "Video Service";
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
            @RequestParam("userID") String userID,
            @RequestParam("description") String description,
            @RequestParam("userName") String userName,
            @RequestParam("videoName") String videoName,
            @RequestParam("thumbnail") MultipartFile thumbnailFile) throws IOException {
        byte[] thumbnail = thumbnailFile.getBytes();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        return new ResponseEntity<>(videoService.addVideo(file, userID, thumbnail, timestamp, description, userName, videoName), HttpStatus.OK);
    }


    @GetMapping("/get/{id}")
    public ResponseEntity<?> download(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {
        VideoWithStream videoWithStream = videoService.getVideoWithStream(id);
        if (videoWithStream == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        GridFSFile gridFSFile = videoWithStream.getGridFSFile();
        InputStream inputStream = videoWithStream.getInputStream();

        long fileSize = gridFSFile.getLength();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(gridFSFile.getMetadata().get("_contentType").toString()));
        headers.setContentDisposition(ContentDisposition.builder("inline")
                .filename(UriUtils.encodePath(gridFSFile.getFilename(), StandardCharsets.UTF_8))
                .build());

        if (rangeHeader == null) {
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileSize)
                    .body(new InputStreamResource(inputStream));
        }

        String[] ranges = rangeHeader.replace("bytes=", "").split("-");
        long rangeStart = Long.parseLong(ranges[0]);
        long rangeEnd = ranges.length > 1 ? Long.parseLong(ranges[1]) : fileSize - 1;
        if (rangeEnd > fileSize - 1) {
            rangeEnd = fileSize - 1;
        }
        long rangeLength = rangeEnd - rangeStart + 1;

        inputStream.skip(rangeStart);
        InputStreamResource inputStreamResource = new InputStreamResource(
                new LimitedInputStream(inputStream, rangeLength));

        headers.add("Content-Range", "bytes " + rangeStart + "-" + rangeEnd + "/" + fileSize);
        headers.setContentLength(rangeLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .body(inputStreamResource);
    }

    @GetMapping("/getDetails/{videoId}")
    public Map<String, Object> getVideoDetails(@PathVariable String videoId) {
        return videoService.getDetails(videoId);
    }

    @GetMapping("/getDetailsByUserId/{userId}")
    public ResponseEntity<?> getVideoDetailsByUserId(@PathVariable String userId) {
        return new ResponseEntity<>(videoService.getDetailsByUserId(userId), HttpStatus.OK);
    }

    @GetMapping("/getAllIds")
    public ResponseEntity<?> getAllID() {
        return new ResponseEntity<>(videoService.getAllVideoIDs(), HttpStatus.OK);
    }

    @GetMapping("/listIdThumbnail")
    public ResponseEntity<?> listIdThumbnail() {
        return new ResponseEntity<>(videoService.listIdThumbnail(), HttpStatus.OK);
    }

    @GetMapping("/getThumbnailIdByUserId/{id}")
    public ResponseEntity<?> getThumbnailIdByUserId(@PathVariable String id) {
        return new ResponseEntity<>(videoService.getThumbnailIdByUserId(id), HttpStatus.OK);
    }

    @GetMapping("/getVideoIdFromThumbnailId/{id}")
    public ResponseEntity<?> getVideoIdFromThumbnailId(@PathVariable String id) {
        return new ResponseEntity<>(videoService.getVideoIdFromThumbnailId(id), HttpStatus.OK);
    }

    @GetMapping("/getThumbnailIdsByVideoId/{videoId}")
    public ResponseEntity<?> getThumbnailIdsByVideoId(@PathVariable String videoId) {
        try {
            List<String> thumbnailIds = videoService.getThumbnailIdsByVideoId(videoId);
            return new ResponseEntity<>(thumbnailIds, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching thumbnail IDs: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //hanle views


    @PutMapping("/updateViews/{id}")
    public ResponseEntity<?> updateViews(@PathVariable String id) {
        videoService.updateViews(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PutMapping("/updateLikes/{id}")
    public ResponseEntity<?> updateLikes(@PathVariable String id) {
        videoService.updateLikes(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PutMapping("/updateDislikes/{id}")
    public ResponseEntity<?> updateDislikes(@PathVariable String id) {
        videoService.updateDislikes(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private static class LimitedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        public LimitedInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int result = in.read();
            if (result != -1) {
                remaining--;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int result = in.read(b, off, toRead);
            if (result != -1) {
                remaining -= result;
            }
            return result;
        }
    }


    //Hanlde Subcribe
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestParam("subscriberId") String subscriberId,
            @RequestParam("subscribedToId") String subscribedToId) {
        videoService.subscribe(subscriberId, subscribedToId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestParam("subscriberId") String subscriberId,
            @RequestParam("subscribedToId") String subscribedToId) {
        videoService.unsubscribe(subscriberId, subscribedToId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/isSubscribed")
    public ResponseEntity<Boolean> isSubscribed(@RequestParam("subscriberId") String subscriberId,
            @RequestParam("subscribedToId") String subscribedToId) {
        boolean isSubscribed = videoService.isSubscribed(subscriberId, subscribedToId);
        return new ResponseEntity<>(isSubscribed, HttpStatus.OK);
    }
    
    @GetMapping("/getSubscriberCount")
    public ResponseEntity<Long> getSubscriberCount(@RequestParam("userId") String userId) {
        long subscriberCount = videoService.getSubscriberCount(userId);
        return new ResponseEntity<>(subscriberCount, HttpStatus.OK);
    }


    //Handle Like
    @PostMapping("/like")
    public ResponseEntity<?> like(@RequestParam("likerToId") String likerToId,
            @RequestParam("likedToId") String likedToId) {
        videoService.like(likerToId, likedToId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/unlike")
    public ResponseEntity<?> unlike(@RequestParam("likerToId") String likerToId,
            @RequestParam("likedToId") String likedToId) {
        videoService.unlike(likerToId, likedToId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/isLiked")
    public ResponseEntity<Boolean> isLiked(@RequestParam("likerToId") String likerToId,
            @RequestParam("likedToId") String likedToId) {
        boolean isLiked = videoService.isLiked(likerToId, likedToId);
        return new ResponseEntity<>(isLiked, HttpStatus.OK);
    }

    @GetMapping("/getLikeCount")
    public ResponseEntity<Long> getLikeCount(@RequestParam("videoId") String videoId) {
        long likeCount = videoService.getLikeCount(videoId);
        return new ResponseEntity<>(likeCount, HttpStatus.OK);
    }


    @GetMapping("/getIdFromLikerToId/{likerToId}")
    public ResponseEntity<?> getIdFromLikerToId(@PathVariable String likerToId) {
        return new ResponseEntity<>(videoService.getLikedToIdsFromLikerToId(likerToId), HttpStatus.OK);
    }

    // Hanlde History
    @PostMapping("/addHistory")
    public ResponseEntity<?> addHistory(@RequestParam("userId") String userId,
            @RequestParam("thumbId") String thumbId) {
        videoService.addHistory(userId, thumbId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/getHistoryByUserId/{userId}")
    public ResponseEntity<?> getHistoryByUserId(@PathVariable String userId) {
        return new ResponseEntity<>(videoService.getHistoryByUserId(userId), HttpStatus.OK);
    }

    //Handle Genre
    @GetMapping("/getGenresByVideoId/{videoId}")
    public ResponseEntity<?> getGenresByVideoId(@PathVariable String videoId) {
        return new ResponseEntity<>(videoService.getGenresByVideoId(videoId), HttpStatus.OK);
    }

    @GetMapping("/getGenresByUserId/{userId}")
    public ResponseEntity<?> getGenresByUserId(@PathVariable String userId) {
        try {
            Map<String, Integer> combinedGenres = videoService.getGenresByUserId(userId);
            return new ResponseEntity<>(combinedGenres, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching combined genres: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    
    // Hanle Recommend
    @GetMapping("/getUniqueVideoIdsByGenres/{userId}")
    public ResponseEntity<?> getUniqueVideoIdsByGenres(@PathVariable String userId) {
        try {
            List<String> uniqueVideoIds = videoService.getUniqueVideoIdsByGenres(userId);
            return new ResponseEntity<>(uniqueVideoIds, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching video IDs: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/getThumbnailsByUserGenres/{userId}")
    public ResponseEntity<?> getThumbnailsByUserGenres(@PathVariable String userId) {
        try {
            List<String> thumbnailIds = videoService.getThumbnailIdsByUserGenres(userId);
            return new ResponseEntity<>(thumbnailIds, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching thumbnails: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }





    //handle Report
    @PostMapping("/uploadReport")
    public ResponseEntity<?> uploadReport(@RequestParam("videoId") String videoId,
            @RequestParam("msg") String msg,
            @RequestParam("userId") String userId) {
        videoService.uploadReport(videoId, msg, userId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/videoIdLargerThanFive")
    public ResponseEntity<?> videoIdLargerThanFive() {
        return new ResponseEntity<>(videoService.getReportsWithHighFrequencyVideoIds(), HttpStatus.OK);
    }
    
}
FROM openjdk:11-jre-slim
COPY target/streaming-0.0.1-SNAPSHOT.jar /app/video-service.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/video-service.jar"]

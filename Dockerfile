# StoryAI 백엔드 컨테이너 — 리포 루트에서 빌드 (Railway가 루트를 분석하므로 Root Directory 설정 불필요)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY backend/ .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
# 낭독 영상(mp4) 합성을 위한 ffmpeg 설치.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*.jar app.jar
# Railway가 PORT를 주입 → application.yml의 server.port가 이를 사용.
# 컨테이너 메모리의 50%를 JVM 힙으로(기본 25%는 이미지 처리에 부족해 스레드 OOM→작업 멈춤 유발).
# 나머지 50%는 ffmpeg(영상 합성)·메타스페이스·스레드 등 네이티브 메모리 여유로 남긴다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50.0", "-jar", "app.jar"]

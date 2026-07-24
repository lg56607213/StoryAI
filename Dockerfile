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
# JVM 힙을 1GB로 명시 고정: 512MB에서 나던 OOM을 없애면서(4배), Hobby에서 메모리를
# 8GB까지 무한정 잡아 요금이 커지는 것도 방지한다(딱 필요한 만큼만). ffmpeg·네이티브 여유는 별도.
# 메모리를 더 늘리려면 JAVA_MAX_HEAP(예: 1536m)로 덮어쓸 수 있게 둔다.
ENTRYPOINT ["sh", "-c", "java -Xmx${JAVA_MAX_HEAP:-1024m} -jar app.jar"]

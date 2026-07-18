# StoryAI 백엔드 컨테이너 (Railway 등에서 사용)
# 1단계: Gradle 래퍼로 실행 가능한 부트 JAR 빌드
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# 2단계: JRE만 담아 가볍게 실행
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# Railway가 PORT를 주입 → application.yml의 server.port가 이를 사용.
ENTRYPOINT ["java", "-jar", "app.jar"]

# [Stage 1] 빌드용 (JDK가 필요함)
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# 1. Gradle 파일들 먼저 복사 (캐시 활용해서 속도 높이기 위함)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 2. 실행 권한 부여 (윈도우에서 올리면 권한 깨질 수 있어서 필수!)
RUN chmod +x ./gradlew

# 3. 의존성 다운로드 (소스코드 복사 전에 미리 해둠)
RUN ./gradlew dependencies --no-daemon

# 4. 소스코드 복사 및 빌드 (테스트는 건너뜀)
COPY src src
RUN ./gradlew bootJar -x test

# ---------------------------------------------------

# [Stage 2] 실행용 (가벼운 JRE만 있으면 됨)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 5. [Stage 1]에서 만든 jar 파일만 쏙 가져오기
COPY --from=builder /app/build/libs/*.jar app.jar

# 6. 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
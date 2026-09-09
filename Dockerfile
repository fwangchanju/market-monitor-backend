FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build
COPY gradlew .
COPY gradle gradle
# lombok.config가 없으면 @Qualifier가 생성자 파라미터로 복사되지 않아 RestClient 타임아웃 분리가 무효가 된다
COPY build.gradle settings.gradle gradle.properties lombok.config ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -Dorg.gradle.jvmargs="-Xmx512m -Xms128m"
COPY src src
RUN ./gradlew bootJar -x test --no-daemon -Dorg.gradle.jvmargs="-Xmx512m -Xms128m"

FROM eclipse-temurin:21-jre-jammy
LABEL org.opencontainers.image.source=https://github.com/fwangchanju/market-monitor-backend
WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

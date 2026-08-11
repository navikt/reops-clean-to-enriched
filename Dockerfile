# Stage 1: Build the Spring Boot fat jar
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

COPY gradlew gradlew
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN ./gradlew dependencies --no-daemon -PjavaVersion=25 || true

COPY src src
RUN ./gradlew bootJar --no-daemon -PjavaVersion=25

# Stage 2: Minimal JRE runtime image
FROM eclipse-temurin:25-jre
LABEL maintainer="team-researchops"
WORKDIR /app
COPY --from=builder /build/build/libs/app.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# Stage 1: Build native image
FROM ghcr.io/graalvm/native-image-community:25.0.2 AS builder
RUN microdnf install -y findutils binutils && microdnf clean all
WORKDIR /build

# Cache Gradle distribution download (changes only on Gradle version upgrade)
COPY gradlew gradlew
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version

# Cache dependencies (changes only when build files change)
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY contract/build.gradle.kts contract/
COPY bigquery/build.gradle.kts bigquery/
RUN ./gradlew :bigquery:dependencies --no-daemon -PjavaVersion=25 || true

# Copy source and build native image
COPY contract/src contract/src
COPY bigquery/src bigquery/src
ARG CI
ENV CI=${CI}
RUN ./gradlew :bigquery:nativeCompile --no-daemon -PjavaVersion=25 \
    && strip bigquery/build/native/nativeCompile/app

# Stage 2: Minimal runtime image (Chainguard with glibc)
FROM cgr.dev/chainguard/glibc-dynamic:latest
LABEL maintainer="team-researchops"
WORKDIR /app
COPY --from=builder /usr/lib64/libz.so.1 /lib/libz.so.1
COPY --from=builder /build/bigquery/build/native/nativeCompile/app ./app
EXPOSE 8080
ENTRYPOINT ["./app"]

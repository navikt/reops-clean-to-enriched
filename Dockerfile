# Stage 1: Build native image
FROM ghcr.io/graalvm/native-image-community:25.0.2 AS builder
RUN microdnf install -y findutils binutils && microdnf clean all
WORKDIR /build

COPY gradlew gradlew
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN ./gradlew dependencies --no-daemon -PjavaVersion=25 || true

COPY src src
ARG CI
ENV CI=${CI}
RUN ./gradlew nativeCompile --no-daemon -PjavaVersion=25 \
    && strip build/native/nativeCompile/app

# Stage 2: Minimal runtime image
FROM cgr.dev/chainguard/glibc-dynamic:latest
LABEL maintainer="team-researchops"
WORKDIR /app
COPY --from=builder /usr/lib64/libz.so.1 /lib/libz.so.1
COPY --from=builder /build/build/native/nativeCompile/app ./app
EXPOSE 8080
ENTRYPOINT ["./app"]

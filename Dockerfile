# Runtime image only — jar is built by CI before docker build runs
# (see .github/workflows/deploy.yaml: test job builds build/libs/app.jar,
# uploaded as artifact, downloaded here)
FROM eclipse-temurin:25-jre
LABEL maintainer="team-researchops"
WORKDIR /app
COPY build/libs/app.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

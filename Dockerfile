# Runtime image only — jar is built by CI before docker build runs
# (see .github/workflows/deploy.yaml: test job builds build/libs/app.jar,
# uploaded as artifact, downloaded here)
FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25
LABEL maintainer="team-researchops"
WORKDIR /app
COPY build/libs/app.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

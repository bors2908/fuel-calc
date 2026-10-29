# syntax=docker/dockerfile:1

################################################################################
# Build with the project's Gradle Wrapper and JVM toolchain.
################################################################################
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /opt/build

COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src/main ./src/main

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon bootJar

################################################################################
# Runtime: only the JRE, healthcheck client and application artifact.
################################################################################
FROM eclipse-temurin:25-jre-alpine AS backend
RUN apk add --no-cache curl \
    && addgroup -S app \
    && adduser -S -G app -u 10001 app
WORKDIR /opt/app
COPY --from=build /opt/build/build/libs/*.jar ./app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]

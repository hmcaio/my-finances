# Production image for the backend (F014). Not used in local dev (F001 runs the backend
# natively via `./gradlew bootRun`) — see ADR 0006. Built by CI on pushes to main and on
# version tags; can also be built locally for the docker-compose.prod.yml smoke test:
#   docker build -t my-finances-backend:local backend
#
# Build stage: JDK + the project's own Gradle wrapper (not a `gradle:*` base image) so the
# exact Gradle version pinned in gradle/wrapper/gradle-wrapper.properties (9.7.1) is what
# actually runs, matching local dev bit-for-bit.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY gradle.properties settings.gradle build.gradle ./
# Warm the Gradle wrapper/dependency cache as its own layer.
RUN ./gradlew --version

COPY src ./src
# Tests already ran in CI's test job before the image build runs; skip them here to keep the
# image build fast and avoid running them twice.
RUN ./gradlew bootJar -x test --no-daemon

# Runtime stage: slim JRE only, no JDK/Gradle in the final image.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

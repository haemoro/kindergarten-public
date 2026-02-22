# Build stage
FROM gradle:8.14.3-jdk17 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle clean bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 1025

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:1025/actuator/health || exit 1

CMD ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=70.0", \
  "-XX:+UseStringDeduplication", \
  "-jar", "app.jar"]

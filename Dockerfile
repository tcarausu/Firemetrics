# ---- builder ----
FROM eclipse-temurin:21-jdk AS builder
LABEL org.opencontainers.image.authors="Tcarausu"
WORKDIR /app

# Copy Gradle wrapper + build files first (better cache)
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Copy sources
COPY src ./src

# Build a runnable jar (skip tests here; CI runs them)
# Use bootJar if Spring Boot; otherwise use 'build'
RUN ./gradlew clean bootJar -x test --no-daemon --stacktrace || \
    ./gradlew clean build -x test --no-daemon --stacktrace

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the fat/boot jar (adjust the glob if your jar name differs)
COPY --from=builder /app/build/libs/*.jar /app/app.jar

EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]

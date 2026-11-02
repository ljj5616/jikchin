FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/build/libs/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

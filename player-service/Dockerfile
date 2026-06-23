# Runtime stage - assumes JAR is pre-built locally
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY player-service/target/scala-3.3.3/chess-player-service.jar app.jar

EXPOSE 8090

CMD ["java", "-jar", "app.jar"]

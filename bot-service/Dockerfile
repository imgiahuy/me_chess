# Runtime stage - assumes JAR is pre-built locally
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY bot-service/target/scala-3.3.3/chess-bot-service.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]

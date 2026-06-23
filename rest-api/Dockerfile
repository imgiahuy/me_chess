# Runtime stage - assumes JAR is pre-built locally
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY rest-api/target/scala-3.3.3/chess-rest-api.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]

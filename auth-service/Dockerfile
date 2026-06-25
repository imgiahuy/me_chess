FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY auth-service/target/scala-3.3.3/chess-auth-service.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]

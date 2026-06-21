# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache curl wget bash git

RUN wget https://github.com/sbt/sbt/releases/download/v1.9.9/sbt-1.9.9.tgz && \
    tar -xzf sbt-1.9.9.tgz && \
    mv sbt /usr/local/sbt && \
    ln -s /usr/local/sbt/bin/sbt /usr/local/bin/sbt && \
    rm sbt-1.9.9.tgz

ENV PATH="/usr/local/sbt/bin:$PATH"

WORKDIR /app

COPY build.sbt .
COPY project ./project/
COPY core ./core/
COPY shared ./shared/
COPY bot-service ./bot-service/

RUN sbt "botService/assembly"

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY --from=builder /app/bot-service/target/scala-3.3.3/chess-bot-service.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]

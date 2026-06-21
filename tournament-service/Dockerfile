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
COPY tournament-service ./tournament-service/

RUN sbt "tournamentService/assembly"

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY --from=builder /app/tournament-service/target/scala-3.3.3/chess-tournament-service.jar app.jar

EXPOSE 8070

CMD ["java", "-jar", "app.jar"]

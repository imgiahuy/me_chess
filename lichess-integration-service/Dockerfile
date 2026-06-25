# Runtime stage - assumes JAR is pre-built locally
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl git g++ make && \
    git clone --depth 1 --branch sf_16 https://github.com/official-stockfish/Stockfish.git /tmp/stockfish && \
    cd /tmp/stockfish/src && \
    make build ARCH=x86-64-modern && \
    mv stockfish /usr/local/bin/stockfish && \
    chmod +x /usr/local/bin/stockfish && \
    rm -rf /tmp/stockfish

WORKDIR /app

COPY lichess-integration-service/target/scala-3.3.3/chess-lichess-integration-service.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]

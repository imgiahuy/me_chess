# Build stage: assemble fat JAR from source
FROM eclipse-temurin:17-jdk-jammy AS builder

RUN apt-get update && apt-get install -y curl wget bash git && rm -rf /var/lib/apt/lists/*

RUN wget -q https://github.com/sbt/sbt/releases/download/v1.9.9/sbt-1.9.9.tgz && \
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
COPY spark ./spark/

RUN sbt spark/assembly

# Runtime stage: plain JRE — Spark is fully bundled inside the fat JAR
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /app/spark/target/scala-3.3.3/chess-spark.jar chess-spark.jar

ENTRYPOINT ["java", \
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", \
  "--add-opens=java.base/java.io=ALL-UNNAMED", \
  "--add-opens=java.base/java.lang=ALL-UNNAMED", \
  "--add-opens=java.base/java.util=ALL-UNNAMED", \
  "-Dspark.ui.enabled=false", \
  "-jar", "chess-spark.jar"]

CMD ["batch", "/tmp/chess-analytics"]

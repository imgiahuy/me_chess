FROM eclipse-temurin:21-jdk-alpine

# Install necessary tools
RUN apk add --no-cache curl wget bash git

# Install SBT using official installation script
RUN wget https://github.com/sbt/sbt/releases/download/v1.9.9/sbt-1.9.9.tgz && \
    tar -xzf sbt-1.9.9.tgz && \
    mv sbt /usr/local/sbt && \
    ln -s /usr/local/sbt/bin/sbt /usr/local/bin/sbt && \
    rm sbt-1.9.9.tgz

ENV PATH="/usr/local/sbt/bin:$PATH"

WORKDIR /app

# Copy build files first for better caching
COPY build.sbt .
COPY project ./project/
COPY core ./core/
COPY shared ./shared/
COPY persistent ./persistent/

# Copy rest-api source files without project config
RUN mkdir -p rest-api/src/main/scala/api
COPY rest-api/src/main/scala/api rest-api/src/main/scala/api

# Build the application
RUN sbt "++ 3.8.3!" compile

# Build fat JAR
RUN sbt "++ 3.8.3!" "restApi/assembly"

# Expose port
EXPOSE 8080

# Run the application with java
CMD ["java", "-jar", "rest-api/target/scala-3.8.3/chess-rest-api.jar"]

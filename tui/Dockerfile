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
COPY tui ./tui/

# Remove tui project subdirectory to force using root project
RUN rm -rf tui/project

# Build the application
RUN sbt compile

# Build fat JAR
RUN sbt "tui/assembly"

# Run the application
CMD ["java", "-jar", "tui/target/scala-3.3.3/chess-tui.jar"]

FROM eclipse-temurin:21-jdk

# Install X11 and dependencies for GUI applications
RUN apt-get update && apt-get install -y \
    curl wget xvfb git \
    libgtk-3-0 \
    libasound2t64 \
    libdrm2 \
    libxkbcommon0 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxss1 \
    libxtst6 \
    libxxf86vm1 \
    && rm -rf /var/lib/apt/lists/*

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
COPY gui ./gui/

# Remove gui project subdirectory to force using root project
RUN rm -rf gui/project

# Build the application
RUN sbt compile

# Build fat JAR
RUN sbt "gui/assembly"

# Run the application with java
CMD ["java", "-jar", "gui/target/scala-3.3.3/chess-gui.jar"]

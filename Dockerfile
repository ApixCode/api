# Stage 1: Build the Spring Boot JAR
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime with Chromium + ChromeDriver
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache \
    chromium \
    chromium-chromedriver \
    nss \
    freetype \
    harfbuzz \
    ca-certificates \
    ttf-freefont

# Set environment variables for Selenium/Chrome
ENV CHROME_BIN=/usr/bin/chromium-browser
ENV CHROMEDRIVER_PATH=/usr/bin/chromedriver

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

# Expose Spring Boot port (Railway uses $PORT env var)
EXPOSE 8080

# Railway assigns dynamic $PORT → Spring Boot listens on it
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]

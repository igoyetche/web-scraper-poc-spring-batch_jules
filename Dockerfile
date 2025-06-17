# Use a slim JDK 21 image
FROM eclipse-temurin:21-jdk-jammy

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the build context
# The JAR file name might vary depending on your build configuration (e.g., project name and version)
# Adjust "your-application.jar" accordingly
COPY build/libs/*.jar app.jar

# Define the entry point to run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]

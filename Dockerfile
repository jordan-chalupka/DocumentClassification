# Use the official OpenJDK 22 image
FROM openjdk:22-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the source code into the container
COPY . .

# Build the application
RUN ./gradlew build

# Expose the port the app runs on
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "/app/build/libs/demo-0.0.1-SNAPSHOT.jar"]

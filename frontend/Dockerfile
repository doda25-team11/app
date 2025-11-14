FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

EXPOSE 8080

# Run the compiled Jar file when the container starts
ENTRYPOINT ["java", "-jar", "/app/target/frontend-0.0.1.jar"]

FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

ENV MODEL_SERVICE_URL=http://localhost:${MODEL_SERVICE_PORT}

EXPOSE ${APP_PORT}

# Run the compiled Jar file when the container starts
ENTRYPOINT ["java", "-jar", "/app/target/frontend-0.0.1.jar"]

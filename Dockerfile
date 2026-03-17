# ==========================
# Stage 1: Build the app
# ==========================
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

ENV MAVEN_OPTS="-Xmx1024m"

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# ==========================
# Stage 2: Run the app
# ==========================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
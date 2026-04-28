# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: build
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Cache Maven dependencies before copying source
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

COPY src src
RUN ./mvnw clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

WORKDIR /app

# Dedicated non-root user for container security
RUN addgroup --system jrostering && adduser --system --ingroup jrostering jrostering

COPY --from=build --chown=jrostering:jrostering /app/target/JRostering-1.0-SNAPSHOT.jar app.jar

USER jrostering

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

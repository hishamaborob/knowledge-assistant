# ---- Build stage --------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# Cache dependency layer separately from source.
# This layer is invalidated only when pom.xml changes, not on source edits.
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B -q

COPY backend/src src
RUN mvn package -B -q -DskipTests

# ---- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user: ECS task role provides AWS access; no root needed.
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build --chown=app:app /build/target/*.jar app.jar

EXPOSE 8080

# wget is available on Alpine; curl is not installed by default.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

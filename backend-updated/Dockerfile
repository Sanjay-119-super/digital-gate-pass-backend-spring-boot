# ─────────────────────────────────────────────────────────────
# Stage 1: Build the JAR using Maven
# Uses the full JDK image so Maven can compile the source code.
# ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml first and download dependencies in a separate layer.
# This layer is cached by Docker so "mvn dependency:go-offline" only
# runs again when pom.xml changes — not on every source change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Copy the full source tree and build the executable JAR
COPY src ./src
RUN mvn -B -q -DskipTests package

# ─────────────────────────────────────────────────────────────
# Stage 2: Run the JAR using a minimal JRE image
# The final image has no compiler or source code — smaller and
# more secure than shipping the full build environment.
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for the process — running as root inside
# a container is a security anti-pattern.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/target/*.jar app.jar

# Change ownership so the non-root user can read the JAR
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# JVM flags:
#   -XX:+UseContainerSupport  — JVM respects cgroup memory limits (important in K8s)
#   -XX:MaxRAMPercentage=75   — Use up to 75% of the container's RAM for the heap
#   -Djava.security.egd      — Faster random seed source inside containers
ENTRYPOINT ["java",
  "-XX:+UseContainerSupport",
  "-XX:MaxRAMPercentage=75.0",
  "-Djava.security.egd=file:/dev/./urandom",
  "-jar", "/app/app.jar"]
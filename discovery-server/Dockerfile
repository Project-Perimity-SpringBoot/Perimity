# discovery-server image. Same two-stage build as the six services, with the
# /storage volume removed - a service registry writes nothing to disk.
#
# The healthcheck below hits /actuator/health, which is already exposed by
# management.endpoints.web.exposure.include=health,info in its properties.
# The six services use their own /ping endpoints; the registry has none.
#
# ==========================================================================
# WHY `mvn` AND NOT `./mvnw`
# ==========================================================================
# The usual Spring Boot Dockerfile runs ./mvnw. That cannot work in this repo:
# `.mvn/` is git-ignored in every service, so the wrapper jar was never
# committed, and guard-service and user-service have no mvnw script at all.
#
# The maven base image already contains Maven. Using it directly sidesteps the
# whole problem and removes a moving part.
#
# ==========================================================================
# WHY THE POM IS COPIED BEFORE THE SOURCE
# ==========================================================================
# Docker caches each layer. Copying pom.xml and resolving dependencies first
# means a source-only change reuses the cached dependency layer instead of
# re-downloading Spring Boot every build.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- run ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# curl is only here for the container healthcheck.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Do not run as root.
RUN useradd -r -u 1001 perimity

USER perimity

COPY --from=build /build/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
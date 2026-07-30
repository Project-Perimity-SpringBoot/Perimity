# Perimity service image. IDENTICAL for all six services - the build context is
# the service folder, so nothing here names guard-service. Copy it unchanged.
#
# ==========================================================================
# WHY `mvn` AND NOT `./mvnw`
# ==========================================================================
# The usual Spring Boot Dockerfile runs ./mvnw. That cannot work in this repo:
# `.mvn/` is git-ignored in every service, so the wrapper jar was never
# committed, and guard-service and user-service have no mvnw script at all.
#
# The maven base image already contains Maven. Using it directly sidesteps the
# whole problem and removes a moving part - the wrapper exists to guarantee a
# Maven version on a developer machine, which is exactly what pinning the base
# image already does here.
#
# ==========================================================================
# WHY THE POM IS COPIED BEFORE THE SOURCE
# ==========================================================================
# Docker caches each layer. Copying pom.xml and resolving dependencies first
# means a source-only change reuses the cached dependency layer instead of
# re-downloading Spring Boot every build. First build is slow; the rest are not.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Tests run in CI, not here. A Docker build that runs tests fails deploys for
# reasons unrelated to deployment, and doubles the build time on every retry.
RUN mvn -B clean package -DskipTests

# ---------- run ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# curl is only here for the container healthcheck. Without it Docker reports
# every container as "starting" forever and `depends_on: service_healthy` never
# releases whatever is waiting.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Do not run as root. If the process is ever compromised, this is the difference
# between a contained blast radius and one that owns the container.
RUN useradd -r -u 1001 perimity

# Shared object storage while STORAGE_TYPE=local.
#
# Four services write here - user (photos, documents), campus (logos), gatepass
# (bulk sheets, error reports) and qr (QR images, PDF passes) - and they read
# each other's files. Inside Docker each container has its own filesystem, so
# without a shared volume mounted at this path a PDF written by qr-service
# simply does not exist as far as gatepass-service is concerned, and everything
# is lost on `docker compose down` anyway.
#
# Created here, owned by perimity, BEFORE the USER switch: Docker seeds a fresh
# named volume with the ownership of the image directory it covers. Skip this and
# the volume arrives root-owned, the app cannot write to it, and every upload
# fails with a permission error that looks nothing like a permissions problem.
RUN mkdir -p /storage && chown perimity:perimity /storage

USER perimity

COPY --from=build /build/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

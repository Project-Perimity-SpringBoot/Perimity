# syntax=docker/dockerfile:1
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

#
# ==========================================================================
# WHY THE MAVEN CACHE IS MOUNTED RATHER THAN BAKED INTO A LAYER
# ==========================================================================
# Without the --mount=type=cache below, every one of the seven images resolves
# Spring Boot's whole dependency tree - roughly 200MB - into its own isolated
# layer. `docker compose build` runs them in PARALLEL by default, so a single
# home connection is asked for that download seven times at once. It does not
# fail cleanly; it times out, and looks like a hang.
#
# The cache mount gives all seven builds one shared /root/.m2. The first
# service populates it, the other six reuse it. Seven downloads become one, and
# a rebuild after a code change skips dependency resolution entirely instead of
# fetching it again.
#
# It also stops `dependency:go-offline` mattering. That goal is famously
# incomplete - it misses plugin dependencies - so `mvn package` re-downloads a
# chunk of what the previous step just fetched. With a shared cache, the second
# fetch is a cache hit rather than a network call.
#
# Requires BuildKit, which is the default in Docker Desktop and in Docker
# Engine 23+. The syntax directive on line 1 pins the Dockerfile frontend that
# understands the flag.

#
# sharing=locked, deliberately. The default is sharing=shared, which lets all
# seven builds write this one Maven repository CONCURRENTLY - they then contend
# inside Maven's own locking and the build appears to hang. `locked` makes them
# queue explicitly, which is honest about what is happening.
#
# The practical consequence: populate the cache with ONE build first
#   docker compose build auth-service
# then `docker compose up -d --build` - the other six are cache hits and the
# parallelism costs nothing.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B dependency:go-offline

COPY src ./src
# Tests run in CI, not here. A Docker build that runs tests fails deploys for
# reasons unrelated to deployment, and doubles the build time on every retry.
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B clean package -DskipTests

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

# syntax=docker/dockerfile:1
# discovery-server image. Same two-stage build as the six services, with the
# /storage volume removed - a service registry writes nothing to disk.
#
# The container healthcheck lives in docker-compose.yml and hits "/", the Eureka
# dashboard, NOT /actuator/health. pom.xml declares only
# spring-cloud-starter-netflix-eureka-server, with no spring-boot-starter-actuator,
# so management.endpoints.web.exposure.include=health,info in the properties file
# has nothing to expose. A healthcheck on a path that 404s can never pass, and
# every service with `depends_on: discovery-server: service_healthy` would wait
# on it forever.
#
# ==========================================================================
# WHY `mvn` AND NOT `./mvnw`
# ==========================================================================
# The usual Spring Boot Dockerfile runs ./mvnw. That cannot work in this repo:
# `.mvn/` is git-ignored in every service, so the wrapper jar was never
# committed, and guard-service, user-service and this module have no mvnw script
# at all.
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
# BOTH SIDES OF A MERGE, because they solved different problems.
#
# dependency:resolve rather than dependency:go-offline: go-offline hit SSL
# failures on some networks, and it is famously incomplete anyway - it misses
# plugin dependencies, so `mvn package` re-downloads part of what it just
# fetched. resolve + resolve-plugins is the honest version of the same step.
#
# The cache mount is kept. It is orthogonal to which goal runs: it is what
# makes seven parallel builds share ONE /root/.m2 instead of each pulling
# Spring Boot's ~200MB tree down a single home connection at the same time.
# Dropping it is what made `docker compose build` look like it hung.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B dependency:resolve dependency:resolve-plugins

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
#
# No /storage here, unlike the six services. Four of them share a volume for
# photos, documents, bulk sheets and generated passes; a registry holds only
# in-memory registration state and would never touch it.
RUN useradd -r -u 1001 perimity

USER perimity

COPY --from=build /build/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

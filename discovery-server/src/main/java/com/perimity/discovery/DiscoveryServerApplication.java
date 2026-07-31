package com.perimity.discovery;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * The Perimity service registry.
 *
 * Every other service announces itself here on startup and sends a heartbeat
 * every thirty seconds. A service that stops sending heartbeats is dropped.
 *
 * Dashboard: http://localhost:8761
 *
 * WHY THIS EXISTS, honestly: at six services on one Compose network, Docker DNS
 * already resolves service names, and the registry is not solving a problem we
 * have today. It earns its place for two other reasons - it is the standard
 * pattern a Spring Cloud microservices system is expected to demonstrate, and
 * the dashboard makes the architecture visible in a way a diagram cannot.
 *
 * It stays optional. Every service can still be run with eureka.client.enabled
 * =false and will fall back to the plain URLs it used before, so the registry
 * being down never stops the platform.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        // Same first line as every other service. Postgres 16 rejects the
        // Asia/Calcutta alias, so the whole platform standardises on this.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}

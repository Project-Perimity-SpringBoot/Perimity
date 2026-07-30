package com.perimity.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Catches the failures that only appear at startup: a missing bean, a JWT
 * secret too short for HS256, a security config that cannot be built, an entity
 * Hibernate refuses to map, a storage implementation that neither condition
 * selected.
 *
 * That last one is worth having. LocalFileStorageService and S3StorageService
 * are chosen by @ConditionalOnProperty; get the property name wrong in either
 * and NO bean is created, which surfaces as an unsatisfied dependency here
 * rather than on the first upload in a demo.
 *
 * NOTE the package. This is com.perimity.user, the same package as
 * UserServiceApplication. @SpringBootTest searches UPWARD from the test's own
 * package for a @SpringBootConfiguration, so a test sitting in
 * com.perimity.user_service would never find the application class and would
 * fail with "Unable to find a @SpringBootConfiguration" - which reads like a
 * broken build rather than a misplaced file.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

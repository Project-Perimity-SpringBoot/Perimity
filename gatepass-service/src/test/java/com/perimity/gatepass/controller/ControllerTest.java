package com.perimity.gatepass.controller;

import com.perimity.gatepass.exception.GlobalExceptionHandler;
import com.perimity.gatepass.security.InternalApiKeyFilter;
import com.perimity.gatepass.security.JwtAuthenticationFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * One annotation for every controller test in this service.
 *
 * ==========================================================================
 *  WHY THIS EXISTS RATHER THAN REPEATING FIVE ANNOTATIONS PER TEST CLASS
 * ==========================================================================
 *
 * A plain @WebMvcTest on any controller in this service fails to start, with
 * an error that does not name the real problem:
 *
 *   No qualifying bean of type 'JwtTokenReader' available
 *
 * The cause is not obvious. @WebMvcTest does not load the whole application -
 * it loads controllers and a short list of web-layer types. That list INCLUDES
 * anything implementing jakarta.servlet.Filter. JwtAuthenticationFilter is a
 * Filter, so it is pulled in; JwtTokenReader is a plain @Component, so it is
 * not; and the filter cannot be constructed without it.
 *
 * Three ways out, and the reasoning for the one chosen:
 *
 *   1. @MockBean JwtTokenReader on every test class.
 *      Works, but it is a mock that exists only to satisfy a bean that these
 *      tests then disable anyway. It also has to be repeated everywhere and
 *      the next person will not know why it is there.
 *
 *   2. Load the security chain properly and authenticate each request.
 *      Correct for testing AUTHORISATION, and it should be done - in its own
 *      test class, against SecurityConfig. Doing it here means every
 *      validation test needs a JWT, and a 401 from a missing token looks
 *      identical to the 400 these tests are trying to assert. The failure
 *      mode is a test that passes for the wrong reason.
 *
 *   3. Exclude the security filters from the context entirely. <- chosen
 *      These tests exist to prove that @Valid is wired and that constraints
 *      actually run. Security is a different question and belongs in a
 *      different test.
 *
 * WHAT THIS DELIBERATELY DOES NOT COVER: @PreAuthorize. With
 * SecurityAutoConfiguration excluded, method security is not active, so a test
 * here can call an endpoint its role would never be allowed to reach. That is
 * fine and intentional - but it means a green run here is NOT evidence that
 * the role checks work. Do not read it as such in the viva.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest(
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
public @interface ControllerTest {

    /** The controller under test. Mirrors @WebMvcTest#controllers. */
    @org.springframework.core.annotation.AliasFor(annotation = WebMvcTest.class,
            attribute = "controllers")
    Class<?>[] value() default {};
}

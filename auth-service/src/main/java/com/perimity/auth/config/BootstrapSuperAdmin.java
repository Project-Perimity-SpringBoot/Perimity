package com.perimity.auth.config;

import com.perimity.auth.entity.User;
import com.perimity.auth.entity.enums.Role;
import com.perimity.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first Super Admin, once, if there is not one already.
 *
 * WHY THIS IS DAY 12 WORK AND NOT DAY 20 SEED DATA.
 *
 * The Day 12 gate is "the whole happy path works WITHOUT MANUAL DATABASE
 * EDITS". Until now the only way to obtain an admin token was to INSERT a row
 * with psql by hand, because creating an admin needs an admin token - a
 * circular dependency that no amount of API testing can break. So the gate was
 * unreachable for a reason that had nothing to do with any of the six services
 * being wrong.
 *
 * This is deliberately NOT the Day 20 DataSeeder. It creates one account and
 * nothing else: no campuses, no departments, no demo students. Those belong to
 * the services that own them, and seeding a campus from here would put
 * campus-service's data in auth-service's startup path.
 *
 * SAFE TO LEAVE IN FOR PRODUCTION, which is the point of the guard below:
 *   - runs only when NO Super Admin exists, so it can never overwrite one and
 *     never resurrects a deleted account
 *   - the password comes from the environment, never from code
 *   - mustChangePassword is true, so the seeded credential is dead on first use
 *
 * A "delete this before deploying" seeder is one nobody deletes. This one is
 * written to survive.
 */
@Component
public class BootstrapSuperAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapSuperAdmin.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    /**
     * Read straight from the repo-root .env, which application.properties
     * already imports. No new property names to keep in step with anything.
     */
    public BootstrapSuperAdmin(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               @Value("${SUPER_ADMIN_EMAIL:}") String email,
                               @Value("${SUPER_ADMIN_PASSWORD:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD in .env - skipping bootstrap. "
                    + "You will have no way to obtain an admin token.");
            return;
        }

        // The guard. Not "is the table empty" - a platform with visitors but no
        // admin is exactly the state that needs fixing, and would be skipped.
        if (!userRepository.findByRole(Role.SUPER_ADMIN).isEmpty()) {
            log.debug("A Super Admin already exists, bootstrap skipped.");
            return;
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            // Somebody registered as a visitor on that address first. Refuse
            // rather than silently promote them - a role change is not
            // something a startup task should make on its own.
            log.error("SUPER_ADMIN_EMAIL {} already belongs to a non-admin account. "
                    + "Bootstrap skipped. Use a different address in .env.", email);
            return;
        }

        User admin = userRepository.save(User.builder()
                .email(email.toLowerCase())
                .name("Super Admin")
                .role(Role.SUPER_ADMIN)
                // NULL for SUPER_ADMIN, who is platform-wide. The entity's own
                // @AssertTrue rejects the row otherwise.
                .campusId(null)
                .passwordHash(passwordEncoder.encode(password))
                .mustChangePassword(true)
                .active(true)
                .build());

        log.warn("=======================================================================");
        log.warn(" Created the first Super Admin: {} (id {})", admin.getEmail(), admin.getId());
        log.warn(" The password came from SUPER_ADMIN_PASSWORD in .env.");
        log.warn(" mustChangePassword is set - change it via POST /api/auth/password/change");
        log.warn("=======================================================================");

        if ("change_me_on_first_login".equals(password)) {
            log.warn(" SUPER_ADMIN_PASSWORD is still the placeholder from .env.example. "
                    + "Fine today; not fine on Day 22.");
        }
    }
}

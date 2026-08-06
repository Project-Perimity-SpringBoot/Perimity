package com.perimity.auth.repository;

import com.perimity.auth.entity.User;
import com.perimity.auth.entity.enums.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Email is the universal key. Always look up case-insensitively. */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCaseAndActiveTrue(String email);

    /** Campus Admin managing their own staff. Never crosses campus boundaries. */
    Page<User> findByCampusIdAndRoleOrderByNameAsc(Long campusId, Role role, Pageable pageable);

    Page<User> findByCampusIdOrderByNameAsc(Long campusId, Pageable pageable);

    /**
     * Accounts on a campus limited to a set of roles.
     *
     * Needed because visibility is now per-actor: a Campus Admin sees FACULTY
     * and GUARD, not everyone on the campus. Filtering in Java after loading
     * the page would silently return short pages - the database has to do it.
     */
    Page<User> findByCampusIdAndRoleInOrderByNameAsc(
            Long campusId, java.util.Collection<Role> roles, Pageable pageable);

    List<User> findByRole(Role role);

    Page<User> findByRoleOrderByNameAsc(Role role, Pageable pageable);

    long countByCampusIdAndRoleAndActiveTrue(Long campusId, Role role);

    long countByRole(Role role);

    // ------------------------------------------------------ Day 10, bulk

    /**
     * Every identity that already exists among a sheet's worth of emails, in
     * one query.
     *
     * This is the mixed-attendee problem from Event_Bulk_Design.md turned into
     * a single round trip: 600 rows of which roughly 100 are already members,
     * and the faculty does not know which. One query answers it for the whole
     * sheet.
     *
     * NOT IgnoreCase, deliberately. Every write path in this service lowercases
     * the address before saving (create, registerVisitor, resolveOrCreate), so
     * the column only ever holds lowercase. The caller lowercases its side once
     * and this stays a plain indexed IN lookup. A case-insensitive variant here
     * would force lower(email) on every row and lose the unique index.
     */
    List<User> findByEmailIn(Collection<String> emails);
}

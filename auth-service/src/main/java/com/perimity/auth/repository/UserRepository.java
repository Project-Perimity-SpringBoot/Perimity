package com.perimity.auth.repository;

import com.perimity.auth.entity.User;
import com.perimity.auth.entity.enums.Role;
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

    List<User> findByRole(Role role);

    long countByCampusIdAndRoleAndActiveTrue(Long campusId, Role role);

    long countByRole(Role role);
}

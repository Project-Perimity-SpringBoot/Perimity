package com.perimity.campus.repository;

import com.perimity.campus.entity.Campus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampusRepository extends JpaRepository<Campus, Long> {

    Optional<Campus> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Campus> findByActiveTrueOrderByNameAsc();

    Optional<Campus> findByAdminUserId(Long adminUserId);

    long countByActiveTrue();
}

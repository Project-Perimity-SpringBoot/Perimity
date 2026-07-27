package com.perimity.guard.repository;

import com.perimity.guard.document.GuardSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GuardSessionRepository extends MongoRepository<GuardSession, String> {

    /** The guard's open shift. Every scan reads the gate from this. */
    Optional<GuardSession> findByGuardIdAndActiveTrue(Long guardId);

    List<GuardSession> findByCampusIdAndActiveTrue(Long campusId);

    List<GuardSession> findByGuardIdOrderByStartedAtDesc(Long guardId);

    boolean existsByGuardIdAndActiveTrue(Long guardId);
}

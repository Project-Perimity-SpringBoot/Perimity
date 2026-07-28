package com.perimity.guard.repository;

import com.perimity.guard.document.ScanSession;
import com.perimity.guard.document.enums.SessionState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScanSessionRepository extends MongoRepository<ScanSession, String> {

    /** The guard's open shift. Every scan reads the gate from this. */
    Optional<ScanSession> findByGuardUserIdAndState(Long guardUserId, SessionState state);

    List<ScanSession> findByCampusIdAndState(Long campusId, SessionState state);

    List<ScanSession> findByGuardUserIdOrderByStartedAtDesc(Long guardUserId);

    boolean existsByGuardUserIdAndState(Long guardUserId, SessionState state);
}

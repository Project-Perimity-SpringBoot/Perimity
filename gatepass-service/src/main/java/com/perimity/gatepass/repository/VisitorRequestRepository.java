package com.perimity.gatepass.repository;

import com.perimity.gatepass.entity.VisitorRequest;
import com.perimity.gatepass.entity.enums.RequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitorRequestRepository extends JpaRepository<VisitorRequest, Long> {

    /** Screen 7 - Approval Queue, scoped to one campus. */
    Page<VisitorRequest> findByCampusIdAndStatusOrderByCreatedAtAsc(
            Long campusId, RequestStatus status, Pageable pageable);

    /** A host faculty seeing only the requests addressed to them. */
    Page<VisitorRequest> findByHostUserIdAndStatusOrderByCreatedAtAsc(
            Long hostUserId, RequestStatus status, Pageable pageable);

    /** Visitor self-service - "show me what I submitted". */
    List<VisitorRequest> findByVisitorEmailOrderByCreatedAtDesc(String visitorEmail);

    Optional<VisitorRequest> findByIdAndCampusId(Long id, Long campusId);

    /** Stops the same visitor spamming duplicate pending requests. */
    boolean existsByVisitorEmailAndCampusIdAndStatus(
            String visitorEmail, Long campusId, RequestStatus status);

    long countByCampusIdAndStatus(Long campusId, RequestStatus status);
}

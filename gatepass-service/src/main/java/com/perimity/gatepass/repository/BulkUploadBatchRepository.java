package com.perimity.gatepass.repository;

import com.perimity.gatepass.entity.BulkUploadBatch;
import com.perimity.gatepass.entity.enums.BatchStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BulkUploadBatchRepository extends JpaRepository<BulkUploadBatch, Long> {

    /** Screen 10 - Bulk Progress history for one campus. */
    Page<BulkUploadBatch> findByCampusIdOrderByCreatedAtDesc(Long campusId, Pageable pageable);

    List<BulkUploadBatch> findByUploadedByOrderByCreatedAtDesc(Long uploadedBy);

    Optional<BulkUploadBatch> findByIdAndCampusId(Long id, Long campusId);

    List<BulkUploadBatch> findByStatus(BatchStatus status);

    List<BulkUploadBatch> findByEventId(Long eventId);
}

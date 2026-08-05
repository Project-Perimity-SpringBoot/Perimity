package com.perimity.user.repository;

import com.perimity.user.entity.StudentImportBatch;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentImportBatchRepository extends JpaRepository<StudentImportBatch, Long> {

    /**
     * Campus-scoped read. Always prefer this over findById for anything a user
     * asked for - a bare findById would let one campus poll another campus's
     * import progress by guessing a small integer.
     */
    Optional<StudentImportBatch> findByIdAndCampusId(Long id, Long campusId);

    /** The history list, newest first - the opposite of the review queue. */
    Page<StudentImportBatch> findByCampusIdOrderByIdDesc(Long campusId, Pageable pageable);
}

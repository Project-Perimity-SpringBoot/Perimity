package com.perimity.user.repository;

import com.perimity.user.entity.StudentImportRow;
import com.perimity.user.entity.enums.ImportRowOutcome;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentImportRowRepository extends JpaRepository<StudentImportRow, Long> {

    /**
     * Ordered by rowNumber, so the preview matches the spreadsheet on screen.
     * A different order would make "row 47" mean two different things depending
     * on where somebody read it.
     */
    Page<StudentImportRow> findByBatchIdOrderByRowNumberAsc(Long batchId, Pageable pageable);

    /** Unpaged, for the confirm pass over the whole batch. */
    List<StudentImportRow> findByBatchIdOrderByRowNumberAsc(Long batchId);

    /**
     * The rejected rows, which is what faculty actually want to see first - a
     * preview of two hundred fine rows is not worth reading, and three broken
     * ones are.
     */
    Page<StudentImportRow> findByBatchIdAndOutcomeOrderByRowNumberAsc(
            Long batchId, ImportRowOutcome outcome, Pageable pageable);

    long countByBatchIdAndOutcome(Long batchId, ImportRowOutcome outcome);

    void deleteByBatchId(Long batchId);
}

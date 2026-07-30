package com.perimity.auth.repository;

import com.perimity.auth.entity.BlocklistEntry;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BlocklistEntryRepository extends JpaRepository<BlocklistEntry, Long> {

    /**
     * The screening check, run on every registration and every bulk-upload row.
     * Scoped by campus - barred at one campus is not barred at another.
     */
    boolean existsByCampusIdAndEmailIgnoreCase(Long campusId, String email);

    boolean existsByCampusIdAndPhone(Long campusId, String phone);

    /** Screen 17 - Blocklist, with search. */
    Page<BlocklistEntry> findByCampusIdOrderByCreatedAtDesc(Long campusId, Pageable pageable);

    Page<BlocklistEntry> findByCampusIdAndEmailContainingIgnoreCase(
            Long campusId, String emailFragment, Pageable pageable);

    Optional<BlocklistEntry> findByIdAndCampusId(Long id, Long campusId);

    long countByCampusId(Long campusId);

    // ------------------------------------------------------ Day 10, bulk

    /**
     * The whole campus blocklist as a set of emails, in one query.
     *
     * WHY LOAD IT ALL rather than ask per row: the two exists() methods above
     * cost two round trips PER ROW. A 600-row sheet is 1,200 queries, and the
     * faculty is watching a spinner for all of them. These two methods make it
     * two queries for any sheet size, after which every row is a hash lookup.
     *
     * The tradeoff is honest and worth being able to state: this loads the
     * entire blocklist into memory. A campus blocklist is a list of people an
     * administrator typed a reason for by hand - realistically tens, plausibly
     * hundreds. At that size this is trivially cheaper. If a campus ever had a
     * blocklist large enough for this to hurt, the right answer would be a
     * single query with the sheet's emails in an IN clause, not 1,200 queries.
     *
     * Lowercased in SQL so the caller can lowercase its side once and compare
     * with plain Set.contains, instead of a case-insensitive scan per row.
     */
    @Query("select lower(b.email) from BlocklistEntry b "
            + "where b.campusId = :campusId and b.email is not null")
    Set<String> findBlockedEmails(@Param("campusId") Long campusId);

    @Query("select b.phone from BlocklistEntry b "
            + "where b.campusId = :campusId and b.phone is not null")
    Set<String> findBlockedPhones(@Param("campusId") Long campusId);
}

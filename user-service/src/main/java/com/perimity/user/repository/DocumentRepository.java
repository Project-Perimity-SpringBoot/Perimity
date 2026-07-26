package com.perimity.user.repository;

import com.perimity.user.entity.Document;
import com.perimity.user.entity.enums.DocumentType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Document> findByUserIdAndDocType(Long userId, DocumentType docType);

    List<Document> findByUserIdAndVerifiedFalse(Long userId);
}

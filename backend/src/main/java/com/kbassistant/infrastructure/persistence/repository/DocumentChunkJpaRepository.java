package com.kbassistant.infrastructure.persistence.repository;

import com.kbassistant.infrastructure.persistence.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkJpaRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndex(UUID documentId);

    void deleteByDocumentId(UUID documentId);

    int countByDocumentId(UUID documentId);
}

package com.kbassistant.infrastructure.persistence.repository;

import com.kbassistant.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    List<DocumentEntity> findByStatus(String status);

    List<DocumentEntity> findByCreatedBy(String createdBy);
}

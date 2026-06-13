package com.kbassistant.infrastructure.persistence.adapter;

import com.kbassistant.domain.exception.DocumentNotFoundException;
import com.kbassistant.domain.model.*;
import com.kbassistant.domain.port.out.DocumentRepository;
import com.kbassistant.infrastructure.persistence.entity.DocumentEntity;
import com.kbassistant.infrastructure.persistence.repository.DocumentJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class DocumentJpaAdapter implements DocumentRepository {

    private final DocumentJpaRepository jpaRepository;

    public DocumentJpaAdapter(DocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Document save(Document document) {
        DocumentEntity entity = toEntity(document);
        DocumentEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findById(DocumentId id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void delete(DocumentId id) {
        if (!jpaRepository.existsById(id.value())) {
            throw new DocumentNotFoundException(id);
        }
        jpaRepository.deleteById(id.value());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(DocumentId id) {
        return jpaRepository.existsById(id.value());
    }

    // =========================================================================
    // Mapping — domain ↔ entity
    // =========================================================================

    private Document toDomain(DocumentEntity e) {
        return Document.reconstitute(
                DocumentId.of(e.getId()),
                e.getName(),
                e.getOriginalFilename(),
                MimeType.fromMimeString(e.getMimeType())
                        .orElseThrow(() -> new IllegalStateException("Unknown mime type in DB: " + e.getMimeType())),
                e.getFileSizeBytes(),
                e.getCreatedBy(),
                DocumentStatus.valueOf(e.getStatus()),
                e.getS3Key(),
                e.getChunkCount(),
                e.getErrorMessage(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private DocumentEntity toEntity(Document d) {
        var e = new DocumentEntity();
        e.setId(d.id().value());
        e.setName(d.name());
        e.setOriginalFilename(d.originalFilename());
        e.setMimeType(d.mimeType().mimeString());
        e.setFileSizeBytes(d.fileSizeBytes());
        e.setCreatedBy(d.createdBy());
        e.setStatus(d.status().name());
        e.setS3Key(d.s3Key());
        e.setChunkCount(d.chunkCount());
        e.setErrorMessage(d.errorMessage());
        e.setCreatedAt(d.createdAt());
        e.setUpdatedAt(d.updatedAt());
        return e;
    }
}

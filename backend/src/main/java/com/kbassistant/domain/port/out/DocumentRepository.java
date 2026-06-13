package com.kbassistant.domain.port.out;

import com.kbassistant.domain.model.Document;
import com.kbassistant.domain.model.DocumentId;

import java.util.List;
import java.util.Optional;

/**
 * Port for Document persistence.
 * Implemented by DocumentJpaAdapter in infrastructure.
 *
 * This is a domain repository interface — NOT a Spring Data repository.
 * It works with domain objects (Document), not JPA entities.
 * The mapping between domain Document and JPA DocumentEntity happens in the adapter.
 */
public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(DocumentId id);

    List<Document> findAll();

    void delete(DocumentId id);

    boolean existsById(DocumentId id);
}

package com.kbassistant.domain.port.out;

import com.kbassistant.domain.model.DocumentChunk;
import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.ScoredChunk;
import com.kbassistant.domain.model.SimilaritySearchRequest;

import java.util.List;

/**
 * Port for vector storage and similarity search.
 * Implemented by PgVectorAdapter in infrastructure.
 *
 * Kept separate from DocumentRepository because the vector operations
 * use a different execution path (JDBC + native SQL) from the document
 * metadata operations (JPA). Merging them would force the use of the
 * heavier JPA path for high-frequency similarity searches.
 */
public interface VectorStorePort {

    void saveChunks(List<DocumentChunk> chunks);

    List<ScoredChunk> similaritySearch(SimilaritySearchRequest request);

    void deleteByDocumentId(DocumentId documentId);

    int countByDocumentId(DocumentId documentId);
}

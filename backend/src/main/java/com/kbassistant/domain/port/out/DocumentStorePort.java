package com.kbassistant.domain.port.out;

import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.MimeType;

/**
 * Port for durable file storage.
 * Implemented by S3DocumentAdapter in infrastructure.
 *
 * Returns an opaque storage key — the domain does not know or care
 * that the backing store is S3. Swapping to GCS or Azure Blob means
 * writing one new adapter, not touching DocumentService.
 */
public interface DocumentStorePort {

    /**
     * Stores the document bytes and returns the storage key for later retrieval.
     * The key format is an implementation detail of the adapter.
     */
    String store(DocumentId id, byte[] content, String filename, MimeType mimeType);

    /**
     * Retrieves raw bytes by the storage key returned from store().
     */
    byte[] retrieve(String storageKey);

    /**
     * Deletes the stored file. Idempotent — no-op if key does not exist.
     */
    void delete(String storageKey);
}

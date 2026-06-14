package com.kbassistant.infrastructure.storage;

import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.MimeType;
import com.kbassistant.domain.port.out.DocumentStorePort;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
public class S3DocumentAdapter implements DocumentStorePort {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public S3DocumentAdapter(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    @Override
    public String store(DocumentId id, byte[] content, String filename, MimeType mimeType) {
        String key = buildKey(id, filename);

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(s3Properties.getBucketName())
                        .key(key)
                        .contentType(mimeType.mimeString())
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content)
        );

        return key;
    }

    @Override
    public byte[] retrieve(String storageKey) {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(s3Properties.getBucketName())
                        .key(storageKey)
                        .build()
        ).asByteArray();
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(s3Properties.getBucketName())
                        .key(storageKey)
                        .build()
        );
    }

    // Key format: documents/{documentId}/{filename}
    // Groups all files for a document under one prefix for easy prefix-based deletion.
    private String buildKey(DocumentId id, String filename) {
        return "documents/" + id.value() + "/" + filename;
    }
}

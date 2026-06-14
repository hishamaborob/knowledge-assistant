package com.kbassistant.infrastructure.storage;

import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.MimeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3DocumentAdapterTest {

    @Mock S3Client s3Client;

    S3Properties s3Properties;
    S3DocumentAdapter adapter;

    @BeforeEach
    void setUp() {
        s3Properties = new S3Properties();
        s3Properties.setBucketName("test-bucket");
        adapter = new S3DocumentAdapter(s3Client, s3Properties);
    }

    @Test
    void store_buildsCorrectKeyAndCallsPutObject() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        DocumentId id = DocumentId.generate();
        String key = adapter.store(id, "data".getBytes(), "report.pdf", MimeType.PDF);

        assertThat(key).isEqualTo("documents/" + id.value() + "/report.pdf");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo(key);
        assertThat(captor.getValue().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void retrieve_callsGetObjectAsBytes_andReturnsBytes() {
        byte[] expected = "file content".getBytes();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), expected));

        byte[] result = adapter.retrieve("documents/id/file.txt");

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("documents/id/file.txt");
    }

    @Test
    void delete_callsDeleteObjectWithCorrectKeyAndBucket() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        adapter.delete("documents/id/file.txt");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("documents/id/file.txt");
    }

    @Test
    void delete_nullKey_skipsS3Call() {
        adapter.delete(null);
        verifyNoInteractions(s3Client);
    }

    @Test
    void delete_blankKey_skipsS3Call() {
        adapter.delete("  ");
        verifyNoInteractions(s3Client);
    }
}

package com.kbassistant.api.controller;

import com.kbassistant.api.dto.DocumentResponse;
import com.kbassistant.api.exception.GlobalExceptionHandler;
import com.kbassistant.application.command.UploadDocumentCommand;
import com.kbassistant.application.service.DocumentService;
import com.kbassistant.domain.exception.DocumentNotFoundException;
import com.kbassistant.domain.model.Document;
import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.MimeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@Import(GlobalExceptionHandler.class)
class DocumentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean DocumentService documentService;

    @Test
    void upload_validTxtFile_returns202WithDocumentIdAndStoredStatus() throws Exception {
        Document doc = storedDocument("test.txt", MimeType.TEXT);
        when(documentService.upload(any(UploadDocumentCommand.class))).thenReturn(doc);

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "test.txt", "text/plain", "Hello world".getBytes())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("STORED"))
                .andExpect(jsonPath("$.mimeType").value("text/plain"));
    }

    @Test
    void upload_validPdf_returns202() throws Exception {
        Document doc = storedDocument("report.pdf", MimeType.PDF);
        when(documentService.upload(any(UploadDocumentCommand.class))).thenReturn(doc);

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46})))
                .andExpect(status().isAccepted());
    }

    @Test
    void upload_unsupportedExtension_returns415() throws Exception {
        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "script.exe", "application/octet-stream", new byte[]{0x4D, 0x5A})))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(documentService);
    }

    @Test
    void upload_noExtensionNoContentType_returns415() throws Exception {
        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "unknownfile", null, "data".getBytes())))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void findById_existingDocument_returns200WithBody() throws Exception {
        Document doc = Document.create("My Doc", "doc.pdf", MimeType.PDF, 1024L, "user");
        when(documentService.findById(doc.id())).thenReturn(doc);

        mockMvc.perform(get("/documents/" + doc.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Doc"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void findById_nonExistentId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.findById(DocumentId.of(id)))
                .thenThrow(new DocumentNotFoundException(DocumentId.of(id)));

        mockMvc.perform(get("/documents/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_returns200WithList() throws Exception {
        when(documentService.findAll()).thenReturn(List.of(
                Document.create("A", "a.txt", MimeType.TEXT, 10L, "u"),
                Document.create("B", "b.pdf", MimeType.PDF, 20L, "u")
        ));

        mockMvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void delete_existingDocument_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(documentService).delete(DocumentId.of(id));

        mockMvc.perform(delete("/documents/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_nonExistentDocument_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DocumentNotFoundException(DocumentId.of(id)))
                .when(documentService).delete(DocumentId.of(id));

        mockMvc.perform(delete("/documents/" + id))
                .andExpect(status().isNotFound());
    }

    private Document storedDocument(String filename, MimeType mimeType) {
        Document doc = Document.create(filename, filename, mimeType, 100L, "anonymous");
        doc.markStored("documents/" + doc.id().value() + "/" + filename);
        return doc;
    }
}

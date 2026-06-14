package com.kbassistant.api.controller;

import com.kbassistant.api.dto.DocumentResponse;
import com.kbassistant.application.command.UploadDocumentCommand;
import com.kbassistant.application.service.DocumentService;
import com.kbassistant.domain.exception.UnsupportedMimeTypeException;
import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.MimeType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
@Tag(name = "Documents", description = "Document upload and management")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Upload a document for ingestion")
    public DocumentResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name
    ) throws IOException {
        MimeType mimeType = detectMimeType(file);
        String displayName = (name != null && !name.isBlank()) ? name : file.getOriginalFilename();

        // Reads entire file into memory — acceptable for 50MB limit.
        // Phase 10: replace with streaming multipart upload to S3 via TransferManager.
        UploadDocumentCommand command = new UploadDocumentCommand(
                displayName,
                file.getOriginalFilename(),
                file.getBytes(),
                mimeType,
                file.getSize(),
                "anonymous"  // Phase 10: replace with JWT subject from SecurityContext
        );

        return DocumentResponse.from(documentService.upload(command));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document status and metadata by ID")
    public DocumentResponse findById(@PathVariable UUID id) {
        return DocumentResponse.from(documentService.findById(DocumentId.of(id)));
    }

    @GetMapping
    @Operation(summary = "List all documents")
    public List<DocumentResponse> findAll() {
        return documentService.findAll().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document and its stored file")
    public void delete(@PathVariable UUID id) {
        documentService.delete(DocumentId.of(id));
    }

    private MimeType detectMimeType(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String extension = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1)
                : "";

        // Extension wins over Content-Type: clients can send wrong Content-Type headers.
        return MimeType.fromExtension(extension)
                .or(() -> MimeType.fromMimeString(
                        file.getContentType() != null ? file.getContentType() : ""))
                .orElseThrow(() -> new UnsupportedMimeTypeException(
                        "extension='" + extension + "', content-type='" + file.getContentType() + "'"));
    }
}

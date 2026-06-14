package com.kbassistant.infrastructure.extraction;

import com.kbassistant.domain.model.MimeType;
import com.kbassistant.domain.port.out.TextExtractorPort;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class TextExtractorAdapter implements TextExtractorPort {

    @Override
    public String extract(byte[] content, MimeType mimeType) {
        return switch (mimeType) {
            case PDF -> extractPdf(content);
            case TEXT, MARKDOWN -> new String(content, StandardCharsets.UTF_8);
        };
    }

    private String extractPdf(byte[] content) {
        // PDFBox 3.x uses Loader.loadPDF() — PDDocument.load() is deprecated in 3.x.
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }
}

package com.kbassistant.infrastructure.extraction;

import com.kbassistant.domain.model.MimeType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class TextExtractorAdapterTest {

    private final TextExtractorAdapter adapter = new TextExtractorAdapter();

    @Test
    void extract_plainText_returnsUtf8Content() {
        byte[] content = "Hello, world!".getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.extract(content, MimeType.TEXT)).isEqualTo("Hello, world!");
    }

    @Test
    void extract_markdown_returnsRawContent() {
        String md = "# Title\n\nSome **bold** text.";
        byte[] content = md.getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.extract(content, MimeType.MARKDOWN)).isEqualTo(md);
    }

    @Test
    void extract_pdf_returnsNonEmptyTextContainingExpectedContent() throws Exception {
        byte[] pdfBytes = buildPdf("Hello from PDFBox");
        String result = adapter.extract(pdfBytes, MimeType.PDF);
        assertThat(result).isNotBlank();
        assertThat(result).contains("Hello");
    }

    @Test
    void extract_corruptPdf_throwsRuntimeException() {
        byte[] garbage = "not a pdf".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> adapter.extract(garbage, MimeType.PDF))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to extract text from PDF");
    }

    private byte[] buildPdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                // PDFBox 3.x: PDType1Font requires Standard14Fonts.FontName constructor
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}

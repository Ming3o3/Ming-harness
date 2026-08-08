package org.mingharness.context;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.mingharness.common.BusinessException;
import org.mingharness.config.DocumentImportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentFileParserTests {

    private KnowledgeDocumentFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new KnowledgeDocumentFileParser(new DocumentImportProperties(2_000_000, 100_000));
    }

    @Test
    void shouldExtractPdfTextAndPageCount() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0))) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Vector retrieval guide");
                content.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            bytes = output.toByteArray();
        }
        byte[] prefixed = new byte[32 + bytes.length];
        System.arraycopy(bytes, 0, prefixed, 32, bytes.length);

        ParsedKnowledgeDocument result = parser.parse(new MockMultipartFile(
                "file", "guide.pdf", "application/pdf", prefixed));

        assertEquals("PDF", result.format());
        assertEquals(1, result.pageCount());
        assertTrue(result.text().contains("Vector retrieval guide"));
    }

    @Test
    void shouldExtractDocxParagraphs() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("DOCX knowledge base");
            document.createParagraph().createRun().setText("Chunk this paragraph too");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            bytes = output.toByteArray();
        }

        ParsedKnowledgeDocument result = parser.parse(new MockMultipartFile(
                "file", "guide.docx", "application/octet-stream", bytes));

        assertEquals("DOCX", result.format());
        assertEquals(0, result.pageCount());
        assertTrue(result.text().contains("DOCX knowledge base"));
        assertTrue(result.text().contains("Chunk this paragraph too"));
    }

    @Test
    void shouldRejectUnsupportedExtensionAndMismatchedMagic() {
        MockMultipartFile text = new MockMultipartFile("file", "guide.txt", "text/plain", "hello".getBytes());
        BusinessException unsupported = assertThrows(BusinessException.class, () -> parser.parse(text));
        assertEquals("DOCUMENT_FORMAT_UNSUPPORTED", unsupported.getCode());

        MockMultipartFile mismatched = new MockMultipartFile("file", "guide.pdf", "application/pdf", "not pdf".getBytes());
        BusinessException mismatch = assertThrows(BusinessException.class, () -> parser.parse(mismatched));
        assertEquals("DOCUMENT_FORMAT_MISMATCH", mismatch.getCode());
    }

    @Test
    void shouldRejectEmptyExtractedText() {
        byte[] emptyDocx;
        try (XWPFDocument document = new XWPFDocument()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            emptyDocx = output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        BusinessException exception = assertThrows(BusinessException.class, () -> parser.parse(
                new MockMultipartFile("file", "empty.docx", null, emptyDocx)));
        assertEquals("DOCUMENT_TEXT_EMPTY", exception.getCode());
    }
}

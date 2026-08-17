package org.mingharness.context;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.mingharness.common.BusinessException;
import org.mingharness.config.DocumentImportProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

/** 只允许 PDF/DOCX，并在进入知识库前提取为受限纯文本。 */
@Service
public class KnowledgeDocumentFileParser {

    private static final String PDF_MEDIA_TYPE = "application/pdf";
    private static final String DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocumentImportProperties properties;

    public KnowledgeDocumentFileParser(DocumentImportProperties properties) {
        this.properties = properties;
    }

    public ParsedKnowledgeDocument parse(MultipartFile file) {
        String originalName = validateUpload(file);
        String extension = extension(originalName);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_READ_FAILED", "读取知识库文件失败");
        }

        try {
            ParsedKnowledgeDocument parsed = switch (extension) {
                case "pdf" -> parsePdf(originalName, bytes);
                case "docx" -> parseDocx(originalName, bytes);
                default -> throw unsupportedFormat();
            };
            String text = normalizeText(parsed.text());
            if (text.isBlank()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_TEXT_EMPTY",
                        "文件中没有可用于检索的文本内容");
            }
            if (text.length() > properties.maxContentChars()) {
                throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_TEXT_TOO_LARGE",
                        "解析后的正文不能超过 " + properties.maxContentChars() + " 个字符");
            }
            return new ParsedKnowledgeDocument(parsed.originalName(), parsed.mediaType(), parsed.format(),
                    text, parsed.pageCount());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_PARSE_FAILED",
                    "无法解析该文件，请确认文件未损坏且未加密");
        }
    }

    public String validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_REQUIRED", "请上传一个 PDF 或 DOCX 文件");
        }
        if (file.getSize() > properties.maxUploadBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_FILE_TOO_LARGE",
                    "知识库文件不能超过 " + properties.maxUploadBytes() / (1024 * 1024) + " MB");
        }
        String originalName = displayName(file.getOriginalFilename());
        if (!properties.supportsExtension(extension(originalName))) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_FORMAT_UNSUPPORTED",
                    "当前仅支持 PDF 和 DOCX 文件");
        }
        return originalName;
    }

    /**
     * 按页面/段落流式读取文档，调用方可以在每个片段到达后立即分块和持久化。
     * 该路径不会把解析后的全文拼成一个大字符串。
     */
    public ParsedDocumentMetadata parseSegments(Path path, String originalName,
                                                Consumer<ParsedDocumentSegment> consumer) {
        if (path == null || consumer == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_REQUIRED", "请上传一个 PDF 或 DOCX 文件");
        }
        try {
            long size = Files.size(path);
            if (size <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_REQUIRED", "请上传一个 PDF 或 DOCX 文件");
            }
            if (size > properties.maxUploadBytes()) {
                throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_FILE_TOO_LARGE",
                        "知识库文件不能超过 " + properties.maxUploadBytes() / (1024 * 1024) + " MB");
            }
            String displayName = displayName(originalName);
            String extension = extension(displayName);
            if (!properties.supportsExtension(extension)) {
                throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_FORMAT_UNSUPPORTED",
                        "当前仅支持 PDF 和 DOCX 文件");
            }
            return switch (extension) {
                case "pdf" -> parsePdfSegments(path, displayName, consumer);
                case "docx" -> parseDocxSegments(path, displayName, consumer);
                default -> throw unsupportedFormat();
            };
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_READ_FAILED", "读取知识库文件失败");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_PARSE_FAILED",
                    "无法解析该文件，请确认文件未损坏且未加密");
        }
    }

    private ParsedDocumentMetadata parsePdfSegments(Path path, String originalName,
                                                     Consumer<ParsedDocumentSegment> consumer) throws IOException {
        if (!containsPdfHeader(path)) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_FORMAT_MISMATCH",
                    "文件扩展名与实际 PDF 格式不匹配");
        }
        int totalChars = 0;
        int pageCount;
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalizeText(stripper.getText(document));
                if (text.isBlank()) continue;
                totalChars = emitSegment(consumer, new ParsedDocumentSegment(text, page), totalChars);
            }
        }
        requireText(totalChars);
        return new ParsedDocumentMetadata(originalName, PDF_MEDIA_TYPE, "PDF", totalChars, pageCount);
    }

    private ParsedDocumentMetadata parseDocxSegments(Path path, String originalName,
                                                      Consumer<ParsedDocumentSegment> consumer) throws IOException {
        if (!containsDocxHeader(path)) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_FORMAT_MISMATCH",
                    "文件扩展名与实际 DOCX 格式不匹配");
        }
        int totalChars = 0;
        try (XWPFDocument document = new XWPFDocument(new BufferedInputStream(Files.newInputStream(path)))) {
            for (var paragraph : document.getParagraphs()) {
                String text = normalizeText(paragraph.getText());
                if (text.isBlank()) continue;
                totalChars = emitSegment(consumer, new ParsedDocumentSegment(text, 0), totalChars);
            }
            for (var table : document.getTables()) {
                for (var row : table.getRows()) {
                    for (var cell : row.getTableCells()) {
                        for (var paragraph : cell.getParagraphs()) {
                            String text = normalizeText(paragraph.getText());
                            if (text.isBlank()) continue;
                            totalChars = emitSegment(consumer, new ParsedDocumentSegment(text, 0), totalChars);
                        }
                    }
                }
            }
        }
        requireText(totalChars);
        return new ParsedDocumentMetadata(originalName, DOCX_MEDIA_TYPE, "DOCX", totalChars, 0);
    }

    private int emitSegment(Consumer<ParsedDocumentSegment> consumer, ParsedDocumentSegment segment,
                            int totalChars) {
        int next = totalChars + segment.text().length();
        if (next > properties.maxContentChars()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_TEXT_TOO_LARGE",
                    "解析后的正文不能超过 " + properties.maxContentChars() + " 个字符");
        }
        consumer.accept(segment);
        return next;
    }

    private void requireText(int totalChars) {
        if (totalChars <= 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_TEXT_EMPTY",
                    "文件中没有可用于检索的文本内容");
        }
    }

    private ParsedKnowledgeDocument parsePdf(String originalName, byte[] bytes) throws IOException {
        if (!containsPdfHeader(bytes)) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_FORMAT_MISMATCH",
                    "文件扩展名与实际 PDF 格式不匹配");
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return new ParsedKnowledgeDocument(originalName, PDF_MEDIA_TYPE, "PDF",
                    stripper.getText(document), document.getNumberOfPages());
        }
    }

    private ParsedKnowledgeDocument parseDocx(String originalName, byte[] bytes) throws IOException {
        if (!startsWith(bytes, new byte[]{'P', 'K', 3, 4}, 0)) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_FORMAT_MISMATCH",
                    "文件扩展名与实际 DOCX 格式不匹配");
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return new ParsedKnowledgeDocument(originalName, DOCX_MEDIA_TYPE, "DOCX",
                    extractor.getText(), 0);
        }
    }

    private BusinessException unsupportedFormat() {
        return new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_FORMAT_UNSUPPORTED",
                "当前仅支持 PDF 和 DOCX 文件");
    }

    private static boolean containsPdfHeader(byte[] value) {
        byte[] prefix = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        int lastStart = Math.min(value == null ? -1 : value.length - prefix.length, 1024);
        for (int offset = 0; offset <= lastStart; offset++) {
            if (startsWith(value, prefix, offset)) return true;
        }
        return false;
    }

    private static boolean containsPdfHeader(Path path) throws IOException {
        byte[] prefix = new byte[1029];
        int length;
        try (var input = Files.newInputStream(path)) {
            length = input.read(prefix);
        }
        byte[] pdf = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        int lastStart = Math.min(length - pdf.length, 1024);
        for (int offset = 0; offset <= lastStart; offset++) {
            if (startsWith(prefix, pdf, offset)) return true;
        }
        return false;
    }

    private static boolean containsDocxHeader(Path path) throws IOException {
        byte[] prefix = new byte[4];
        int length;
        try (var input = Files.newInputStream(path)) {
            length = input.read(prefix);
        }
        return length == 4 && startsWith(prefix, new byte[]{'P', 'K', 3, 4}, 0);
    }

    private static boolean startsWith(byte[] value, byte[] prefix, int offset) {
        if (value == null || offset < 0 || value.length - offset < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[offset + index] != prefix[index]) return false;
        }
        return true;
    }

    private static String normalizeText(String value) {
        if (value == null) return "";
        String normalized = value.replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.lines().forEach(line -> {
            if (result.length() > 0) result.append('\n');
            result.append(line.stripTrailing());
        });
        return result.toString().trim();
    }

    private static String displayName(String value) {
        if (value == null || value.isBlank()) return "未命名文档";
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

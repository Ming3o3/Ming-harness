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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_REQUIRED", "请上传一个 PDF 或 DOCX 文件");
        }
        if (file.getSize() > properties.maxUploadBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_FILE_TOO_LARGE",
                    "知识库文件不能超过 " + properties.maxUploadBytes() / (1024 * 1024) + " MB");
        }

        String originalName = displayName(file.getOriginalFilename());
        String extension = extension(originalName);
        if (!properties.supportsExtension(extension)) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "DOCUMENT_FORMAT_UNSUPPORTED",
                    "当前仅支持 PDF 和 DOCX 文件");
        }

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

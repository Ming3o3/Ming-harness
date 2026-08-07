package org.mingharness.context;

import org.mingharness.config.ContextChunkingProperties;
import org.mingharness.config.EmbeddingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 结构优先、长度有界的确定性分块器。
 *
 * <p>先按空行保留段落边界，再把相邻短段落合并；超长段落在标点附近切分，最后使用少量
 * 尾部重叠降低跨块语义断裂。该实现不调用模型，便于写入链路稳定，也便于未来替换成
 * 语义分块器而不影响索引表和召回协议。</p>
 */
@Service
public class ContextChunker {

    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String BOUNDARY_CHARS = "。！？!?；;。\n";

    private final ContextChunkingProperties properties;
    private final EmbeddingProperties embeddingProperties;

    public ContextChunker(ContextChunkingProperties properties) {
        this(properties, null);
    }

    @Autowired
    public ContextChunker(ContextChunkingProperties properties, EmbeddingProperties embeddingProperties) {
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
    }

    public List<ContextChunkDraft> chunk(String content) {
        String normalized = normalize(content);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> units = Arrays.stream(normalized.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(unit -> !unit.isBlank())
                .toList();
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (exceedsBudget(unit)) {
                flush(current, chunks);
                appendOversized(unit, chunks);
                continue;
            }
            int required = current.isEmpty() ? unit.length() : current.length() + PARAGRAPH_SEPARATOR.length() + unit.length();
            if (required <= properties.chunkMaxChars()
                    && fitsTokens(join(current, unit))) {
                if (!current.isEmpty()) current.append(PARAGRAPH_SEPARATOR);
                current.append(unit);
                continue;
            }
            String overlap = tail(current.toString(), properties.chunkOverlapChars());
            flush(current, chunks);
            if (!overlap.isBlank()
                    && overlap.length() + PARAGRAPH_SEPARATOR.length() + unit.length()
                    <= properties.chunkMaxChars()
                    && fitsTokens(overlap + PARAGRAPH_SEPARATOR + unit)) {
                current.append(overlap).append(PARAGRAPH_SEPARATOR);
            }
            current.append(unit);
        }
        flush(current, chunks);

        List<ContextChunkDraft> result = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            result.add(new ContextChunkDraft(index, chunks.get(index)));
        }
        return List.copyOf(result);
    }

    private void appendOversized(String unit, List<String> chunks) {
        int start = 0;
        while (start < unit.length()) {
            int end = Math.min(unit.length(), start + properties.chunkMaxChars());
            if (embeddingProperties != null && embeddingProperties.maxInputTokens() > 0) {
                String tokenBounded = EmbeddingTokenEstimator.truncate(unit.substring(start),
                        embeddingProperties.maxInputTokens());
                if (!tokenBounded.isEmpty()) {
                    end = Math.min(end, start + tokenBounded.length());
                }
            }
            if (end < unit.length()) {
                int boundary = lastBoundary(unit, start, end);
                if (boundary > start + properties.chunkMaxChars() / 2) {
                    end = boundary + 1;
                }
                if (embeddingProperties != null && embeddingProperties.maxInputTokens() > 0) {
                    String tokenBounded = EmbeddingTokenEstimator.truncate(unit.substring(start),
                            embeddingProperties.maxInputTokens());
                    if (!tokenBounded.isEmpty()) {
                        end = Math.min(end, start + tokenBounded.length());
                    }
                }
            }
            String piece = unit.substring(start, end).trim();
            if (!piece.isBlank()) chunks.add(piece);
            if (end >= unit.length()) break;
            int nextStart = Math.max(start + 1, end - properties.chunkOverlapChars());
            start = nextStart;
        }
    }

    private int lastBoundary(String value, int start, int end) {
        for (int index = end - 1; index > start; index--) {
            if (BOUNDARY_CHARS.indexOf(value.charAt(index)) >= 0) return index;
        }
        return -1;
    }

    private void flush(StringBuilder current, List<String> chunks) {
        String value = current.toString().trim();
        if (!value.isBlank()) chunks.add(value);
        current.setLength(0);
    }

    private String tail(String value, int maxLength) {
        if (maxLength <= 0 || value.length() <= maxLength) return value;
        return value.substring(value.length() - maxLength).trim();
    }

    private boolean exceedsBudget(String value) {
        return value.length() > properties.chunkMaxChars() || !fitsTokens(value);
    }

    private boolean fitsTokens(String value) {
        return embeddingProperties == null
                || EmbeddingTokenEstimator.estimate(value) <= embeddingProperties.maxInputTokens();
    }

    private String join(StringBuilder current, String unit) {
        return current.isEmpty() ? unit : current + PARAGRAPH_SEPARATOR + unit;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}

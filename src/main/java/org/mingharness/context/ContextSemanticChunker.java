package org.mingharness.context;

import org.mingharness.config.ContextChunkingProperties;
import org.mingharness.config.EmbeddingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 基于相邻原子单元 embedding 相似度的语义分块器。
 *
 * <p>embedding 模型只负责表达语义，边界判断、最大长度和重叠仍由本地算法负责。结构
 * 单元（段落和 fenced code block）先于相似度生效，避免把代码、表格或标题拆到不稳定
 * 的位置。供应商不可用时返回确定性分块，正文写入不会被外部服务阻断。</p>
 */
@Service
public class ContextSemanticChunker {

    private static final Logger log = LoggerFactory.getLogger(ContextSemanticChunker.class);
    private static final String STRATEGY = "SEMANTIC";
    private static final String VERSION = "semantic-v1";
    private static final String DETERMINISTIC_STRATEGY = "DETERMINISTIC";
    private static final String DETERMINISTIC_VERSION = "deterministic-v1";

    private final ContextChunker deterministicChunker;
    private final ContextChunkingProperties properties;
    private final EmbeddingProperties embeddingProperties;
    private final EmbeddingGateway embeddingGateway;

    public ContextSemanticChunker(ContextChunker deterministicChunker,
                                  ContextChunkingProperties properties,
                                  EmbeddingProperties embeddingProperties,
                                  EmbeddingGateway embeddingGateway) {
        this.deterministicChunker = deterministicChunker;
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
        this.embeddingGateway = embeddingGateway;
    }

    public ContextChunkingResult chunk(String content) {
        ContextChunkingResult deterministic = deterministic(content);
        if (!properties.semanticEnabled() || !embeddingGateway.enabled()) {
            return deterministic;
        }
        String normalized = normalize(content);
        List<String> units = atomicUnits(normalized);
        if (units.size() < properties.semanticMinUnits()
                || units.stream().anyMatch(unit -> unit.length() > embeddingProperties.maxInputChars()
                || unit.length() > properties.chunkMaxChars())) {
            return deterministic;
        }
        try {
            List<EmbeddingVector> vectors = embedAll(units);
            if (vectors.size() != units.size()) {
                return deterministic;
            }
            List<String> chunks = merge(units, vectors);
            if (chunks.isEmpty()) {
                return deterministic;
            }
            return new ContextChunkingResult(drafts(chunks), STRATEGY, VERSION);
        } catch (EmbeddingGatewayException exception) {
            log.warn("语义分块 embedding 失败，回退到确定性分块，message={}", exception.getMessage());
            return deterministic;
        }
    }

    private ContextChunkingResult deterministic(String content) {
        return new ContextChunkingResult(deterministicChunker.chunk(content),
                DETERMINISTIC_STRATEGY, DETERMINISTIC_VERSION);
    }

    private List<EmbeddingVector> embedAll(List<String> units) {
        List<EmbeddingVector> vectors = new ArrayList<>(units.size());
        int batchSize = Math.max(1, embeddingProperties.batchSize());
        for (int start = 0; start < units.size(); start += batchSize) {
            List<String> batch = units.subList(start, Math.min(units.size(), start + batchSize));
            vectors.addAll(embeddingGateway.embed(batch));
        }
        return List.copyOf(vectors);
    }

    private List<String> merge(List<String> units, List<EmbeddingVector> vectors) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentUnits = 0;
        for (int index = 0; index < units.size(); index++) {
            String unit = units.get(index);
            boolean semanticBoundary = index > 0
                    && cosine(vectors.get(index - 1), vectors.get(index)) < properties.semanticBreakpoint()
                    && currentUnits >= properties.semanticMinUnits();
            int required = current.isEmpty() ? unit.length()
                    : current.length() + 2 + unit.length();
            if (semanticBoundary || required > properties.chunkMaxChars()) {
                flush(current, chunks);
                currentUnits = 0;
                String overlap = tail(chunks.isEmpty() ? "" : chunks.get(chunks.size() - 1),
                        properties.chunkOverlapChars());
                if (!overlap.isBlank() && overlap.length() + 2 + unit.length() <= properties.chunkMaxChars()) {
                    current.append(overlap).append("\n\n");
                }
            }
            if (!current.isEmpty() && !current.toString().endsWith("\n\n")) {
                current.append("\n\n");
            }
            current.append(unit);
            currentUnits++;
        }
        flush(current, chunks);
        return List.copyOf(chunks);
    }

    private double cosine(EmbeddingVector left, EmbeddingVector right) {
        if (left == null || right == null || left.values().size() != right.values().size()
                || left.values().isEmpty()) return -1.0;
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < left.values().size(); index++) {
            double a = left.values().get(index);
            double b = right.values().get(index);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) return -1.0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private List<String> atomicUnits(String normalized) {
        List<String> result = new ArrayList<>();
        for (String paragraph : Arrays.stream(normalized.split("\\n\\s*\\n"))
                .map(String::trim).filter(item -> !item.isBlank()).toList()) {
            if (paragraph.startsWith("```") || paragraph.startsWith("~~~")) {
                result.add(paragraph);
            } else {
                result.addAll(splitSentences(paragraph));
            }
        }
        return result.stream().filter(item -> !item.isBlank()).toList();
    }

    private List<String> splitSentences(String paragraph) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < paragraph.length(); index++) {
            char value = paragraph.charAt(index);
            current.append(value);
            boolean punctuationBoundary = "。！？!?；;".indexOf(value) >= 0
                    || value == '.' && (index + 1 == paragraph.length()
                    || Character.isWhitespace(paragraph.charAt(index + 1)));
            if (punctuationBoundary) {
                flush(current, result);
            }
        }
        flush(current, result);
        return result;
    }

    private List<ContextChunkDraft> drafts(List<String> chunks) {
        List<ContextChunkDraft> result = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            result.add(new ContextChunkDraft(index, chunks.get(index)));
        }
        return List.copyOf(result);
    }

    private void flush(StringBuilder current, List<String> result) {
        String value = current.toString().trim();
        if (!value.isBlank()) result.add(value);
        current.setLength(0);
    }

    private String tail(String value, int maxLength) {
        if (maxLength <= 0 || value.length() <= maxLength) return value;
        return value.substring(value.length() - maxLength).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}

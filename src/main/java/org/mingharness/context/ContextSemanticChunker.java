package org.mingharness.context;

import org.mingharness.config.ContextChunkingProperties;
import org.mingharness.config.EmbeddingProperties;
import org.mingharness.observability.HarnessMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
    private static final String VERSION = "semantic-v2";
    private static final String DETERMINISTIC_STRATEGY = "DETERMINISTIC";
    private static final String DETERMINISTIC_VERSION = "deterministic-v1";

    private final ContextChunker deterministicChunker;
    private final ContextChunkingProperties properties;
    private final EmbeddingProperties embeddingProperties;
    private final EmbeddingProviderConfigService configService;
    private final EmbeddingGateway embeddingGateway;
    private final ContextEmbeddingCache embeddingCache;
    private final HarnessMetrics metrics;

    public ContextSemanticChunker(ContextChunker deterministicChunker,
                                  ContextChunkingProperties properties,
                                  EmbeddingProperties embeddingProperties,
                                  EmbeddingGateway embeddingGateway) {
        this(deterministicChunker, properties, embeddingProperties, embeddingGateway,
                new NoopContextEmbeddingCache(), null, null);
    }

    public ContextSemanticChunker(ContextChunker deterministicChunker,
                                  ContextChunkingProperties properties,
                                  EmbeddingProperties embeddingProperties,
                                  EmbeddingGateway embeddingGateway,
                                  ContextEmbeddingCache embeddingCache,
                                  HarnessMetrics metrics) {
        this(deterministicChunker, properties, embeddingProperties, embeddingGateway, embeddingCache, metrics, null);
    }

    @Autowired
    public ContextSemanticChunker(ContextChunker deterministicChunker,
                                  ContextChunkingProperties properties,
                                  EmbeddingProperties embeddingProperties,
                                  EmbeddingGateway embeddingGateway,
                                  ContextEmbeddingCache embeddingCache,
                                  HarnessMetrics metrics,
                                  EmbeddingProviderConfigService configService) {
        this.deterministicChunker = deterministicChunker;
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
        this.configService = configService;
        this.embeddingGateway = embeddingGateway;
        this.embeddingCache = embeddingCache;
        this.metrics = metrics;
    }

    public ContextChunkingResult chunk(String content) {
        return chunk(null, content);
    }

    /** 使用租户隔离的原子单元 embedding 缓存完成语义边界判断。 */
    public ContextChunkingResult chunk(String tenantId, String content) {
        ContextChunkingResult deterministic = deterministic(content);
        EmbeddingProviderConfigService.ResolvedEmbeddingConfig config = config(tenantId);
        if (!properties.semanticEnabled() || !enabled(tenantId, config)) {
            return deterministic;
        }
        String normalized = normalize(content);
        List<String> units = atomicUnits(normalized);
        if (units.size() < properties.semanticMinUnits()
                || units.stream().anyMatch(unit -> unit.length() > config.maxInputChars()
                || EmbeddingTokenEstimator.estimate(unit) > config.maxInputTokens()
                || unit.length() > properties.chunkMaxChars())) {
            return deterministic;
        }
        try {
            List<EmbeddingVector> vectors = embedAll(tenantId, units, config);
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

    /** 只执行本地确定性分块，供正文事务先落库，避免等待外部 embedding 服务。 */
    public ContextChunkingResult deterministicOnly(String content) {
        return deterministic(content);
    }

    private ContextChunkingResult deterministic(String content) {
        return new ContextChunkingResult(deterministicChunker.chunk(content),
                DETERMINISTIC_STRATEGY, DETERMINISTIC_VERSION);
    }

    private List<EmbeddingVector> embedAll(String tenantId, List<String> units,
                                           EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        List<EmbeddingVector> vectors = new ArrayList<>(units.size());
        List<AtomicEmbeddingMiss> misses = new ArrayList<>();
        for (int index = 0; index < units.size(); index++) {
            String unit = units.get(index);
            Optional<EmbeddingVector> cached = cached(tenantId, unit, config);
            if (cached.isPresent()) {
                vectors.add(cached.get());
                if (metrics != null) metrics.contextSemanticEmbeddingCacheHit();
            } else {
                vectors.add(null);
                misses.add(new AtomicEmbeddingMiss(index, unit));
                if (metrics != null) metrics.contextSemanticEmbeddingCacheMiss();
            }
        }

        int batchSize = Math.max(1, config.batchSize());
        for (int start = 0; start < misses.size(); start += batchSize) {
            List<AtomicEmbeddingMiss> batch = misses.subList(start, Math.min(misses.size(), start + batchSize));
            List<EmbeddingVector> batchVectors = configService == null
                    ? embeddingGateway.embed(batch.stream().map(AtomicEmbeddingMiss::content).toList())
                    : embeddingGateway.embed(tenantId, batch.stream()
                    .map(AtomicEmbeddingMiss::content).toList());
            if (batchVectors.size() != batch.size()) {
                throw new EmbeddingGatewayException(false, "语义分块 embedding 返回数量与原子单元数量不一致");
            }
            for (int index = 0; index < batch.size(); index++) {
                EmbeddingVector vector = batchVectors.get(index);
                validateDimension(vector, config);
                AtomicEmbeddingMiss miss = batch.get(index);
                vectors.set(miss.index(), vector);
                saveCache(tenantId, miss.content(), vector, config);
            }
        }
        return List.copyOf(vectors);
    }

    private Optional<EmbeddingVector> cached(String tenantId, String content,
                                             EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        try {
            Optional<EmbeddingVector> value = embeddingCache.find(tenantId,
                    EmbeddingContentHasher.sha256(content), config.model(),
                    config.modelVersion(), config.dimension());
            if (value != null && value.isPresent()
                    && value.get().dimension() == config.dimension()) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // 缓存是加速层，读取失败时继续调用供应商。
        }
        return Optional.empty();
    }

    private void saveCache(String tenantId, String content, EmbeddingVector vector,
                           EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        try {
            embeddingCache.save(tenantId, EmbeddingContentHasher.sha256(content),
                    config.model(), config.modelVersion(), vector);
        } catch (RuntimeException ignored) {
            // 缓存写入失败不能阻断语义分块。
        }
    }

    private void validateDimension(EmbeddingVector vector,
                                   EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        if (vector == null || vector.dimension() != config.dimension()) {
            throw new EmbeddingGatewayException(false,
                    "语义分块 embedding 维度不匹配，期望 " + config.dimension());
        }
    }

    private boolean enabled(String tenantId, EmbeddingProviderConfigService.ResolvedEmbeddingConfig config) {
        return configService == null ? embeddingGateway.enabled() : embeddingGateway.enabled(tenantId);
    }

    private EmbeddingProviderConfigService.ResolvedEmbeddingConfig config(String tenantId) {
        return configService == null
                ? new EmbeddingProviderConfigService.ResolvedEmbeddingConfig(embeddingProperties.enabled(),
                embeddingProperties.baseUrl(), embeddingProperties.apiKey(), embeddingProperties.model(),
                embeddingProperties.modelVersion(), embeddingProperties.dimension(), embeddingProperties.batchSize(),
                embeddingProperties.maxInputChars(), embeddingProperties.maxInputTokens(), embeddingProperties.maxResponseChars(),
                embeddingProperties.maxAttempts(), embeddingProperties.retryBackoffMs(), embeddingProperties.timeoutMs(),
                "environment", null)
                : configService.resolve(tenantId);
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
            if (semanticBoundary || required > properties.chunkMaxChars()
                    || !fitsTokens(joined(current, unit))) {
                flush(current, chunks);
                currentUnits = 0;
                String overlap = tail(chunks.isEmpty() ? "" : chunks.get(chunks.size() - 1),
                        properties.chunkOverlapChars());
                if (!overlap.isBlank() && overlap.length() + 2 + unit.length() <= properties.chunkMaxChars()
                        && fitsTokens(overlap + "\n\n" + unit)) {
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
        for (AtomicUnit unit : structuralUnits(normalized)) {
            if (unit.protectedUnit()) {
                result.add(unit.content());
            } else {
                result.addAll(splitSentences(unit.content()));
            }
        }
        return result.stream().filter(item -> !item.isBlank()).toList();
    }

    /**
     * 先识别 Markdown 结构，再把普通段落交给句子切分。
     *
     * <p>代码块和表格中的标点、竖线不代表正文语义边界；标题也必须和正文保持可追踪
     * 的结构关系。因此它们作为 protected unit 直接进入 embedding 批次。</p>
     */
    private List<AtomicUnit> structuralUnits(String normalized) {
        String[] lines = normalized.split("\\n", -1);
        List<AtomicUnit> result = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        int index = 0;
        while (index < lines.length) {
            String line = lines[index];
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                flushParagraph(paragraph, result);
                index++;
                continue;
            }
            if (isFenceStart(trimmed)) {
                flushParagraph(paragraph, result);
                String marker = trimmed.substring(0, 3);
                StringBuilder fence = new StringBuilder(trimmed);
                index++;
                while (index < lines.length) {
                    String next = lines[index];
                    fence.append("\n").append(next);
                    index++;
                    if (next.trim().startsWith(marker)) break;
                }
                result.add(new AtomicUnit(fence.toString().trim(), true));
                continue;
            }
            if (index + 1 < lines.length && isTableHeader(trimmed)
                    && isTableDelimiter(lines[index + 1])) {
                flushParagraph(paragraph, result);
                StringBuilder table = new StringBuilder(trimmed);
                table.append("\n").append(lines[index + 1].trim());
                index += 2;
                while (index < lines.length && isTableRow(lines[index])) {
                    table.append("\n").append(lines[index].trim());
                    index++;
                }
                result.add(new AtomicUnit(table.toString().trim(), true));
                continue;
            }
            if (isHeading(trimmed)) {
                flushParagraph(paragraph, result);
                result.add(new AtomicUnit(trimmed, true));
                index++;
                continue;
            }
            if (!paragraph.isEmpty()) paragraph.append('\n');
            paragraph.append(trimmed);
            index++;
        }
        flushParagraph(paragraph, result);
        return List.copyOf(result);
    }

    private boolean isFenceStart(String line) {
        return line.startsWith("```") || line.startsWith("~~~");
    }

    private boolean isHeading(String line) {
        return line.matches("#{1,6}\\s+.+");
    }

    private boolean isTableHeader(String line) {
        return line.indexOf('|') >= 0;
    }

    private boolean isTableDelimiter(String line) {
        String value = line.trim();
        if (value.startsWith("|")) value = value.substring(1);
        if (value.endsWith("|")) value = value.substring(0, value.length() - 1);
        String[] cells = value.split("\\|");
        if (cells.length < 2) return false;
        return Arrays.stream(cells).allMatch(cell -> cell.trim().matches(":?-{3,}:?"));
    }

    private boolean isTableRow(String line) {
        return !line.isBlank() && line.indexOf('|') >= 0;
    }

    private void flushParagraph(StringBuilder paragraph, List<AtomicUnit> result) {
        String value = paragraph.toString().trim();
        if (!value.isBlank()) result.add(new AtomicUnit(value, false));
        paragraph.setLength(0);
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

    private boolean fitsTokens(String value) {
        return EmbeddingTokenEstimator.estimate(value) <= embeddingProperties.maxInputTokens();
    }

    private String joined(StringBuilder current, String unit) {
        return current.isEmpty() ? unit : current + "\n\n" + unit;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private record AtomicUnit(String content, boolean protectedUnit) {
    }

    private record AtomicEmbeddingMiss(int index, String content) {
    }
}

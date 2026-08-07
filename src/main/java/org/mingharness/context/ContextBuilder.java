package org.mingharness.context;

import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 统一构建可引用上下文，先做权限过滤，再做轻量关键词召回和预算裁剪。 */
@Service
public class ContextBuilder {

    private final KnowledgeDocumentRepository documentRepository;
    private final MemoryEntryRepository memoryRepository;
    private final VectorContextRetriever vectorContextRetriever;

    public ContextBuilder(KnowledgeDocumentRepository documentRepository,
                          MemoryEntryRepository memoryRepository,
                          VectorContextRetriever vectorContextRetriever) {
        this.documentRepository = documentRepository;
        this.memoryRepository = memoryRepository;
        this.vectorContextRetriever = vectorContextRetriever;
    }

    public ContextResult build(String tenantId, String userId, String query, int maxChars) {
        if (query == null || query.isBlank() || maxChars < 1) {
            return new ContextResult("", List.of());
        }
        ContextResult vectorResult = new ContextResult("", List.of());
        try {
            vectorResult = vectorContextRetriever.retrieve(tenantId, userId, query, maxChars);
        } catch (EmbeddingGatewayException | DataAccessException exception) {
            // embedding 服务或 pgvector 暂时不可用时保持旧的确定性关键词召回能力。
        }
        ContextResult keywordResult = buildKeyword(tenantId, userId, query, maxChars);
        if (vectorResult.isEmpty()) return keywordResult;
        if (keywordResult.isEmpty()) return vectorResult;
        return merge(vectorResult, keywordResult, maxChars);
    }

    private ContextResult buildKeyword(String tenantId, String userId, String query, int maxChars) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String[] terms = normalizedQuery.split("\\s+|[，。！？、,:：;；]+");
        List<ScoredContext> candidates = new ArrayList<>();
        for (KnowledgeDocument document : documentRepository
                .findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId)) {
            if (!document.isVisibleTo(userId)) {
                continue;
            }
            String searchable = (document.getTitle() + "\n" + document.getContent()).toLowerCase(Locale.ROOT);
            int score = score(searchable, terms);
            if (score > 0) {
                candidates.add(ScoredContext.document(document, score));
            }
        }

        Instant now = Instant.now();
        for (MemoryEntry memory : memoryRepository
                .findTop100ByTenantIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, userId)) {
            if (!memory.isActive(now)) {
                continue;
            }
            String searchable = (memory.getMemoryType() + "\n" + memory.getContent()).toLowerCase(Locale.ROOT);
            int score = score(searchable, terms);
            if (score > 0) {
                candidates.add(ScoredContext.memory(memory, score));
            }
        }

        candidates.sort(Comparator.comparingInt(ScoredContext::score).reversed()
                .thenComparing(ScoredContext::createdAt, Comparator.reverseOrder()));

        List<ContextEvidence> evidences = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (ScoredContext candidate : candidates) {
            String excerpt = excerpt(candidate.content(), normalizedQuery, terms, 800);
            String block = "[" + candidate.displayId() + "] " + candidate.title() + "\n" + excerpt + "\n";
            if (context.length() + block.length() > maxChars) {
                // 单个来源过大时跳过它，继续尝试更小的来源，避免一篇长文阻断相关记忆。
                continue;
            }
            context.append(block);
            evidences.add(new ContextEvidence(candidate.id(), candidate.title(),
                    candidate.citation(), excerpt));
        }
        return new ContextResult(context.toString(), List.copyOf(evidences));
    }

    /** 向量结果优先，关键词结果补充未命中的父来源，兼顾语义召回和错误码/名称精确匹配。 */
    private ContextResult merge(ContextResult vectorResult, ContextResult keywordResult, int maxChars) {
        List<ContextEvidence> evidences = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        java.util.Set<String> sources = new java.util.HashSet<>();
        appendResult(vectorResult, maxChars, text, evidences, sources);
        appendResult(keywordResult, maxChars, text, evidences, sources);
        return new ContextResult(text.toString(), List.copyOf(evidences));
    }

    private void appendResult(ContextResult result, int maxChars, StringBuilder text,
                              List<ContextEvidence> evidences, java.util.Set<String> sources) {
        for (ContextEvidence evidence : result.evidences()) {
            String source = sourceKey(evidence.citation());
            if (!sources.add(source)) continue;
            String block = "[" + evidence.citation() + "] " + evidence.title() + "\n"
                    + evidence.excerpt() + "\n";
            if (text.length() + block.length() > maxChars) continue;
            text.append(block);
            evidences.add(evidence);
        }
    }

    private String sourceKey(String citation) {
        if (citation == null) return "";
        int chunk = citation.indexOf("#chunk:");
        return chunk < 0 ? citation : citation.substring(0, chunk);
    }

    private int score(String searchable, String[] terms) {
        int score = 0;
        for (String term : terms) {
            if (!term.isBlank() && searchable.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private String excerpt(String content, String query, String[] terms, int maxLength) {
        String normalized = content == null ? "" : content;
        String searchable = normalized.toLowerCase(Locale.ROOT);
        int position = searchable.indexOf(query);
        if (position < 0) {
            for (String term : terms) {
                if (!term.isBlank()) {
                    position = searchable.indexOf(term);
                    if (position >= 0) {
                        break;
                    }
                }
            }
        }
        if (position < 0) {
            return normalized.substring(0, Math.min(normalized.length(), maxLength));
        }
        int start = Math.max(0, position - maxLength / 3);
        int end = Math.min(normalized.length(), start + maxLength);
        return normalized.substring(start, end);
    }

    private record ScoredContext(String id, String displayId, String title, String citation,
                                 String content, int score, Instant createdAt) {

        private static ScoredContext document(KnowledgeDocument document, int score) {
            return new ScoredContext(document.getId(), document.getId(), document.getTitle(),
                    "document:" + document.getId(), document.getContent(), score, document.getCreatedAt());
        }

        private static ScoredContext memory(MemoryEntry memory, int score) {
            return new ScoredContext(memory.getId(), "memory:" + memory.getId(),
                    "记忆 · " + memory.getMemoryType(), "memory:" + memory.getId(),
                    memory.getContent(), score, memory.getCreatedAt());
        }
    }
}

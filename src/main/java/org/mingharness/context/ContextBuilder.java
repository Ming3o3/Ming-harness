package org.mingharness.context;

import org.mingharness.context.api.ContextEvidence;
import org.mingharness.context.api.ContextResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 统一构建可引用上下文，先做租户和用户过滤，再做轻量关键词召回。 */
@Service
public class ContextBuilder {

    private final KnowledgeDocumentRepository documentRepository;

    public ContextBuilder(KnowledgeDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public ContextResult build(String tenantId, String userId, String query, int maxChars) {
        if (query == null || query.isBlank() || maxChars < 1) {
            return new ContextResult("", List.of());
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String[] terms = normalizedQuery.split("\\s+|[，。！？、,:：;；]+");
        List<ScoredDocument> candidates = new ArrayList<>();
        for (KnowledgeDocument document : documentRepository
                .findTop100ByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId)) {
            if (!document.isVisibleTo(userId)) {
                continue;
            }
            String searchable = (document.getTitle() + "\n" + document.getContent()).toLowerCase(Locale.ROOT);
            int score = 0;
            for (String term : terms) {
                if (!term.isBlank() && searchable.contains(term)) {
                    score++;
                }
            }
            if (score > 0) {
                candidates.add(new ScoredDocument(document, score));
            }
        }
        candidates.sort(Comparator.comparingInt(ScoredDocument::score).reversed()
                .thenComparing(item -> item.document().getCreatedAt(), Comparator.reverseOrder()));

        List<ContextEvidence> evidences = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (ScoredDocument candidate : candidates) {
            KnowledgeDocument document = candidate.document();
            String excerpt = excerpt(document.getContent(), normalizedQuery, 800);
            String block = "[" + document.getId() + "] " + document.getTitle() + "\n" + excerpt + "\n";
            if (context.length() + block.length() > maxChars) {
                break;
            }
            context.append(block);
            evidences.add(new ContextEvidence(document.getId(), document.getTitle(),
                    "document:" + document.getId(), excerpt));
        }
        return new ContextResult(context.toString(), List.copyOf(evidences));
    }

    private String excerpt(String content, String query, int maxLength) {
        String normalized = content == null ? "" : content;
        int position = normalized.toLowerCase(Locale.ROOT).indexOf(query);
        if (position < 0) {
            return normalized.substring(0, Math.min(normalized.length(), maxLength));
        }
        int start = Math.max(0, position - maxLength / 3);
        int end = Math.min(normalized.length(), start + maxLength);
        return normalized.substring(start, end);
    }

    private record ScoredDocument(KnowledgeDocument document, int score) {
    }
}

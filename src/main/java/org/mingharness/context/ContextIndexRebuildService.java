package org.mingharness.context;

import org.mingharness.context.api.ContextReindexRequest;
import org.mingharness.context.api.ContextReindexResponse;
import org.mingharness.observability.HarnessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 租户级上下文索引重建服务。
 *
 * <p>先补齐历史父对象缺失的 chunk，再以有限批次为未完成 chunk 建立 embedding。外部
 * API 失败时保留已经成功的批次，并通过 pendingChunks 让下一次调用继续处理，避免把
 * 正文重建和向量供应商可用性绑定在同一个不可恢复的大事务里。</p>
 */
@Service
public class ContextIndexRebuildService {

    private static final Logger log = LoggerFactory.getLogger(ContextIndexRebuildService.class);

    private final KnowledgeDocumentRepository documentRepository;
    private final MemoryEntryRepository memoryRepository;
    private final ContextChunkRepository chunkRepository;
    private final ContextChunkWriter chunkWriter;
    private final ContextEmbeddingIndexer embeddingIndexer;
    private final HarnessMetrics metrics;

    public ContextIndexRebuildService(KnowledgeDocumentRepository documentRepository,
                                      MemoryEntryRepository memoryRepository,
                                      ContextChunkRepository chunkRepository,
                                      ContextChunkWriter chunkWriter,
                                      ContextEmbeddingIndexer embeddingIndexer,
                                      HarnessMetrics metrics) {
        this.documentRepository = documentRepository;
        this.memoryRepository = memoryRepository;
        this.chunkRepository = chunkRepository;
        this.chunkWriter = chunkWriter;
        this.embeddingIndexer = embeddingIndexer;
        this.metrics = metrics;
    }

    public ContextReindexResponse rebuild(String tenantId, ContextReindexRequest request) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("租户不能为空");
        }
        ContextReindexRequest effective = request == null ? new ContextReindexRequest(null, null, null, false) : request;
        String scope = effective.effectiveScope();
        int parentLimit = effective.effectiveParentLimit();
        int chunkLimit = effective.effectiveChunkLimit();

        List<ParentRef> parents = collectParents(tenantId, scope, parentLimit);
        int parentsRebuilt = 0;
        int chunksCreated = 0;
        for (ParentRef parent : parents) {
            if (!effective.shouldRechunk()
                    && chunkWriter.hasActiveChunks(tenantId, parent.type(), parent.id())) {
                continue;
            }
            chunksCreated += chunkWriter.replace(tenantId, parent.type(), parent.id(), parent.content());
            parentsRebuilt++;
        }

        List<ContextChunk> pending = chunkRepository
                .findByTenantIdAndDeletedAtIsNullAndEmbeddedAtIsNullOrderByCreatedAtAsc(
                        tenantId, PageRequest.of(0, chunkLimit));
        int chunksIndexed = 0;
        int chunksFailed = 0;
        if (embeddingIndexer.ready(tenantId) && !pending.isEmpty()) {
            int batchSize = Math.max(1, embeddingIndexer.batchSize(tenantId));
            for (int start = 0; start < pending.size(); start += batchSize) {
                List<ContextChunk> batch = pending.subList(start, Math.min(pending.size(), start + batchSize));
                try {
                    chunksIndexed += embeddingIndexer.indexChunks(batch);
                } catch (EmbeddingGatewayException | ContextEmbeddingStoreException exception) {
                    chunksFailed += pending.size() - start;
                    metrics.contextIndexFailure();
                    log.warn("上下文索引重建批次失败，tenantId={}, batchSize={}, message={}",
                            tenantId, batch.size(), exception.getMessage());
                    break;
                }
            }
        }

        long pendingCount = chunkRepository.countByTenantIdAndDeletedAtIsNullAndEmbeddedAtIsNull(tenantId);
        metrics.contextChunksIndexed(chunksIndexed);
        metrics.contextIndexPending(pendingCount);
        return new ContextReindexResponse(scope, embeddingIndexer.ready(tenantId), parents.size(), parentsRebuilt,
                chunksCreated, chunksIndexed, chunksFailed, safeInt(pendingCount));
    }

    private List<ParentRef> collectParents(String tenantId, String scope, int limit) {
        List<ParentRef> result = new ArrayList<>();
        PageRequest page = PageRequest.of(0, limit);
        if ("ALL".equals(scope) || "DOCUMENT".equals(scope)) {
            documentRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(tenantId, page)
                    .stream()
                    .map(document -> new ParentRef("DOCUMENT", document.getId(),
                            document.getCreatedAt(), document.getTitle() + "\n" + document.getContent()))
                    .forEach(result::add);
        }
        if ("ALL".equals(scope) || "MEMORY".equals(scope)) {
            Instant now = Instant.now();
            memoryRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(tenantId, page)
                    .stream()
                    .filter(memory -> memory.isActive(now))
                    .map(memory -> new ParentRef("MEMORY", memory.getId(),
                            memory.getCreatedAt(), memory.getMemoryType() + "\n" + memory.getContent()))
                    .forEach(result::add);
        }
        return result.stream()
                .sorted(Comparator.comparing(ParentRef::createdAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private record ParentRef(String type, String id, Instant createdAt, String content) {
    }
}

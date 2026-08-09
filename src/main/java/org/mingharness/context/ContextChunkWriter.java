package org.mingharness.context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import org.mingharness.config.ContextChunkingProperties;

/** 统一维护父对象到有序子块的物化关系，供写入链路和历史重建任务复用。 */
@Service
public class ContextChunkWriter {

    private final ContextChunkRepository chunkRepository;
    private final ContextParentWindowRepository parentWindowRepository;
    private final ContextSemanticChunker semanticChunker;
    private final ContextChunkingProperties properties;
    private final TransactionTemplate transactionTemplate;

    /** 供纯单元测试使用；生产 Bean 使用带事务管理器的构造函数。 */
    public ContextChunkWriter(ContextChunkRepository chunkRepository,
                              ContextParentWindowRepository parentWindowRepository,
                              ContextSemanticChunker semanticChunker,
                              ContextChunkingProperties properties) {
        this(chunkRepository, parentWindowRepository, semanticChunker, properties, null);
    }

    @Autowired
    public ContextChunkWriter(ContextChunkRepository chunkRepository,
                              ContextParentWindowRepository parentWindowRepository,
                              ContextSemanticChunker semanticChunker,
                              ContextChunkingProperties properties,
                              PlatformTransactionManager transactionManager) {
        this.chunkRepository = chunkRepository;
        this.parentWindowRepository = parentWindowRepository;
        this.semanticChunker = semanticChunker;
        this.properties = properties;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    public int replace(String tenantId, String parentType, String parentId, String content) {
        // 先计算分块，再开启数据库事务，避免语义分块调用外部 Embedding 时长期占用事务。
        ContextChunkingResult result = semanticChunker.chunk(tenantId, content);
        return persistReplacement(tenantId, parentType, parentId, result);
    }

    /** 事务内只落确定性子块，语义重分块由提交后的异步任务执行。 */
    public int replaceDeterministic(String tenantId, String parentType, String parentId, String content) {
        return persistReplacement(tenantId, parentType, parentId, semanticChunker.deterministicOnly(content));
    }

    /** 使用已完成的语义分块结果替换父对象的子块和父窗口。 */
    public int replaceSemantic(String tenantId, String parentType, String parentId, String content) {
        ContextChunkingResult result = semanticChunker.chunk(tenantId, content);
        return persistReplacement(tenantId, parentType, parentId, result);
    }

    private int persistReplacement(String tenantId, String parentType, String parentId,
                                   ContextChunkingResult result) {
        if (transactionTemplate == null) {
            return persistReplacementInTransaction(tenantId, parentType, parentId, result);
        }
        Integer created = transactionTemplate.execute(status ->
                persistReplacementInTransaction(tenantId, parentType, parentId, result));
        return created == null ? 0 : created;
    }

    private int persistReplacementInTransaction(String tenantId, String parentType, String parentId,
                                                ContextChunkingResult result) {
        chunkRepository.deleteByTenantIdAndParentTypeAndParentId(tenantId, parentType, parentId);
        // Derived delete 会把删除动作留在 Hibernate action queue；先 flush，避免新旧
        // chunk 在同一事务提交时因 parent/index 唯一约束发生 INSERT 先于 DELETE。
        chunkRepository.flush();
        parentWindowRepository.deleteByTenantIdAndParentTypeAndParentId(tenantId, parentType, parentId);
        parentWindowRepository.flush();
        List<WindowDraft> windows = windows(result.chunks());
        List<ContextParentWindow> parentWindows = new ArrayList<>(windows.size());
        List<ContextChunk> chunks = new ArrayList<>();
        for (WindowDraft window : windows) {
            ContextParentWindow parentWindow = new ContextParentWindow(tenantId, parentType, parentId,
                    window.windowIndex(), window.content(), EmbeddingContentHasher.sha256(window.content()));
            parentWindows.add(parentWindow);
            for (ContextChunkDraft draft : window.chunks()) {
                chunks.add(new ContextChunk(tenantId, parentType, parentId, draft.chunkIndex(),
                        draft.content(), EmbeddingContentHasher.sha256(draft.content()), result.strategy(), result.version(),
                        parentWindow.getId()));
            }
        }
        if (!parentWindows.isEmpty()) {
            parentWindowRepository.saveAll(parentWindows);
            parentWindowRepository.flush();
        }
        if (!chunks.isEmpty()) {
            chunkRepository.saveAll(chunks);
            chunkRepository.flush();
        }
        return chunks.size();
    }

    public boolean hasActiveChunks(String tenantId, String parentType, String parentId) {
        return chunkRepository.countByTenantIdAndParentTypeAndParentIdAndDeletedAtIsNull(
                tenantId, parentType, parentId) > 0;
    }

    private List<WindowDraft> windows(List<ContextChunkDraft> drafts) {
        List<WindowDraft> result = new ArrayList<>();
        List<ContextChunkDraft> current = new ArrayList<>();
        int currentLength = 0;
        for (ContextChunkDraft draft : drafts == null ? List.<ContextChunkDraft>of() : drafts) {
            int required = current.isEmpty() ? draft.content().length()
                    : currentLength + 2 + draft.content().length();
            if (!current.isEmpty() && required > properties.parentWindowMaxChars()) {
                result.add(window(result.size(), current));
                current = new ArrayList<>();
                currentLength = 0;
            }
            current.add(draft);
            currentLength = current.isEmpty() ? 0 : currentLength + (current.size() == 1 ? 0 : 2)
                    + draft.content().length();
        }
        if (!current.isEmpty()) result.add(window(result.size(), current));
        return List.copyOf(result);
    }

    private WindowDraft window(int index, List<ContextChunkDraft> chunks) {
        String content = chunks.stream().map(ContextChunkDraft::content)
                .reduce((left, right) -> left + "\n\n" + right).orElse("");
        return new WindowDraft(index, List.copyOf(chunks), content);
    }

    private record WindowDraft(int windowIndex, List<ContextChunkDraft> chunks, String content) {
    }
}

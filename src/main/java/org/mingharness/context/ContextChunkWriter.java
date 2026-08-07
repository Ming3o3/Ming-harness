package org.mingharness.context;

import org.springframework.stereotype.Service;

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

    public ContextChunkWriter(ContextChunkRepository chunkRepository,
                              ContextParentWindowRepository parentWindowRepository,
                              ContextSemanticChunker semanticChunker,
                              ContextChunkingProperties properties) {
        this.chunkRepository = chunkRepository;
        this.parentWindowRepository = parentWindowRepository;
        this.semanticChunker = semanticChunker;
        this.properties = properties;
    }

    public int replace(String tenantId, String parentType, String parentId, String content) {
        return replace(tenantId, parentType, parentId, semanticChunker.chunk(tenantId, content));
    }

    /** 事务内只落确定性子块，语义重分块由提交后的异步任务执行。 */
    public int replaceDeterministic(String tenantId, String parentType, String parentId, String content) {
        return replace(tenantId, parentType, parentId, semanticChunker.deterministicOnly(content));
    }

    /** 使用已完成的语义分块结果替换父对象的子块和父窗口。 */
    public int replaceSemantic(String tenantId, String parentType, String parentId, String content) {
        return replace(tenantId, parentType, parentId, semanticChunker.chunk(tenantId, content));
    }

    private int replace(String tenantId, String parentType, String parentId, ContextChunkingResult result) {
        chunkRepository.deleteByParentTypeAndParentId(parentType, parentId);
        parentWindowRepository.deleteByParentTypeAndParentId(parentType, parentId);
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
        if (!parentWindows.isEmpty()) parentWindowRepository.saveAll(parentWindows);
        if (!chunks.isEmpty()) chunkRepository.saveAll(chunks);
        return chunks.size();
    }

    public boolean hasActiveChunks(String parentType, String parentId) {
        return chunkRepository.countByParentTypeAndParentIdAndDeletedAtIsNull(parentType, parentId) > 0;
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

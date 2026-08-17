package org.mingharness.context;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 以小批次写入流式解析产生的 chunk 和有界父窗口。 */
@Service
public class StreamingContextChunkPersister {

    private final ContextChunkRepository chunkRepository;
    private final ContextParentWindowRepository parentWindowRepository;

    public StreamingContextChunkPersister(ContextChunkRepository chunkRepository,
                                          ContextParentWindowRepository parentWindowRepository) {
        this.chunkRepository = chunkRepository;
        this.parentWindowRepository = parentWindowRepository;
    }

    @Transactional
    public void clear(String tenantId, String parentType, String parentId) {
        chunkRepository.deleteByTenantIdAndParentTypeAndParentId(tenantId, parentType, parentId);
        chunkRepository.flush();
        parentWindowRepository.deleteByTenantIdAndParentTypeAndParentId(tenantId, parentType, parentId);
        parentWindowRepository.flush();
    }

    @Transactional
    public int append(String tenantId, String parentType, String parentId,
                      List<StreamingWindow> windows) {
        int created = 0;
        for (StreamingWindow window : windows == null ? List.<StreamingWindow>of() : windows) {
            ContextParentWindow parentWindow = new ContextParentWindow(tenantId, parentType, parentId,
                    window.windowIndex(), window.content(), EmbeddingContentHasher.sha256(window.content()));
            parentWindowRepository.save(parentWindow);
            for (ContextChunkDraft draft : window.chunks()) {
                chunkRepository.save(new ContextChunk(tenantId, parentType, parentId,
                        draft.chunkIndex(), draft.content(), EmbeddingContentHasher.sha256(draft.content()),
                        "DETERMINISTIC", "deterministic-v1", parentWindow.getId()));
                created++;
            }
        }
        parentWindowRepository.flush();
        chunkRepository.flush();
        return created;
    }

    public record StreamingWindow(int windowIndex, String content, List<ContextChunkDraft> chunks) {
        public StreamingWindow {
            content = content == null ? "" : content;
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
        }
    }
}

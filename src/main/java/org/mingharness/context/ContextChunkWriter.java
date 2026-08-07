package org.mingharness.context;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        chunkRepository.deleteByParentTypeAndParentId(parentType, parentId);
        parentWindowRepository.deleteByParentTypeAndParentId(parentType, parentId);
        ContextChunkingResult result = semanticChunker.chunk(content);
        List<WindowDraft> windows = windows(result.chunks());
        List<ContextParentWindow> parentWindows = new ArrayList<>(windows.size());
        List<ContextChunk> chunks = new ArrayList<>();
        for (WindowDraft window : windows) {
            ContextParentWindow parentWindow = new ContextParentWindow(tenantId, parentType, parentId,
                    window.windowIndex(), window.content(), sha256(window.content()));
            parentWindows.add(parentWindow);
            for (ContextChunkDraft draft : window.chunks()) {
                chunks.add(new ContextChunk(tenantId, parentType, parentId, draft.chunkIndex(),
                        draft.content(), sha256(draft.content()), result.strategy(), result.version(),
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

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256 算法", exception);
        }
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

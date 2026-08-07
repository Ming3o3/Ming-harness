package org.mingharness.context;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** 统一维护父对象到有序子块的物化关系，供写入链路和历史重建任务复用。 */
@Service
public class ContextChunkWriter {

    private final ContextChunkRepository chunkRepository;
    private final ContextChunker chunker;

    public ContextChunkWriter(ContextChunkRepository chunkRepository, ContextChunker chunker) {
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
    }

    public void replace(String tenantId, String parentType, String parentId, String content) {
        chunkRepository.deleteByParentTypeAndParentId(parentType, parentId);
        List<ContextChunk> chunks = new ArrayList<>();
        for (ContextChunkDraft draft : chunker.chunk(content)) {
            chunks.add(new ContextChunk(tenantId, parentType, parentId, draft.chunkIndex(),
                    draft.content(), sha256(draft.content())));
        }
        if (!chunks.isEmpty()) chunkRepository.saveAll(chunks);
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
}

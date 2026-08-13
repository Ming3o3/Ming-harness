package org.mingharness.runtime.application;

import org.mingharness.context.ContextEvidenceSnapshotCodec;
import org.mingharness.context.api.ContextEvidence;

import java.util.List;

/** 将模型步骤实际使用的授权来源以脱敏 JSON 快照持久化，供 Run 详情追溯。 */
final class ContextEvidenceCodec {

    private ContextEvidenceCodec() {
    }

    static String encode(List<ContextEvidence> evidences) {
        return ContextEvidenceSnapshotCodec.encode(evidences);
    }

    static List<ContextEvidence> decode(String raw) {
        return ContextEvidenceSnapshotCodec.decode(raw);
    }
}

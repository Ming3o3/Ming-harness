package org.mingharness.audit;

import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.runtime.domain.Run;
import org.mingharness.runtime.repository.RunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * 追加并验证审计事件的 HMAC 链。
 * HMAC 密钥不写入数据库，因此拥有数据库写权限的攻击者不能伪造有效事件或链头。
 */
@Service
public class AuditTrailService {

    private static final String GENESIS_HASH = "GENESIS";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final AuditEventRepository auditEventRepository;
    private final RunRepository runRepository;
    private final byte[] integrityKey;
    private final SensitiveDataSanitizer sanitizer;

    public AuditTrailService(AuditEventRepository auditEventRepository,
                             RunRepository runRepository,
                             AuditIntegrityProperties properties,
                             SensitiveDataSanitizer sanitizer) {
        this.auditEventRepository = auditEventRepository;
        this.runRepository = runRepository;
        this.integrityKey = properties.integrityKey().getBytes(StandardCharsets.UTF_8);
        this.sanitizer = sanitizer;
    }

    /** 在与业务状态相同的事务中追加一条已封签的审计事件。 */
    @Transactional
    public AuditEvent append(AuditEvent event) {
        if (event.getRunId() == null || event.getRunId().isBlank()) {
            throw new IllegalArgumentException("审计事件必须关联 Run");
        }
        event.sanitize(sanitizer);
        Run run = runRepository.findByIdForAuditUpdate(event.getRunId())
                .orElseThrow(() -> new IllegalArgumentException("审计事件关联的 Run 不存在"));
        if (!Objects.equals(run.getTenantId(), event.getTenantId())) {
            throw new IllegalArgumentException("审计事件租户与 Run 不一致");
        }
        long sequence = run.getAuditEventCount() + 1;
        String previousHash = run.getAuditHeadHash() == null ? GENESIS_HASH : run.getAuditHeadHash();
        String integrityHash = eventHash(event, sequence, previousHash);
        event.seal(sequence, previousHash, integrityHash);
        AuditEvent saved = auditEventRepository.save(event);
        run.updateAuditHead(sequence, integrityHash, headSignature(run.getId(), sequence, integrityHash));
        return saved;
    }

    /** 验证 Run 事件顺序、前序哈希、事件 HMAC 和 Run 链头签名。 */
    @Transactional(readOnly = true)
    public AuditIntegrityVerification verify(String runId) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("审计校验关联的 Run 不存在"));
        List<AuditEvent> events = auditEventRepository.findByRunIdOrderByIntegritySequenceAsc(runId);
        int legacyEventCount = (int) events.stream()
                .filter(event -> event.getIntegritySequence() == null || event.getIntegrityHash() == null)
                .count();
        List<AuditEvent> signedEvents = events.stream()
                .filter(event -> event.getIntegritySequence() != null && event.getIntegrityHash() != null)
                .toList();
        if (legacyEventCount > 0) {
            return failure(runId, signedEvents.size(), legacyEventCount, "LEGACY_UNSIGNED_EVENTS",
                    "存在迁移前未签名的审计记录，无法证明完整性");
        }
        if (run.getAuditEventCount() != signedEvents.size()) {
            return failure(runId, signedEvents.size(), 0, "AUDIT_EVENT_COUNT_MISMATCH",
                    "Run 审计事件计数与审计表不一致");
        }
        if (signedEvents.isEmpty() && run.getAuditEventCount() == 0
                && run.getAuditHeadHash() == null && run.getAuditHeadSignature() == null) {
            return new AuditIntegrityVerification(runId, true, 0, 0, null, "空审计链校验通过");
        }
        String expectedPreviousHash = GENESIS_HASH;
        long expectedSequence = 1;
        for (AuditEvent event : signedEvents) {
            if (event.getIntegritySequence() != expectedSequence) {
                return failure(runId, signedEvents.size(), 0, "AUDIT_SEQUENCE_MISMATCH",
                        "审计事件序号不连续");
            }
            if (!Objects.equals(expectedPreviousHash, event.getPreviousHash())) {
                return failure(runId, signedEvents.size(), 0, "AUDIT_PREVIOUS_HASH_MISMATCH",
                        "审计事件前序哈希不匹配");
            }
            String expectedHash = eventHash(event, expectedSequence, expectedPreviousHash);
            if (!constantTimeEquals(expectedHash, event.getIntegrityHash())) {
                return failure(runId, signedEvents.size(), 0, "AUDIT_EVENT_HASH_MISMATCH",
                        "审计事件内容或签名已被修改");
            }
            expectedPreviousHash = expectedHash;
            expectedSequence++;
        }
        if (!Objects.equals(expectedPreviousHash, run.getAuditHeadHash())) {
            return failure(runId, signedEvents.size(), 0, "AUDIT_HEAD_HASH_MISMATCH",
                    "Run 审计链头与事件链不一致");
        }
        String expectedHeadSignature = headSignature(run.getId(), run.getAuditEventCount(), expectedPreviousHash);
        if (!constantTimeEquals(expectedHeadSignature, run.getAuditHeadSignature())) {
            return failure(runId, signedEvents.size(), 0, "AUDIT_HEAD_SIGNATURE_MISMATCH",
                    "Run 审计链头签名无效");
        }
        return new AuditIntegrityVerification(runId, true, signedEvents.size(), 0, null, "审计链校验通过");
    }

    private AuditIntegrityVerification failure(String runId, int checkedEventCount, int legacyEventCount,
                                                String failureCode, String message) {
        return new AuditIntegrityVerification(runId, false, checkedEventCount, legacyEventCount,
                failureCode, message);
    }

    private String eventHash(AuditEvent event, long sequence, String previousHash) {
        return hmac(canonical("event-v1", event.getId(), Long.toString(sequence), previousHash,
                event.getTenantId(), event.getActorId(), event.getTraceId(), event.getRunId(), event.getStepId(),
                event.getEventType(), event.getMessage(), event.getMetadata(),
                Long.toString(event.getCreatedAt().toEpochMilli())));
    }

    private String headSignature(String runId, long eventCount, String headHash) {
        return hmac(canonical("head-v1", runId, Long.toString(eventCount), headHash));
    }

    private String canonical(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null) {
                builder.append("-1:");
            } else {
                builder.append(value.length()).append(':').append(value);
            }
        }
        return builder.toString();
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(integrityKey, HMAC_ALGORITHM));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算审计 HMAC", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}

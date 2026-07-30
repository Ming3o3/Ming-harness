package org.mingharness.policy;

import java.util.Set;

/** 策略引擎只接收确定性的身份与权限快照，不依赖模型自行判断。 */
public record PolicyContext(
        String tenantId,
        String userId,
        Set<String> permissions,
        boolean approvalGranted
) {
}

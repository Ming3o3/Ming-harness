package org.mingharness.security;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;

/** 保存当前 HTTP 请求身份，业务服务不直接读取未经认证的请求头。 */
public final class HarnessIdentityContext {

    private static final ThreadLocal<HarnessIdentity> CURRENT = new ThreadLocal<>();

    private HarnessIdentityContext() {
    }

    public static HarnessIdentity current() {
        return CURRENT.get();
    }

    public static HarnessIdentity require() {
        HarnessIdentity identity = CURRENT.get();
        if (identity == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请求未完成身份认证");
        }
        return identity;
    }

    static void set(HarnessIdentity identity) {
        CURRENT.set(identity);
    }

    static void clear() {
        CURRENT.remove();
    }
}

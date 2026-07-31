package org.mingharness.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Harness 身份认证配置。
 * apiKeys 每项格式为：明文Key|租户ID|用户ID|权限1,权限2；生产环境应由密钥系统注入。
 */
@ConfigurationProperties(prefix = "harness.auth")
public class HarnessAuthProperties {

    /** local 兼容本地请求头，api-key 启用配置化 API Key 认证。 */
    private String mode = "local";
    private String apiKeys = "";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(String apiKeys) {
        this.apiKeys = apiKeys;
    }
}

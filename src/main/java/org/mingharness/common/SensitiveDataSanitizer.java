package org.mingharness.common;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 在数据离开受控边界前移除常见凭证，避免密钥进入数据库、审计事件、模型请求或错误响应。
 *
 * 这不是 DLP 系统：它覆盖最常见、最容易误入日志的凭证格式。正式环境仍应配合密钥托管、
 * 网关脱敏和访问控制使用，不能把业务正文中的机密数据交给模型服务。
 */
@Component
public class SensitiveDataSanitizer {

    public static final String REDACTION_MARKER = "[REDACTED]";

    private static final List<Rule> RULES = List.of(
            // PEM 私钥通常跨多行，必须最先整体替换，避免只泄露其中一段。
            new Rule(Pattern.compile("-----BEGIN(?: [A-Z0-9]+)* PRIVATE KEY-----[\\s\\S]*?-----END(?: [A-Z0-9]+)* PRIVATE KEY-----",
                    Pattern.CASE_INSENSITIVE), REDACTION_MARKER),
            // HTTP Basic/Bearer 认证头和常见的环境变量、JSON 键值对。
            new Rule(Pattern.compile("(?i)([\\\"']?(?:authorization|proxy-authorization)[\\\"']?\\s*[:=]\\s*[\\\"']?bearer\\s+)[A-Za-z0-9._~+/=-]+([\\\"']?)"),
                    "$1" + REDACTION_MARKER + "$2"),
            new Rule(Pattern.compile("(?i)(\\\"(?:api[_-]?key|x-api-key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|passwd|secret|token|private[_-]?key)\\\"\\s*[:=]\\s*\\\")[^\\\"]*(\\\")"),
                    "$1" + REDACTION_MARKER + "$2"),
            new Rule(Pattern.compile("(?i)('(?:api[_-]?key|x-api-key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|passwd|secret|token|private[_-]?key)'\\s*[:=]\\s*')[^']*(')"),
                    "$1" + REDACTION_MARKER + "$2"),
            new Rule(Pattern.compile("(?i)(\\b(?:api[_-]?key|x-api-key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|passwd|secret|token|private[_-]?key)\\b\\s*[:=]\\s*)[^\\s,;}&\\]]+"),
                    "$1" + REDACTION_MARKER),
            // URL 用户名:密码形式，例如 PostgreSQL、Redis 或第三方 HTTP 地址。
            new Rule(Pattern.compile("(?i)((?:[a-z][a-z0-9+.-]*):\\/\\/[^\\s/:@]+:)[^@\\s]+(@)"),
                    "$1" + REDACTION_MARKER + "$2"),
            // 已签发 JWT 和常见厂商格式的长期 Token。
            new Rule(Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
                    REDACTION_MARKER),
            new Rule(Pattern.compile("\\b(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16})\\b"),
                    REDACTION_MARKER)
    );

    /** 返回可安全持久化、记录或发送到外部模型服务的文本。 */
    public String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String sanitized = value;
        for (Rule rule : RULES) {
            sanitized = rule.pattern().matcher(sanitized).replaceAll(rule.replacement());
        }
        return sanitized;
    }

    /** 用于长期记忆等不可逆数据，发现疑似凭证时应拒绝写入而不是仅做替换。 */
    public boolean containsSensitiveData(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return RULES.stream().anyMatch(rule -> rule.pattern().matcher(value).find());
    }

    private record Rule(Pattern pattern, String replacement) {
    }
}

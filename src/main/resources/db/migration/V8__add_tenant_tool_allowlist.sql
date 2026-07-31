-- 租户工具白名单；NULL/空字符串表示未启用白名单，保持旧租户兼容。
ALTER TABLE harness_tenant_policies ADD COLUMN allowed_tools VARCHAR(4000);

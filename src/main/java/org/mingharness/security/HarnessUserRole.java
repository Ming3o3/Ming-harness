package org.mingharness.security;

/**
 * 面向产品工作台的用户角色。角色是权限和资源关系之上的体验分组，
 * 不替代后端接口权限校验；同一用户可以同时拥有多个角色。
 */
public enum HarnessUserRole {
    ADMIN,
    TEACHER,
    STUDENT
}

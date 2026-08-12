package org.mingharness.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessRoleResolverTests {

    private final HarnessRoleResolver resolver = new HarnessRoleResolver();

    @Test
    void localDemoRoleShouldBeExplicitAndIndependentOfRequestPermissions() {
        HarnessIdentity identity = new HarnessIdentity("tenant-demo", "student-demo", Set.of(), "local");

        Set<HarnessUserRole> roles = resolver.resolve(identity, "student");

        assertEquals(Set.of(HarnessUserRole.STUDENT), roles);
        assertEquals(HarnessUserRole.STUDENT, resolver.primaryRole(roles));
    }

    @Test
    void trustedPermissionsShouldExposeMultipleProductRoles() {
        HarnessIdentity identity = new HarnessIdentity("tenant-a", "teacher-001",
                Set.of("education.assign", "education.read", "run.create"), "api-key");

        Set<HarnessUserRole> roles = resolver.resolve(identity, "admin");

        assertTrue(roles.contains(HarnessUserRole.TEACHER));
        assertTrue(roles.contains(HarnessUserRole.STUDENT));
        assertEquals(HarnessUserRole.TEACHER, resolver.primaryRole(roles));
    }

    @Test
    void adminShouldBePrimaryWhenIdentityHasAdministrationPermission() {
        HarnessIdentity identity = new HarnessIdentity("tenant-a", "admin-001",
                Set.of("ops.read", "education.read"), "oidc");

        Set<HarnessUserRole> roles = resolver.resolve(identity, null);

        assertEquals(Set.of(HarnessUserRole.ADMIN, HarnessUserRole.STUDENT), roles);
        assertEquals(HarnessUserRole.ADMIN, resolver.primaryRole(roles));
    }

    @Test
    void genericContextWriterShouldNotBeMisclassifiedAsTeacher() {
        HarnessIdentity identity = new HarnessIdentity("tenant-a", "operator-001",
                Set.of("context.write"), "api-key");

        assertEquals(Set.of(), resolver.resolve(identity, null));
    }

    @Test
    void anyGovernancePermissionShouldExposeAdminWorkspace() {
        HarnessIdentity identity = new HarnessIdentity("tenant-a", "operator-001",
                Set.of("model.configure"), "api-key");

        assertEquals(Set.of(HarnessUserRole.ADMIN), resolver.resolve(identity, null));
    }
}

package org.mingharness.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.WorkspaceProperties;
import org.mingharness.tool.WorkspaceToolSupport;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证工作区摘要仅暴露可安全显示的状态字段。 */
class WorkspaceStatusServiceTests {

    @TempDir
    Path workspaceRoot;

    @Test
    void shouldDescribeAccessibleGitWorkspaceWithoutReturningAbsolutePath() throws Exception {
        Files.createDirectories(workspaceRoot.resolve(".git"));
        WorkspaceStatusService service = new WorkspaceStatusService(support(true, true));

        var status = service.status();

        assertTrue(status.enabled());
        assertTrue(status.accessible());
        assertEquals(workspaceRoot.getFileName().toString(), status.displayName());
        assertTrue(status.gitRepository());
        assertTrue(status.commandExecutionEnabled());
        assertEquals(2, status.allowedCommandCount());
        assertTrue(status.writeRequiresApproval());
        assertTrue(status.absolutePathHidden());
    }

    @Test
    void shouldNotRevealOrProbeRootWhenWorkspaceIsDisabled() {
        WorkspaceStatusService service = new WorkspaceStatusService(support(false, false));

        var status = service.status();

        assertFalse(status.enabled());
        assertFalse(status.accessible());
        assertEquals("未连接本地工作区", status.displayName());
        assertTrue(status.absolutePathHidden());
    }

    private WorkspaceToolSupport support(boolean enabled, boolean execEnabled) {
        WorkspaceProperties properties = new WorkspaceProperties(enabled, workspaceRoot.toString(),
                100_000, 100_000, 100, 100, 20, 100, false,
                execEnabled, List.of("git", "mvn"), 30_000, 100_000, 16);
        return new WorkspaceToolSupport(properties, new ObjectMapper(), new SensitiveDataSanitizer());
    }
}

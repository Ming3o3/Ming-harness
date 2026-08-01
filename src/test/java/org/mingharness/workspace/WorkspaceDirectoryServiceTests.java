package org.mingharness.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mingharness.tool.ToolExecutionContext;
import org.mingharness.tool.WorkspaceListTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证登记路径加密、所属隔离和工具调用的 Run 级目录绑定。 */
@SpringBootTest
@TestPropertySource(properties = {
        "harness.workspace.enabled=true",
        "harness.workspace.local-registration-enabled=true"
})
class WorkspaceDirectoryServiceTests {

    @TempDir
    Path tempDir;

    @Autowired
    private WorkspaceDirectoryService workspaceDirectoryService;
    @Autowired
    private LocalWorkspaceRepository workspaceRepository;
    @Autowired
    private WorkspaceListTool workspaceListTool;

    @BeforeEach
    void cleanWorkspaces() {
        workspaceRepository.deleteAll();
    }

    @Test
    void shouldEncryptRegisteredRootAndResolveOnlyForItsOwner() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# local project");

        var registered = workspaceDirectoryService.register("tenant-local", "developer", "示例项目", tempDir.toString());
        LocalWorkspace stored = workspaceRepository.findById(registered.id()).orElseThrow();

        assertEquals("示例项目", registered.displayName());
        assertTrue(registered.accessible());
        assertFalse(stored.getRootPathCiphertext().contains(tempDir.toString()));
        assertEquals(tempDir.toRealPath(), workspaceDirectoryService
                .requireRoot(registered.id(), "tenant-local", "developer").toRealPath());
    }

    @Test
    void shouldExecuteWorkspaceToolInsideRunBoundWorkspaceInsteadOfConfiguredDefault() throws Exception {
        Files.writeString(tempDir.resolve("ProjectOnly.txt"), "只属于已授权项目");
        var registered = workspaceDirectoryService.register("tenant-local", "developer", "项目", tempDir.toString());

        String result = workspaceListTool.execute("{\"path\":\".\"}", new ToolExecutionContext(
                "run-local", "step-list", "tenant-local", "developer", registered.id(), "idempotency-local"));

        assertTrue(result.contains("ProjectOnly.txt"));
        assertTrue(!result.contains(tempDir.toString()));
    }
}

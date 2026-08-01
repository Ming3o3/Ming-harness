package org.mingharness.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.WorkspaceProperties;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工作区工具的路径隔离、文本边界和并发写保护测试。 */
class WorkspaceToolTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldListReadAndSearchTextInsideWorkspace() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/App.java"), "class App {\n  // harness\n}\n");
        WorkspaceToolSupport support = support();

        String listed = new WorkspaceListTool(support).execute("{\"path\":\"src\"}");
        assertTrue(listed.contains("App.java"));

        String read = new WorkspaceReadFileTool(support).execute(
                "{\"path\":\"src/App.java\",\"startLine\":2,\"endLine\":2}");
        assertTrue(read.contains("harness"));
        assertTrue(read.contains("\"sha256\""));

        String search = new WorkspaceSearchTool(support).execute(
                "{\"query\":\"harness\",\"path\":\"src\"}");
        assertTrue(search.contains("App.java"));
        assertTrue(search.contains("\"line\":2"));
    }

    @Test
    void shouldWriteAtomicallyAndRequireExpectedHashForOverwrite() throws Exception {
        WorkspaceToolSupport support = support();
        WorkspaceWriteFileTool tool = new WorkspaceWriteFileTool(support);

        String created = tool.execute("{\"path\":\"src/Main.java\",\"content\":\"class Main {}\"}");
        assertTrue(created.contains("\"created\":true"));
        Path target = tempDir.resolve("src/Main.java");
        assertEquals("class Main {}", Files.readString(target));

        BusinessException missingHash = assertThrows(BusinessException.class,
                () -> tool.execute("{\"path\":\"src/Main.java\",\"content\":\"changed\"}"));
        assertEquals("WORKSPACE_EXPECTED_HASH_REQUIRED", missingHash.getCode());

        String hash = new WorkspaceReadFileTool(support).execute("{\"path\":\"src/Main.java\"}")
                .replaceFirst(".*\\\"sha256\\\":\\\"([0-9a-f]{64})\\\".*", "$1");
        String updated = tool.execute("{\"path\":\"src/Main.java\",\"content\":\"changed\",\"expectedSha256\":\""
                + hash + "\"}");
        assertTrue(updated.contains("\"created\":false"));
        assertEquals("changed", Files.readString(target));
    }

    @Test
    void shouldRejectTraversalHiddenFilesAndSymlinks() throws Exception {
        WorkspaceToolSupport support = support();
        WorkspaceReadFileTool read = new WorkspaceReadFileTool(support);

        BusinessException traversal = assertThrows(BusinessException.class,
                () -> read.execute("{\"path\":\"../outside.txt\"}"));
        assertEquals("WORKSPACE_PATH_DENIED", traversal.getCode());

        Files.writeString(tempDir.resolve(".env"), "secret");
        BusinessException hidden = assertThrows(BusinessException.class,
                () -> read.execute("{\"path\":\".env\"}"));
        assertEquals("WORKSPACE_HIDDEN_PATH_DENIED", hidden.getCode());

        Path outside = tempDir.getParent().resolve("harness-workspace-outside.txt");
        Files.writeString(outside, "outside");
        try {
            Files.createSymbolicLink(tempDir.resolve("outside-link.txt"), outside);
            BusinessException symlink = assertThrows(BusinessException.class,
                    () -> read.execute("{\"path\":\"outside-link.txt\"}"));
            assertEquals("WORKSPACE_PATH_DENIED", symlink.getCode());
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void shouldFailClosedWhenWorkspaceIsDisabled() {
        WorkspaceProperties disabled = new WorkspaceProperties(false, tempDir.toString(),
                1000, 1000, 20, 20, 20, 20, false);
        WorkspaceToolSupport support = new WorkspaceToolSupport(disabled, new ObjectMapper(),
                new SensitiveDataSanitizer());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new WorkspaceListTool(support).execute("{}"));
        assertEquals("WORKSPACE_DISABLED", exception.getCode());
        assertFalse(Files.exists(tempDir.resolve("workspace")));
    }

    private WorkspaceToolSupport support() {
        WorkspaceProperties properties = new WorkspaceProperties(true, tempDir.toString(),
                100_000, 100_000, 100, 100, 20, 100, false);
        return new WorkspaceToolSupport(properties, new ObjectMapper(), new SensitiveDataSanitizer());
    }
}

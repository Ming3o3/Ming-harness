package org.mingharness.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mingharness.common.BusinessException;
import org.mingharness.common.SensitiveDataSanitizer;
import org.mingharness.config.WorkspaceProperties;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    @Test
    void shouldExecuteOnlyWhitelistedCommandWithoutShellInterpretation() throws Exception {
        String command = executableScript("echo.sh", "#!/bin/sh\nprintf 'value:%s:%s' \"$1\" \"${MODEL_API_KEY:-missing}\"\n");
        WorkspaceExecTool tool = new WorkspaceExecTool(execSupport(command, 5_000, 20_000, 8));

        String result = tool.execute("{\"command\":\"" + command
                + "\",\"args\":[\"hello;touch should-not-exist\"]}");

        assertTrue(result.contains("\"exitCode\":0"));
        assertTrue(result.contains("value:hello;touch should-not-exist:missing"));
        assertTrue(result.contains("\"ok\":true"));
        assertFalse(Files.exists(tempDir.resolve("should-not-exist")));
        assertTrue(tool.audit("{}", result).message().contains("退出码=0"));
    }

    @Test
    void shouldRejectUnknownCommandAndDisabledExecution() throws Exception {
        String command = executableScript("echo.sh", "#!/bin/sh\necho ok\n");
        WorkspaceExecTool disabledTool = new WorkspaceExecTool(support());
        BusinessException disabled = assertThrows(BusinessException.class,
                () -> disabledTool.execute("{\"command\":\"" + command + "\"}"));
        assertEquals("WORKSPACE_EXEC_DISABLED", disabled.getCode());

        WorkspaceExecTool notAllowed = new WorkspaceExecTool(execSupport("./other.sh", 5_000, 20_000, 8));
        BusinessException denied = assertThrows(BusinessException.class,
                () -> notAllowed.execute("{\"command\":\"" + command + "\"}"));
        assertEquals("WORKSPACE_COMMAND_NOT_ALLOWED", denied.getCode());
    }

    @Test
    void shouldStopTimedOutCommandAndLimitOutput() throws Exception {
        String slow = executableScript("slow.sh", "#!/bin/sh\nsleep 2\necho late\n");
        WorkspaceExecTool slowTool = new WorkspaceExecTool(execSupport(slow, 3_000, 20_000, 8));
        String timeout = slowTool.execute("{\"command\":\"" + slow + "\",\"timeoutMs\":100}");
        assertTrue(timeout.contains("\"timedOut\":true"));

        String noisy = executableScript("noisy.sh", "#!/bin/sh\nprintf '0123456789%.0s' $(seq 1 1000)\n");
        WorkspaceExecTool noisyTool = new WorkspaceExecTool(execSupport(noisy, 5_000, 1_024, 8));
        String limited = noisyTool.execute("{\"command\":\"" + noisy + "\",\"maxOutputBytes\":1024}");
        assertTrue(limited.contains("\"outputTruncated\":true"));
    }

    @Test
    void shouldApplyExactEditsWithHashAndAudit() throws Exception {
        WorkspaceToolSupport support = support();
        Path target = tempDir.resolve("src/App.java");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "class App {\n  String name = \"old\";\n}\n");
        String hash = support.sha256(Files.readAllBytes(target));
        WorkspaceEditFileTool tool = new WorkspaceEditFileTool(support);

        String result = tool.execute("{\"path\":\"src/App.java\",\"expectedSha256\":\""
                + hash + "\",\"edits\":[{\"oldText\":\"old\",\"newText\":\"new\"}]}");

        assertTrue(result.contains("\"replacements\":1"));
        assertEquals("class App {\n  String name = \"new\";\n}\n", Files.readString(target));
        assertTrue(tool.audit("{}", result).message().contains("src/App.java"));
    }

    @Test
    void shouldRejectStaleOrAmbiguousEdits() throws Exception {
        WorkspaceToolSupport support = support();
        Path target = tempDir.resolve("App.java");
        Files.writeString(target, "value\nvalue\n");
        String hash = support.sha256(Files.readAllBytes(target));
        WorkspaceEditFileTool tool = new WorkspaceEditFileTool(support);

        BusinessException ambiguous = assertThrows(BusinessException.class,
                () -> tool.execute("{\"path\":\"App.java\",\"expectedSha256\":\""
                        + hash + "\",\"edits\":[{\"oldText\":\"value\",\"newText\":\"next\"}]}"));
        assertEquals("WORKSPACE_EDIT_AMBIGUOUS", ambiguous.getCode());

        String replaced = tool.execute("{\"path\":\"App.java\",\"expectedSha256\":\""
                + hash + "\",\"edits\":[{\"oldText\":\"value\",\"newText\":\"next\",\"replaceAll\":true}]}");
        assertTrue(replaced.contains("\"replacements\":2"));
        assertEquals("next\nnext\n", Files.readString(target));

        Files.writeString(target, "changed\nvalue\n");
        BusinessException stale = assertThrows(BusinessException.class,
                () -> tool.execute("{\"path\":\"App.java\",\"expectedSha256\":\""
                        + hash + "\",\"edits\":[{\"oldText\":\"value\",\"newText\":\"next\"}]}"));
        assertEquals("WORKSPACE_FILE_CHANGED", stale.getCode());
    }

    @Test
    void shouldExposeWorkspaceToolsOnlyWhenEnabled() {
        WorkspaceToolSupport support = support();
        WorkspaceProperties disabled = new WorkspaceProperties(false, tempDir.toString(),
                1000, 1000, 20, 20, 20, 20, false);
        WorkspaceToolSupport disabledSupport = new WorkspaceToolSupport(disabled, new ObjectMapper(),
                new SensitiveDataSanitizer());

        assertTrue(new WorkspaceEditFileTool(support).available());
        assertFalse(new WorkspaceEditFileTool(disabledSupport).available());
        assertFalse(new WorkspaceReadFileTool(disabledSupport).available());
        assertFalse(new WorkspaceWriteFileTool(disabledSupport).available());
        assertFalse(new WorkspaceGitStatusTool(disabledSupport).available());
        assertFalse(new WorkspaceGitDiffTool(disabledSupport).available());
    }

    @Test
    void shouldInspectGitStatusAndDiffOnlyInsideWorkspace() throws Exception {
        runGit(tempDir, "init", "-q");
        Path target = tempDir.resolve("App.java");
        Path hidden = tempDir.resolve(".env");
        Files.writeString(target, "old\n");
        Files.writeString(hidden, "SECRET=initial\n");
        runGit(tempDir, "add", "App.java", ".env");
        runGit(tempDir, "-c", "user.name=Harness Test", "-c", "user.email=harness@example.com",
                "commit", "-qm", "initial");
        Files.writeString(target, "new\n");
        Files.writeString(hidden, "SECRET=changed\n");

        WorkspaceToolSupport support = support();
        String status = new WorkspaceGitStatusTool(support).execute("{}");
        assertTrue(status.contains("App.java"));
        assertTrue(status.contains("\"clean\":false"));
        assertFalse(status.contains(".env"));

        WorkspaceGitDiffTool diffTool = new WorkspaceGitDiffTool(support);
        String diff = diffTool.execute("{\"path\":\"App.java\"}");
        assertTrue(diff.contains("-old"));
        assertTrue(diff.contains("+new"));
        assertTrue(diff.contains("\"hasChanges\":true"));

        String allVisibleDiff = diffTool.execute("{}");
        assertTrue(allVisibleDiff.contains("App.java"));
        assertFalse(allVisibleDiff.contains(".env"));
        assertFalse(allVisibleDiff.contains("SECRET=changed"));

        runGit(tempDir, "add", "App.java");
        String stagedDiff = diffTool.execute("{\"path\":\"App.java\",\"staged\":true}");
        assertTrue(stagedDiff.contains("-old"));
        assertTrue(stagedDiff.contains("+new"));

        BusinessException traversal = assertThrows(BusinessException.class,
                () -> diffTool.execute("{\"path\":\"../outside.txt\"}"));
        assertEquals("WORKSPACE_PATH_DENIED", traversal.getCode());

        BusinessException hiddenPath = assertThrows(BusinessException.class,
                () -> diffTool.execute("{\"path\":\".env\"}"));
        assertEquals("WORKSPACE_HIDDEN_PATH_DENIED", hiddenPath.getCode());
    }

    @Test
    void shouldFailClearlyWhenWorkspaceIsNotGitRepository() {
        WorkspaceToolSupport support = support();
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new WorkspaceGitStatusTool(support).execute("{}"));
        assertEquals("WORKSPACE_GIT_REPOSITORY_INVALID", exception.getCode());
    }

    private WorkspaceToolSupport support() {
        WorkspaceProperties properties = new WorkspaceProperties(true, tempDir.toString(),
                100_000, 100_000, 100, 100, 20, 100, false);
        return new WorkspaceToolSupport(properties, new ObjectMapper(), new SensitiveDataSanitizer());
    }

    private WorkspaceToolSupport execSupport(String command, int timeoutMs,
                                             int outputBytes, int maxArgs) {
        WorkspaceProperties properties = new WorkspaceProperties(true, tempDir.toString(),
                100_000, 100_000, 100, 100, 20, 100, false,
                true, List.of(command), timeoutMs, outputBytes, maxArgs);
        return new WorkspaceToolSupport(properties, new ObjectMapper(), new SensitiveDataSanitizer());
    }

    private String executableScript(String name, String content) throws Exception {
        Path script = tempDir.resolve(name);
        Files.writeString(script, content);
        try {
            Files.setPosixFilePermissions(script, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException exception) {
            throw new IllegalStateException("当前测试环境不支持执行工作区脚本", exception);
        }
        return "./" + name;
    }

    private void runGit(Path directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS),
                "Git 测试命令未在超时前结束");
        assertEquals(0, process.exitValue(), "Git 测试命令失败: "
                + new String(process.getInputStream().readAllBytes()));
    }
}

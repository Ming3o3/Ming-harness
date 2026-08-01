package org.mingharness.workspace;

import org.mingharness.tool.WorkspaceToolSupport;
import org.mingharness.workspace.api.WorkspaceStatusView;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * 将本机配置的受控目录转换为安全的控制台状态。
 *
 * <p>这里绝不返回 {@link Path} 或配置中的根目录字符串，避免前端接口、浏览器缓存和模型
 * 上下文泄露用户的本机绝对路径。</p>
 */
@Service
public class WorkspaceStatusService {

    private final WorkspaceToolSupport workspace;

    public WorkspaceStatusService(WorkspaceToolSupport workspace) {
        this.workspace = workspace;
    }

    public WorkspaceStatusView status() {
        if (!workspace.properties().enabled()) {
            return WorkspaceStatusView.disabled();
        }
        Path root = workspace.root();
        boolean accessible = Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(root);
        String displayName = accessible ? displayName(root) : "工作区目录不可用";
        boolean gitRepository = accessible && isRegularGitDirectory(root.resolve(".git"));
        return new WorkspaceStatusView(true, accessible, displayName, gitRepository,
                workspace.properties().execEnabled(), workspace.properties().allowedCommands().size(),
                true, true);
    }

    private boolean isRegularGitDirectory(Path gitDirectory) {
        return !Files.isSymbolicLink(gitDirectory)
                && Files.isDirectory(gitDirectory, LinkOption.NOFOLLOW_LINKS);
    }

    private String displayName(Path root) {
        Path fileName = root.getFileName();
        String value = fileName == null ? "本地工作区" : fileName.toString();
        // 文件名仅用于界面识别；删除控制字符并限制长度，防止目录名破坏控制台展示。
        value = value.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (value.isBlank()) return "本地工作区";
        return value.substring(0, Math.min(value.length(), 120));
    }
}

package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, HarnessTool> tools;

    public ToolRegistry(List<HarnessTool> tools) {
        this.tools = tools.stream().collect(Collectors.toUnmodifiableMap(
                tool -> tool.definition().name(),
                Function.identity()
        ));
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream()
                .map(HarnessTool::definition)
                .sorted((left, right) -> left.name().compareTo(right.name()))
                .toList();
    }

    public HarnessTool get(String name) {
        HarnessTool tool = tools.get(name);
        if (tool == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "工具不存在: " + name);
        }
        return tool;
    }
}

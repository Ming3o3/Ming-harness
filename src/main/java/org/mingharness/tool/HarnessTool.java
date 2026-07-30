package org.mingharness.tool;

public interface HarnessTool {

    ToolDefinition definition();

    String execute(String input);
}

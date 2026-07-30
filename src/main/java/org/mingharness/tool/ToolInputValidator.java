package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.lang.reflect.Array;

/** 对模型或用户提供的工具参数做最小确定性校验，拒绝空输入和缺少必填字段。 */
@Component
public class ToolInputValidator {

    public void validate(ToolDefinition definition, String input) {
        if (input == null || input.isBlank()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_INPUT_INVALID",
                    "工具输入不能为空: " + definition.name());
        }
        Object required = definition.inputSchema().get("required");
        if (required == null) {
            return;
        }
        Iterable<?> requiredFields = required instanceof Collection<?> collection
                ? collection
                : () -> new java.util.Iterator<>() {
                    private int index;

                    @Override
                    public boolean hasNext() {
                        return required.getClass().isArray() && index < Array.getLength(required);
                    }

                    @Override
                    public Object next() {
                        return Array.get(required, index++);
                    }
                };
        for (Object field : requiredFields) {
            if ("message".equals(String.valueOf(field)) && input.isBlank()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_INPUT_INVALID",
                        "工具输入缺少必填字段 message: " + definition.name());
            }
        }
        Object type = definition.inputSchema().get("type");
        if ("object".equals(type) && input.startsWith("{") && !input.endsWith("}")) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_INPUT_INVALID",
                    "工具输入对象格式不完整: " + definition.name());
        }
    }
}

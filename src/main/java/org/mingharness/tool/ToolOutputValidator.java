package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.util.Collection;

/** 校验工具返回值，避免未经业务校验的结果直接进入后续步骤。 */
@Component
public class ToolOutputValidator {

    public void validate(ToolDefinition definition, String output) {
        if (output == null || output.isBlank()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_OUTPUT_INVALID",
                    "工具返回值不能为空: " + definition.name());
        }
        Object type = definition.outputSchema().get("type");
        if ("object".equals(type) && output.startsWith("{") && !output.endsWith("}")) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_OUTPUT_INVALID",
                    "工具返回对象格式不完整: " + definition.name());
        }
        Object required = definition.outputSchema().get("required");
        if (required instanceof Collection<?> requiredFields && requiredFields.stream()
                .anyMatch(field -> !output.contains(String.valueOf(field)))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_OUTPUT_INVALID",
                    "工具返回值缺少必填字段: " + definition.name());
        }
        if (required != null && required.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(required); index++) {
                if (!output.contains(String.valueOf(Array.get(required, index)))) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_OUTPUT_INVALID",
                            "工具返回值缺少必填字段: " + definition.name());
                }
            }
        }
    }
}

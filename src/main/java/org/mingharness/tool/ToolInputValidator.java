package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** 对模型或用户提供的工具参数执行 JSON Schema 校验，并保留旧字符串工具兼容性。 */
@Component
public class ToolInputValidator {

    private final JsonSchemaValidator schemaValidator;

    public ToolInputValidator(JsonSchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
    }

    public void validate(ToolDefinition definition, String input) {
        if (input == null || input.isBlank()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_INPUT_INVALID",
                    "工具输入不能为空: " + definition.name());
        }
        List<String> errors = schemaValidator.validate(input, definition.inputSchema());
        if (!errors.isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_INPUT_INVALID",
                    "工具输入不符合 JSON Schema: " + definition.name() + " - " + String.join("；", errors));
        }
    }
}

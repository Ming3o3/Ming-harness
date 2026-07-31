package org.mingharness.tool;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** 校验工具返回值，避免未经 JSON Schema 校验的结果进入后续步骤。 */
@Component
public class ToolOutputValidator {

    private final JsonSchemaValidator schemaValidator;

    public ToolOutputValidator(JsonSchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
    }

    public void validate(ToolDefinition definition, String output) {
        if (output == null || output.isBlank()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_OUTPUT_INVALID",
                    "工具返回值不能为空: " + definition.name());
        }
        List<String> errors = schemaValidator.validate(output, definition.outputSchema());
        if (!errors.isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOOL_OUTPUT_INVALID",
                    "工具返回值不符合 JSON Schema: " + definition.name() + " - " + String.join("；", errors));
        }
    }
}

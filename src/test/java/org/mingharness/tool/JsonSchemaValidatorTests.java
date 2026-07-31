package org.mingharness.tool;

import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 结构化工具输入输出校验测试。 */
class JsonSchemaValidatorTests {

    private final JsonSchemaValidator validator = new JsonSchemaValidator(new ObjectMapper());
    private final ToolInputValidator inputValidator = new ToolInputValidator(validator);
    private final ToolOutputValidator outputValidator = new ToolOutputValidator(validator);

    @Test
    void shouldValidateNestedObjectArrayAndRequiredFields() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "required", List.of("message", "items"),
                "additionalProperties", false,
                "properties", Map.of(
                        "message", Map.of("type", "string", "minLength", 3),
                        "items", Map.of(
                                "type", "array",
                                "minItems", 1,
                                "items", Map.of("type", "integer", "minimum", 1)
                        )
                )
        );

        assertTrue(validator.validate("{\"message\":\"订单状态\",\"items\":[1,2]}", schema).isEmpty());

        List<String> errors = validator.validate(
                "{\"message\":\"x\",\"items\":[0],\"unexpected\":true}", schema);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("message")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("items[0]")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("unexpected")));
    }

    @Test
    void shouldRejectMalformedJsonForStructuredTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "required", List.of("message"),
                "properties", Map.of("message", Map.of("type", "string"))
        );

        List<String> errors = validator.validate("{\"message\":", schema);

        assertTrue(errors.stream().anyMatch(error -> error.contains("有效 JSON")));
    }

    @Test
    void shouldRejectTrailingDocumentsAndDuplicateFields() {
        Map<String, Object> schema = Map.of("type", "object");

        assertFalse(validator.validate("{\"a\":1}{\"b\":2}", schema).isEmpty());
        assertFalse(validator.validate("{\"a\":1,\"a\":2}", schema).isEmpty());
    }

    @Test
    void shouldKeepExplicitLegacyTextCompatibility() {
        Map<String, Object> schema = Map.of(
                "type", "string",
                "minLength", 2,
                "x-harness-legacy-text", true
        );

        assertTrue(validator.validate("普通文本", schema).isEmpty());
        assertFalse(validator.validate("x", schema).isEmpty());
        assertFalse(validator.validate("普通文本", Map.of("type", "object")).isEmpty());
    }

    @Test
    void shouldValidateEnumFormatAndOneOf() {
        Map<String, Object> schema = Map.of(
                "oneOf", List.of(
                        Map.of("type", "string", "format", "uuid"),
                        Map.of("type", "string", "enum", List.of("auto"))
                )
        );

        assertTrue(validator.validate("\"auto\"", schema).isEmpty());
        assertTrue(validator.validate("\"not-a-uuid\"", schema).get(0).contains("oneOf"));
    }

    @Test
    void shouldTreatEmptyCompositionSchemaAsAnyValue() {
        Map<String, Object> schema = Map.of("anyOf", List.of(Map.of()));

        assertTrue(validator.validate("{\"anything\":true}", schema).isEmpty());
    }

    @Test
    void shouldUseSchemaValidationInsteadOfSubstringMatchingForOutput() {
        ToolDefinition definition = new ToolDefinition(
                "test.structured-output", "结构化输出测试", true, "LOW", false,
                Map.of(), Set.of(), 30_000, 1, "DENY_EXTERNAL",
                Map.of(
                        "type", "object",
                        "required", List.of("name"),
                        "additionalProperties", false,
                        "properties", Map.of("name", Map.of("type", "string"))
                )
        );

        assertDoesNotThrow(() -> outputValidator.validate(definition, "{\"name\":\"Ming\"}"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> outputValidator.validate(definition, "{\"nickname\":\"name\"}"));
        assertTrue(exception.getMessage().contains("必填字段"));
    }

    @Test
    void shouldApplyInputValidationThroughPublicValidator() {
        ToolDefinition definition = new ToolDefinition(
                "test.structured-input", "结构化输入测试", true, "LOW", false,
                Map.of(
                        "type", "object",
                        "required", List.of("query"),
                        "properties", Map.of("query", Map.of("type", "string", "minLength", 2))
                )
        );

        assertDoesNotThrow(() -> inputValidator.validate(definition, "{\"query\":\"订单\"}"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> inputValidator.validate(definition, "{\"query\":1}"));
        assertTrue(exception.getMessage().contains("JSON Schema"));
    }
}

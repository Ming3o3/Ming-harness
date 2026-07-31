package org.mingharness.tool;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 对工具输入和输出执行确定性的 JSON Schema 校验。
 *
 * <p>Harness 不把模型输出当作可信数据。这里使用 Jackson 先解析 JSON，
 * 再递归执行常用 JSON Schema 约束，避免使用字符串 contains 判断字段是否存在。
 * 为兼容早期字符串工具，schema 可以显式声明 {@code x-harness-legacy-text=true}，
 * 此时未加引号的普通文本会按 JSON 字符串节点校验；结构化工具不会自动降级。</p>
 */
@Component
public class JsonSchemaValidator {

    private static final int MAX_ERRORS = 20;

    private final ObjectMapper objectMapper;

    public JsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 返回所有可操作的校验错误；空列表表示校验通过。 */
    public List<String> validate(String rawValue, Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return List.of();
        }
        if (rawValue == null || rawValue.isBlank()) {
            return List.of("值不能为空");
        }

        JsonNode value;
        try {
            // 工具边界拒绝重复字段和尾随的第二个 JSON 文档，避免解析器差异造成校验绕过。
            value = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                            DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .readTree(rawValue);
        } catch (JacksonException exception) {
            if (!Boolean.TRUE.equals(schema.get("x-harness-legacy-text"))) {
                return List.of("值必须是有效 JSON");
            }
            // 只有工具定义明确允许时才兼容旧的未加引号文本输入。
            value = objectMapper.stringNode(rawValue);
        }
        if (value == null || value.isMissingNode()) {
            return List.of("值必须包含一个 JSON 文档");
        }

        List<String> errors = new ArrayList<>();
        validateNode(value, schema, "$", errors);
        return List.copyOf(errors);
    }

    private void validateNode(JsonNode value, Map<String, Object> schema,
                              String path, List<String> errors) {
        if (errors.size() >= MAX_ERRORS || schema == null || schema.isEmpty()) {
            return;
        }

        validateCompositions(value, schema, path, errors);
        if (errors.size() >= MAX_ERRORS) {
            return;
        }

        Set<String> types = stringSet(schema.get("type"));
        if (!types.isEmpty() && types.stream().noneMatch(type -> matchesType(value, type))) {
            addError(errors, path + " 类型必须是 " + String.join(" 或 ", types));
            return;
        }

        Object enumValues = schema.get("enum");
        if (enumValues != null && !containsJsonValue(enumValues, value)) {
            addError(errors, path + " 不在允许的枚举值中");
        }
        if (schema.containsKey("const") && !jsonEquals(schema.get("const"), value)) {
            addError(errors, path + " 必须等于 const 指定的值");
        }

        if (value.isObject()) {
            validateObject(value, schema, path, errors);
        } else if (value.isArray()) {
            validateArray(value, schema, path, errors);
        } else if (value.isTextual()) {
            validateString(value.asString(), schema, path, errors);
        } else if (value.isNumber()) {
            validateNumber(value.decimalValue(), schema, path, errors);
        }

        Object format = schema.get("format");
        if (format != null && value.isTextual()) {
            validateFormat(value.asString(), String.valueOf(format), path, errors);
        }
    }

    private void validateCompositions(JsonNode value, Map<String, Object> schema,
                                      String path, List<String> errors) {
        Object allOf = schema.get("allOf");
        for (Map<String, Object> branch : schemaMaps(allOf)) {
            validateNode(value, branch, path, errors);
        }

        Object anyOf = schema.get("anyOf");
        if (anyOf != null) {
            int matched = 0;
            for (Map<String, Object> branch : schemaMaps(anyOf)) {
                if (validateNodeWithoutErrors(value, branch)) {
                    matched++;
                }
            }
            if (matched == 0) {
                addError(errors, path + " 不满足 anyOf 条件");
            }
        }

        Object oneOf = schema.get("oneOf");
        if (oneOf != null) {
            int matched = 0;
            for (Map<String, Object> branch : schemaMaps(oneOf)) {
                if (validateNodeWithoutErrors(value, branch)) {
                    matched++;
                }
            }
            if (matched != 1) {
                addError(errors, path + " 必须恰好满足一个 oneOf 条件");
            }
        }

        Object not = schema.get("not");
        if (not instanceof Map<?, ?> notSchema && validateNodeWithoutErrors(value, castMap(notSchema))) {
            addError(errors, path + " 不允许匹配 not 条件");
        }
    }

    private boolean validateNodeWithoutErrors(JsonNode value, Map<String, Object> schema) {
        List<String> branchErrors = new ArrayList<>();
        validateNode(value, schema, "$", branchErrors);
        return branchErrors.isEmpty();
    }

    private void validateObject(JsonNode value, Map<String, Object> schema,
                                String path, List<String> errors) {
        int size = value.size();
        numberConstraint(schema.get("minProperties"), BigDecimal.valueOf(size), path,
                "属性数量不能少于", errors, false);
        numberConstraint(schema.get("maxProperties"), BigDecimal.valueOf(size), path,
                "属性数量不能超过", errors, true);

        for (Object requiredField : values(schema.get("required"))) {
            String field = String.valueOf(requiredField);
            if (!value.has(field)) {
                addError(errors, path + "." + field + " 为必填字段");
            }
        }

        Map<String, Object> properties = castMap(schema.get("properties"));
        boolean additionalAllowed = !Boolean.FALSE.equals(schema.get("additionalProperties"));
        for (String field : value.propertyNames()) {
            JsonNode child = value.get(field);
            if (properties.containsKey(field)) {
                validateNode(child, castMap(properties.get(field)), path + "." + field, errors);
            } else if (!additionalAllowed) {
                addError(errors, path + " 不允许出现未知字段 " + field);
            } else if (schema.get("additionalProperties") instanceof Map<?, ?> additionalSchema) {
                validateNode(child, castMap(additionalSchema), path + "." + field, errors);
            }
        }
    }

    private void validateArray(JsonNode value, Map<String, Object> schema,
                               String path, List<String> errors) {
        int size = value.size();
        numberConstraint(schema.get("minItems"), BigDecimal.valueOf(size), path,
                "元素数量不能少于", errors, false);
        numberConstraint(schema.get("maxItems"), BigDecimal.valueOf(size), path,
                "元素数量不能超过", errors, true);

        if (Boolean.TRUE.equals(schema.get("uniqueItems"))) {
            Set<JsonNode> unique = new HashSet<>();
            for (JsonNode item : value) {
                if (!unique.add(item)) {
                    addError(errors, path + " 不能包含重复元素");
                    break;
                }
            }
        }

        Map<String, Object> itemSchema = castMap(schema.get("items"));
        if (!itemSchema.isEmpty()) {
            int index = 0;
            for (JsonNode item : value) {
                validateNode(item, itemSchema, path + "[" + index++ + "]", errors);
            }
        }
    }

    private void validateString(String value, Map<String, Object> schema,
                                String path, List<String> errors) {
        int length = value.codePointCount(0, value.length());
        numberConstraint(schema.get("minLength"), BigDecimal.valueOf(length), path,
                "长度不能少于", errors, false);
        numberConstraint(schema.get("maxLength"), BigDecimal.valueOf(length), path,
                "长度不能超过", errors, true);
        Object pattern = schema.get("pattern");
        if (pattern != null) {
            try {
                if (!Pattern.compile(String.valueOf(pattern)).matcher(value).find()) {
                    addError(errors, path + " 不符合 pattern 约束");
                }
            } catch (PatternSyntaxException exception) {
                addError(errors, path + " 的 schema pattern 无效");
            }
        }
    }

    private void validateNumber(BigDecimal value, Map<String, Object> schema,
                                String path, List<String> errors) {
        numberConstraint(schema.get("minimum"), value, path, "不能小于", errors, false);
        numberConstraint(schema.get("maximum"), value, path, "不能大于", errors, true);
        Object exclusiveMinimum = schema.get("exclusiveMinimum");
        if (exclusiveMinimum instanceof Number number && value.compareTo(new BigDecimal(number.toString())) <= 0) {
            addError(errors, path + " 必须大于 exclusiveMinimum");
        }
        Object exclusiveMaximum = schema.get("exclusiveMaximum");
        if (exclusiveMaximum instanceof Number number && value.compareTo(new BigDecimal(number.toString())) >= 0) {
            addError(errors, path + " 必须小于 exclusiveMaximum");
        }
        Object multipleOf = schema.get("multipleOf");
        if (multipleOf instanceof Number number) {
            BigDecimal divisor = new BigDecimal(number.toString());
            if (divisor.signum() > 0 && value.remainder(divisor).signum() != 0) {
                addError(errors, path + " 必须是 multipleOf 的倍数");
            }
        }
    }

    private void validateFormat(String value, String format, String path, List<String> errors) {
        boolean valid = switch (format) {
            case "email" -> value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
            case "uuid" -> parseUuid(value);
            case "date-time" -> parseInstant(value);
            case "uri", "uri-reference" -> parseUri(value);
            default -> true;
        };
        if (!valid) {
            addError(errors, path + " 不符合 format=" + format + " 约束");
        }
    }

    private boolean matchesType(JsonNode value, String type) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private boolean containsJsonValue(Object candidates, JsonNode value) {
        for (Object candidate : values(candidates)) {
            if (jsonEquals(candidate, value)) {
                return true;
            }
        }
        return false;
    }

    private boolean jsonEquals(Object candidate, JsonNode value) {
        try {
            JsonNode candidateNode = candidate instanceof JsonNode node
                    ? node : objectMapper.valueToTree(candidate);
            if (candidateNode.isNumber() && value.isNumber()) {
                return candidateNode.decimalValue().compareTo(value.decimalValue()) == 0;
            }
            return candidateNode.equals(value);
        } catch (JacksonException exception) {
            return false;
        }
    }

    private void numberConstraint(Object expected, BigDecimal actual, String path,
                                  String message, List<String> errors, boolean upperBound) {
        if (!(expected instanceof Number number)) {
            return;
        }
        BigDecimal limit = new BigDecimal(number.toString());
        boolean invalid = upperBound ? actual.compareTo(limit) > 0 : actual.compareTo(limit) < 0;
        if (invalid) {
            addError(errors, path + " " + message + " " + limit.stripTrailingZeros().toPlainString());
        }
    }

    private boolean parseUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean parseInstant(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean parseUri(String value) {
        try {
            URI uri = URI.create(value);
            return !uri.toString().isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void addError(List<String> errors, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        }
    }

    private Set<String> stringSet(Object value) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (Object item : values(value)) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private List<Object> values(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(java.lang.reflect.Array.get(value, index));
            }
            return result;
        }
        return List.of(value);
    }

    private List<Map<String, Object>> schemaMaps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values(value)) {
            // 空 schema 在 JSON Schema 中表示“允许任意值”，不能被误过滤掉。
            if (item instanceof Map<?, ?> map) {
                result.add(castMap(map));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return (Map<String, Object>) map;
    }
}

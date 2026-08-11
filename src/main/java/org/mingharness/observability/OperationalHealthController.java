package org.mingharness.observability;

import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.mingharness.model.ModelConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 面向控制台的健康摘要接口。
 *
 * <p>Actuator 的公开健康探针只返回整体状态；控制台需要知道数据库、Redis、RabbitMQ
 * 等组件状态，因此通过受 RBAC 保护的 API 返回状态摘要，绝不透出连接串、异常堆栈或组件详情。</p>
 */
@RestController
@RequestMapping("/api/health")
public class OperationalHealthController {

    private final HealthEndpoint healthEndpoint;
    private final HarnessMetrics metrics;
    private final ModelConfig modelConfig;

    public OperationalHealthController(HealthEndpoint healthEndpoint, HarnessMetrics metrics,
                                       ModelConfig modelConfig) {
        this.healthEndpoint = healthEndpoint;
        this.metrics = metrics;
        this.modelConfig = modelConfig;
    }

    @GetMapping
    public OperationalHealthView health() {
        HealthDescriptor component = healthEndpoint.health();
        Map<String, String> components = new TreeMap<>();
        if (component instanceof CompositeHealthDescriptor compositeHealth) {
            compositeHealth.getComponents().forEach((name, value) ->
                    components.put(name, value.getStatus().getCode()));
        }
        return new OperationalHealthView(
                component.getStatus().getCode(),
                new LinkedHashMap<>(components),
                metrics.operationalSnapshot(),
                new ModelHealthView(modelConfig.enabled(),
                        modelConfig.enabled() ? "external" : "demo",
                        modelConfig.enabled() ? modelConfig.name() : "demo-model"),
                new EducationCapabilityView(
                        "education-agent/v1",
                        true,
                        true,
                        true)
        );
    }

    /** 只暴露状态码，不暴露 Actuator 组件 details。 */
    public record OperationalHealthView(String status,
                                        Map<String, String> components,
                                        HarnessMetrics.OperationalSnapshot runtime,
                                        ModelHealthView model,
                                        EducationCapabilityView education) {
    }

    /** 只返回模型模式和名称，不返回供应商地址、API Key 或其他连接细节。 */
    public record ModelHealthView(boolean enabled, String mode, String modelName) {
    }

    /**
     * 前端据此确认当前连接的 Runtime 是否包含教育 Agent 能力。
     *
     * <p>旧版桌面 Runtime 仍可能返回基础健康摘要，但无法识别课程绑定 Run；
     * 这些字段不包含租户、课程或学习者数据，只用于兼容性诊断。</p>
     */
    public record EducationCapabilityView(String apiVersion,
                                          boolean courseBoundRunsEnabled,
                                          boolean learnerStateEnabled,
                                          boolean formativeEvidenceEnabled) {
    }
}

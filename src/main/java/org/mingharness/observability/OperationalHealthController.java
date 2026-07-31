package org.mingharness.observability;

import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
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

    public OperationalHealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
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
                new LinkedHashMap<>(components)
        );
    }

    /** 只暴露状态码，不暴露 Actuator 组件 details。 */
    public record OperationalHealthView(String status, Map<String, String> components) {
    }
}

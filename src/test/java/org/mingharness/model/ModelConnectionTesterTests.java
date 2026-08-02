package org.mingharness.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mingharness.model.api.ModelConnectionTestView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 连接探测必须快速返回，不能因为不可响应的用户模型地址长期占住前端请求。 */
@SpringBootTest
@TestPropertySource(properties = "harness.runtime.model-timeout-ms=1000")
class ModelConnectionTesterTests {

    @Autowired
    private ModelConnectionTester tester;

    private HttpServer slowServer;

    @BeforeEach
    void startSlowServer() throws IOException {
        slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowServer.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(3_000);
                exchange.sendResponseHeaders(200, 0);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        slowServer.start();
    }

    @AfterEach
    void stopSlowServer() {
        if (slowServer != null) slowServer.stop(0);
    }

    @Test
    void shouldReturnTimeoutForUnresponsiveProviderWithoutLeakingDetails() {
        long startedAt = System.nanoTime();
        ModelConnectionTestView result = tester.test(new ModelProviderConfigService.ResolvedModelConfig(
                true, "http://127.0.0.1:" + slowServer.getAddress().getPort() + "/v1",
                "test-secret", "slow-model", "preview"), "tenant-model", "operator");
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertFalse(result.success());
        assertEquals("TIMEOUT", result.status());
        assertTrue(result.message().contains("超时"));
        assertFalse(result.message().contains("127.0.0.1"));
        assertFalse(result.message().contains("test-secret"));
        assertTrue(elapsedMs < 2_500, "连接测试应使用快速超时边界，而非等待供应商完整响应");
    }
}

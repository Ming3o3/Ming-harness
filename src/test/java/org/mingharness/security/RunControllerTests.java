package org.mingharness.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.RunController;
import org.mingharness.runtime.application.RunService;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 验证受信认证模式不会接受请求方伪造的权限快照。 */
class RunControllerTests {

    @AfterEach
    void clearIdentity() {
        HarnessIdentityContext.clear();
    }

    @Test
    void oidcShouldKeepJwtPermissionsWhenCreatingRun() {
        RunService runService = mock(RunService.class);
        RunController controller = new RunController(runService);
        HarnessIdentityContext.set(new HarnessIdentity(
                "tenant-oidc", "oidc-user", Set.of("run.create"), "oidc"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Permissions", "run.execute");

        controller.create(new CreateRunRequest(
                "tenant-oidc", "oidc-user", "测试任务", "测试输入", "demo.echo",
                null, "prompt-v1", "policy-v1", BigDecimal.ONE,
                null, "run.execute"), request);

        ArgumentCaptor<CreateRunRequest> captured = ArgumentCaptor.forClass(CreateRunRequest.class);
        verify(runService).create(captured.capture());
        assertEquals("run.create", captured.getValue().permissions());
    }
}

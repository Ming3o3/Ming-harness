package org.mingharness.runtime.application;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 为模型和工具调用提供统一的超时边界，避免外部依赖卡住 Run。 */
@Component
public class BoundedExecutor {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public <T> T execute(String operation, int timeoutMs, Callable<T> action) {
        Future<T> future = executor.submit(action);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ExecutionTimeoutException(operation + "执行超时（" + timeoutMs + "ms）");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ExecutionTimeoutException(operation + "被中断");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause == null ? operation + "执行失败" : cause.getMessage(), cause);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}

package org.mingharness.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SensitiveDataSanitizer sanitizer;

    public GlobalExceptionHandler(SensitiveDataSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorResponse response = new ErrorResponse(
                exception.getCode(),
                sanitizer.sanitize(exception.getMessage()),
                Map.of(),
                Instant.now()
        );
        return jsonResponse(exception.getStatus(), response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), sanitizer.sanitize(error.getDefaultMessage()));
        }
        ErrorResponse response = new ErrorResponse(
                "VALIDATION_FAILED",
                "请求参数校验失败",
                details,
                Instant.now()
        );
        return jsonResponse(HttpStatus.BAD_REQUEST, response);
    }

    /** 并发修改带版本字段的运行或组织策略时，要求客户端重新读取后再提交，不能伪装成服务故障。 */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException exception) {
        ErrorResponse response = new ErrorResponse(
                "CONCURRENT_UPDATE",
                "数据已被其他请求更新，请刷新后重试",
                Map.of(),
                Instant.now()
        );
        return jsonResponse(HttpStatus.CONFLICT, response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        ErrorResponse response = new ErrorResponse(
                "UNSUPPORTED_MEDIA_TYPE",
                "请求内容类型不支持，请使用 application/json",
                Map.of(),
                Instant.now()
        );
        return jsonResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        ErrorResponse response = new ErrorResponse(
                "DOCUMENT_FILE_TOO_LARGE",
                "上传文件超过服务允许的大小限制",
                Map.of(),
                Instant.now()
        );
        return jsonResponse(HttpStatus.PAYLOAD_TOO_LARGE, response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        String traceId = HarnessRequestContext.currentTraceId();
        log.error("未处理的请求异常，traceId={}, exceptionType={}, message={}",
                traceId,
                exception.getClass().getName(),
                sanitizer.sanitize(exception.getMessage()),
                exception);
        ErrorResponse response = new ErrorResponse(
                "INTERNAL_ERROR",
                "服务暂时不可用，请稍后重试",
                Map.of(),
                Instant.now()
        );
        return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, response);
    }

    /** SSE 客户端通常只声明 text/event-stream；失败时仍返回可解析的既有 JSON 错误结构。 */
    private ResponseEntity<ErrorResponse> jsonResponse(HttpStatus status, ErrorResponse response) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(response);
    }
}

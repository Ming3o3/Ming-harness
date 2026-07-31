package org.mingharness.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
        return ResponseEntity.status(exception.getStatus()).body(response);
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
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        ErrorResponse response = new ErrorResponse(
                "INTERNAL_ERROR",
                "服务暂时不可用，请稍后重试",
                Map.of(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

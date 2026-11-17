package com.stockinsight.api;

import com.stockinsight.provider.FinancialDataProvider;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import static com.stockinsight.api.ApiModels.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final FinancialDataProvider provider;

    public ApiExceptionHandler(FinancialDataProvider provider) { this.provider = provider; }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Envelope<Void>> domain(ApiException ex) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Envelope<Void>> validation(ConstraintViolationException ex) {
        var fields = ex.getConstraintViolations().stream().map(v -> {
            String path = v.getPropertyPath().toString();
            return new FieldError(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
        }).sorted(java.util.Comparator.comparing(FieldError::field)).toList();
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "입력값을 확인해 주세요.", fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Envelope<Void>> missing(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "필수 요청값이 없습니다.",
            List.of(new FieldError(ex.getParameterName(), "필수 항목입니다.")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Envelope<Void>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청값 형식이 올바르지 않습니다.",
            List.of(new FieldError(ex.getName(), "요청 형식을 확인해 주세요.")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Envelope<Void>> noRoute(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 경로가 없습니다.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Envelope<Void>> method(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Envelope<Void>> unexpected(Exception ex) {
        // 응답에는 내부 예외·DB 접속 정보가 노출되지 않도록 고정 메시지를 사용합니다.
        log.error("Unhandled API exception: {}", ex.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버에서 오류가 발생했습니다.", List.of());
    }

    private ResponseEntity<Envelope<Void>> error(HttpStatus status, String code, String message, List<FieldError> fields) {
        return ResponseEntity.status(status).body(new Envelope<>(null,
            new Meta(provider.mode(), List.of(), null, List.of(), null, false),
            new ErrorDetail(code, message, fields)));
    }
}

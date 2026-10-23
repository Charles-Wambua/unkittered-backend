package com.unkittered.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(Instant timestamp, int status, String error, String message) {
        static ErrorResponse of(HttpStatus s, String msg) {
            return new ErrorResponse(Instant.now(), s.value(), s.getReasonPhrase(), msg);
        }
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, msg));
    }

    /**
     * Unreadable bodies (bad JSON, invalid UTF-8, wrong types) are the
     * caller's fault: 400, not 500. Enum parsers throw {@link ApiException}
     * from inside Jackson, so surface that message when present.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ApiException api) return handleApi(api);
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Malformed request body"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        // Framework errors that already know their status (405, 415, missing
        // parameter…) keep it.
        if (ex instanceof org.springframework.web.ErrorResponse framework) {
            HttpStatusCode code = framework.getStatusCode();
            HttpStatus status = HttpStatus.valueOf(code.value());
            if (status.is4xxClientError()) {
                return ResponseEntity.status(status).body(ErrorResponse.of(status, status.getReasonPhrase()));
            }
        }
        // Unexpected: the client only sees a generic message, so the details
        // must reach the logs.
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"));
    }

    private String fieldMessage(FieldError fe) {
        return fe.getField() + " " + fe.getDefaultMessage();
    }
}

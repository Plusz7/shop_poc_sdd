package com.project.custom.shared.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.beans.TypeMismatchException;
import tools.jackson.core.JacksonException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps every error to RFC 9457 {@code application/problem+json} with the contract fields {@code code}
 * and {@code errors[] = {field, message}}. Responses never contain secrets or personal data.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final UiMessages uiMessages;

    GlobalExceptionHandler(UiMessages uiMessages) {
        this.uiMessages = uiMessages;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        ProblemDetail problem = problem(exception.status(), exception.code(),
                uiMessages.get(exception.messageKey(), exception.messageArgs()));
        exception.properties().forEach(problem::setProperty);
        return ResponseEntity.status(exception.status()).body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrencyConflict(OptimisticLockingFailureException exception) {
        log.info("Concurrent modification rejected: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                problem(HttpStatus.CONFLICT, ErrorCode.CONCURRENCY_CONFLICT, uiMessages.get("error.concurrency-conflict")));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception) {
        List<Map<String, String>> errors = exception.getConstraintViolations().stream()
                .map(violation -> fieldError(lastNode(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected error while handling a request", exception);
        return ResponseEntity.internalServerError().body(
                problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, uiMessages.get("error.internal")));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.add(fieldError(error.getField(), error.getDefaultMessage()));
        }
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.add(fieldError(error.getObjectName(), error.getDefaultMessage())));
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                errors.add(fieldError(result.getMethodParameter().getParameterName(), error.getDefaultMessage()))));
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String field = exception.getCause() instanceof JacksonException jackson ? jsonPath(jackson) : "body";
        return ResponseEntity.badRequest().body(validationProblem(
                List.of(fieldError(field.isEmpty() ? "body" : field, uiMessages.get("validation.invalid-value")))));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String field = exception.getPropertyName() == null ? "parameter" : exception.getPropertyName();
        return ResponseEntity.badRequest().body(validationProblem(
                List.of(fieldError(field, uiMessages.get("validation.invalid-value")))));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException exception,
                                                                    HttpHeaders headers, HttpStatusCode status,
                                                                    WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, uiMessages.get("error.not-found")));
    }

    /** Every other framework-produced problem also carries a contract {@code code}. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
                                                             HttpHeaders headers, HttpStatusCode statusCode,
                                                             WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            problem.setProperty("code", defaultCode(statusCode).name());
        }
        return response;
    }

    private ProblemDetail validationProblem(List<Map<String, String>> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                uiMessages.get("error.validation"));
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatusCode status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return problem;
    }

    private Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message == null ? uiMessages.get("validation.invalid-value") : message);
    }

    private static ErrorCode defaultCode(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.NOT_FOUND;
        }
        return status.is4xxClientError() ? ErrorCode.VALIDATION_ERROR : ErrorCode.INTERNAL_ERROR;
    }

    private static String jsonPath(JacksonException exception) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : exception.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }
}

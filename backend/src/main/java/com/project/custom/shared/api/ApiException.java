package com.project.custom.shared.api;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown by a controller (after mapping a domain outcome) to produce an RFC 9457 problem response with
 * a contract error code. The customer-facing {@code detail} is resolved from the UI copy by
 * {@code messageKey}; {@code properties} become extra problem fields (e.g. {@code maxQuantity}).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final String messageKey;
    private final Object[] messageArgs;
    private final Map<String, Object> properties;

    public ApiException(HttpStatus status, ErrorCode code, String messageKey, Object... messageArgs) {
        this(status, code, messageKey, messageArgs, Map.of());
    }

    private ApiException(HttpStatus status, ErrorCode code, String messageKey, Object[] messageArgs,
                         Map<String, Object> properties) {
        super(code.name());
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
        this.properties = properties;
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "error.not-found");
    }

    public ApiException withProperty(String name, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(properties);
        copy.put(name, value);
        return new ApiException(status, code, messageKey, messageArgs, copy);
    }

    HttpStatus status() {
        return status;
    }

    ErrorCode code() {
        return code;
    }

    String messageKey() {
        return messageKey;
    }

    Object[] messageArgs() {
        return messageArgs;
    }

    Map<String, Object> properties() {
        return properties;
    }
}

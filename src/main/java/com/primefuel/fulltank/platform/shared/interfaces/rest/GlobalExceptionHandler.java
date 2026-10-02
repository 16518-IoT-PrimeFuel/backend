package com.primefuel.fulltank.platform.shared.interfaces.rest;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ErrorResponseAssembler;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

@RestControllerAdvice
@NullMarked
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MESSAGES_BASENAME = "messages";

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class
    })
    public ResponseEntity<?> handleProviderParameterValidation(
            Exception exception, HttpServletRequest request) {
        if (!isProviderRoute(request.getRequestURI())) return unexpected(exception);
        if (exception instanceof HandlerMethodValidationException validation
                && validation.isForReturnValue()) return unexpected(exception);
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.validationError(
                        "request-parameters",
                        "Use positive identifiers and valid ISO dates or instants"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleProviderConflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        if (!isProviderRoute(request.getRequestURI())) return unexpected(exception);
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.conflict(
                        "ProviderResource", "A conflicting association already exists"));
    }

    private static boolean isProviderRoute(String uri) {
        return uri.startsWith("/api/provider/")
                || uri.startsWith("/api/analytics/providers/")
                || uri.equals("/api/deliveries")
                || uri.equals("/api/deliveries/recommendation")
                || uri.matches("/api/deliveries/[^/]+/valve-observations");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException ex) {
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.forbidden(
                        ex.getMessage() != null ? ex.getMessage() : "Access denied"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.validationError(
                        "request", ex.getReason() != null ? ex.getReason() : "Invalid request"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors();
        var validationPrefix = resolveMessageOrDefault("validation.field.prefix", "Field");
        var errorDetails =
                fieldErrors.isEmpty()
                        ? resolveMessageOrDefault(
                                "validation.request.failed", "Request validation failed")
                        : fieldErrors.stream()
                                .map(
                                        error ->
                                                "%s %s: %s"
                                                        .formatted(
                                                                validationPrefix,
                                                                error.getField(),
                                                                error.getDefaultMessage()))
                                .reduce((a, b) -> a + "; " + b)
                                .orElse(
                                        resolveMessageOrDefault(
                                                "validation.request.failed",
                                                "Request validation failed"));

        var applicationError = ApplicationError.validationError("request-body", errorDetails);
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.validationError(
                        "request-body", "Malformed or unreadable JSON request body"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        var applicationError =
                ApplicationError.validationError(
                        resolveMessageOrDefault("validation.request.argument", "request-argument"),
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : resolveMessageOrDefault(
                                        "validation.request.failed", "Request validation failed"));
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        return unexpected(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        return unexpected(ex);
    }

    /** Internal messages (class names, parser details) stay in the log, never in the response. */
    private ResponseEntity<?> unexpected(Exception ex) {
        LOG.error("Unhandled exception", ex);
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.unexpected(
                        resolveMessageOrDefault(
                                "error.unexpected.context", "global-exception-handler"),
                        "An unexpected error occurred"));
    }

    private String resolveMessageOrDefault(String key, String defaultValue, Object... args) {
        try {
            var bundle =
                    ResourceBundle.getBundle(MESSAGES_BASENAME, LocaleContextHolder.getLocale());
            if (!bundle.containsKey(key)) {
                return defaultValue;
            }
            return MessageFormat.format(bundle.getString(key), args);
        } catch (MissingResourceException ex) {
            return defaultValue;
        }
    }
}

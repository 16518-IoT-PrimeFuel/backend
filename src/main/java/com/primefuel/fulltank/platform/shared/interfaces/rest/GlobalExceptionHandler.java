package com.primefuel.fulltank.platform.shared.interfaces.rest;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ErrorResponseAssembler;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import com.primefuel.fulltank.platform.shared.interfaces.rest.resources.ErrorResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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
        MissingServletRequestParameterException.class,
        HandlerMethodValidationException.class
    })
    public ResponseEntity<?> handleParameterValidation(Exception exception) {
        if (exception instanceof HandlerMethodValidationException validation
                && validation.isForReturnValue()) return unexpected(exception);
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.validationError(
                        "request-parameters",
                        "Use positive identifiers and valid ISO dates or instants"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        LOG.warn("Data integrity violation: {}", exception.getMostSpecificCause().getMessage());
        var resource = isProviderRoute(request.getRequestURI()) ? "ProviderResource" : "Resource";
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.conflict(resource, "A conflicting resource already exists"));
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<?> handleOptimisticLock(org.springframework.dao.OptimisticLockingFailureException exception) {
        LOG.warn("Optimistic lock conflict: {}", exception.getMessage());
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.conflict("Resource", "The resource was modified concurrently; retry"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResource> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(ex.getHeaders())
                .body(new ErrorResource("METHOD_NOT_ALLOWED", "HTTP method not supported for this resource"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResource> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(ex.getHeaders())
                .body(new ErrorResource("UNSUPPORTED_MEDIA_TYPE", "Content type not supported"));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<?> handleNoResource(Exception ex) {
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.notFound("resource", "route"));
    }

    private static boolean isProviderRoute(String uri) {
        return uri.startsWith("/api/provider/")
                || uri.startsWith("/api/payments/provider/")
                || uri.startsWith("/api/analytics/providers/")
                || uri.equals("/api/deliveries")
                || uri.equals("/api/deliveries/recommendation")
                || uri.matches("/api/deliveries/[^/]+/valve-observations");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResource> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(new ErrorResource("RATE_LIMITED",
                        "Too many attempts. Try again in " + ex.getRetryAfterSeconds() + " seconds."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException ex) {
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                ApplicationError.forbidden(
                        ex.getMessage() != null ? ex.getMessage() : "Access denied"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
        var reason = ex.getReason();
        var error = switch (ex.getStatusCode().value()) {
            case 404 -> ApplicationError.notFound("resource", reason != null ? reason : "requested");
            case 403 -> ApplicationError.forbidden(reason != null ? reason : "Access denied");
            case 409 -> ApplicationError.conflict("resource", reason != null ? reason : "Conflict");
            default -> ApplicationError.validationError(
                    "request", reason != null ? reason : "Invalid request");
        };
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(error);
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

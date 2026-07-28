package com.ticketflow1.ticketing.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Turns exceptions into the standard error body (contracts/README.md).
 *
 * 401 (missing/invalid token) is handled by RestAuthenticationEntryPoint —
 * that denial happens in the filter chain, before a controller runs, so it
 * never reaches this class.
 *
 * 403 from @PreAuthorize is NOT filter-chain-level, though: the method
 * security interceptor is AOP woven around the controller method itself, so
 * denial throws AuthorizationDeniedException from inside the very call
 * DispatcherServlet made to invoke the handler — Spring MVC's own exception
 * resolution (i.e. this class) catches it first. RestAccessDeniedHandler is
 * effectively dead code for method-security denials; handled explicitly here
 * instead of relying on it.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("api_exception method={} path={} status={} code={} message={}",
                request.getMethod(), request.getRequestURI(), ex.getStatus().value(), ex.getErrorCode(), ex.getMessage());
        if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
            securityAlert("LOGIN_RATE_LIMITED", request, ex.getStatus());
        } else if (ex.getStatus() == HttpStatus.FORBIDDEN) {
            securityAlert("ACCESS_DENIED", request, ex.getStatus());
        } else if (ex.getStatus() == HttpStatus.UNAUTHORIZED
                && "/api/auth/login".equals(request.getRequestURI())) {
            securityAlert("LOGIN_FAILURE", request, ex.getStatus());
        }
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiError.of(ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.warn("api_validation_failed method={} path={} fields={}",
                request.getMethod(), request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED",
                "Request body failed validation.", request.getRequestURI(), fieldErrors));
    }

    /**
     * A query/path param that can't be converted to its declared type (e.g.
     * ?slaStatus=WRONG against an enum) is the CALLER's error, not ours —
     * without this handler it fell through to handleUnexpected as a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String message = "Invalid value '%s' for parameter '%s'.".formatted(ex.getValue(), ex.getName());
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED", message,
                request.getRequestURI()));
    }

    /** Disabled documentation/static paths must be a normal 404, never a 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingResource(NoResourceFoundException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Resource not found.", request.getRequestURI()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> handleAuthorizationDenied(AuthorizationDeniedException ex,
            HttpServletRequest request) {
        log.warn("api_forbidden method={} path={} message={}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                HttpStatus.FORBIDDEN.value(), "FORBIDDEN",
                "You do not have permission to perform this action.", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // The client gets a generic body, but the real stack trace must land in
        // the log — a swallowed 500 is undebuggable.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        securityAlert("SERVER_ERROR", request, HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "An unexpected error occurred.", request.getRequestURI()));
    }

    private void securityAlert(String event, HttpServletRequest request, HttpStatus status) {
        // Deliberately excludes request parameters, credentials, IP addresses,
        // and user identifiers; Cloud Logging can alert on this stable marker.
        log.warn("security_alert event={} method={} path={} status={}",
                event, request.getMethod(), request.getRequestURI(), status.value());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticConflict(ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, "CONFLICT",
                "The resource was changed by another request.", request.getRequestURI()));
    }
}

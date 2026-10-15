package com.elderhive.exceptions;

import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.payload.ResponseModel;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mirrors ~/ElderSphere/eldersphere-be's GlobalExceptionHandler. The four bucket sets below
 * only list codes that exist in enums.ExceptionCodeEnum today (see its own additive-only
 * comment) — as later phases add domain-specific codes (DUPLICATE_EMAIL, ROLE_NOT_FOUND,
 * etc.), add them to the matching bucket here too, never by renumbering/removing anything.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<ExceptionCodeEnum> UNAUTHORIZED_CODES = Set.of(
            ExceptionCodeEnum.UNAUTHORIZED,
            ExceptionCodeEnum.INVALID_CREDENTIALS,
            ExceptionCodeEnum.TOKEN_EXPIRED,
            ExceptionCodeEnum.TOKEN_INVALID,
            ExceptionCodeEnum.ACCOUNT_NOT_ACTIVE
    );

    private static final Set<ExceptionCodeEnum> BAD_REQUEST_CODES = Set.of(
            ExceptionCodeEnum.BAD_REQUEST,
            ExceptionCodeEnum.INVALID_ARGUMENT,
            ExceptionCodeEnum.VALIDATION_FAILED,
            ExceptionCodeEnum.FORMAT_ERROR,
            ExceptionCodeEnum.PASSWORD_TOO_WEAK,
            ExceptionCodeEnum.INVALID_ROLE
    );

    private static final Set<ExceptionCodeEnum> CONFLICT_CODES = Set.of(
            ExceptionCodeEnum.CONFLICT,
            ExceptionCodeEnum.DUPLICATE_EMAIL,
            ExceptionCodeEnum.COLOR_THEME_ALREADY_EXISTS
    );

    private static final Set<ExceptionCodeEnum> NOT_FOUND_CODES = Set.of(
            ExceptionCodeEnum.USER_NOT_FOUND,
            ExceptionCodeEnum.DATA_NOT_FOUND,
            ExceptionCodeEnum.COLOR_THEME_NOT_FOUND
    );

    @ExceptionHandler(GenericException.class)
    public ResponseEntity<ResponseModel<Void>> handleGenericException(GenericException ex) {
        ExceptionCodeEnum code = ex.getExceptionCode();
        String message = ex.getErrorMessage() != null ? ex.getErrorMessage() : "An error occurred";
        log.warn("GenericException [{}]: {}", code, message);

        HttpStatus status = resolveStatus(code);
        ResponseModel<Void> body = new ResponseModel<>(message, null, code != null ? code.getValue() : "ERROR");
        body.toFailure();
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ResponseModel<Void>> handleInvalidDataAccess(InvalidDataAccessApiUsageException ex) {
        log.warn("InvalidDataAccessApiUsageException: {}", ex.getMessage());
        ResponseModel<Void> body = new ResponseModel<>("Invalid query parameter. Check your sort or filter values.", null, ExceptionCodeEnum.BAD_REQUEST.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ResponseModel<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        String message = "Authorization header is required";
        log.warn("MissingRequestHeaderException: {}", ex.getHeaderName());
        ResponseModel<Void> body = new ResponseModel<>(message, null, ExceptionCodeEnum.UNAUTHORIZED.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseModel<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("MissingServletRequestParameterException: {}", ex.getMessage());
        String message = "Required query parameter '" + ex.getParameterName() + "' is missing.";
        ResponseModel<Void> body = new ResponseModel<>(message, null, ExceptionCodeEnum.BAD_REQUEST.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidDataAccessResourceUsageException.class)
    public ResponseEntity<ResponseModel<Void>> handleInvalidDataAccessResource(InvalidDataAccessResourceUsageException ex) {
        log.warn("InvalidDataAccessResourceUsageException: {}", ex.getMessage());
        ResponseModel<Void> body = new ResponseModel<>("Invalid query. Check your search or filter values.", null, ExceptionCodeEnum.BAD_REQUEST.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseModel<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        String message = "Validation failed: " + String.join(", ", errors);
        log.warn("ValidationException: {}", message);

        ResponseModel<Void> body = new ResponseModel<>(message, null, ExceptionCodeEnum.VALIDATION_FAILED.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseModel<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("ConstraintViolationException: {}", message);
        ResponseModel<Void> body = new ResponseModel<>("Validation failed: " + message, null, ExceptionCodeEnum.VALIDATION_FAILED.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseModel<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        log.warn("MethodArgumentTypeMismatchException: {}", message);
        ResponseModel<Void> body = new ResponseModel<>(message, null, ExceptionCodeEnum.BAD_REQUEST.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseModel<Void>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadableException: {}", ex.getMessage());
        ResponseModel<Void> body = new ResponseModel<>("Malformed JSON request body", null, ExceptionCodeEnum.BAD_REQUEST.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseModel<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("HttpRequestMethodNotSupportedException: {}", ex.getMethod());
        ResponseModel<Void> body = new ResponseModel<>("HTTP method " + ex.getMethod() + " is not supported for this endpoint", null, ExceptionCodeEnum.BAD_REQUEST.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResponseModel<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("NoResourceFoundException: {}", ex.getMessage());
        ResponseModel<Void> body = new ResponseModel<>("The requested resource was not found.", null,
                ExceptionCodeEnum.DATA_NOT_FOUND.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseModel<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("AccessDeniedException: {}", ex.getMessage());
        ResponseModel<Void> body = new ResponseModel<>("You do not have permission to perform this action.", null,
                ExceptionCodeEnum.FORBIDDEN.getValue());
        body.toFailure();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseModel<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ResponseModel<Void> body = new ResponseModel<>("An unexpected error occurred. Please try again.", null,
                ExceptionCodeEnum.INTERNAL_SERVER_ERROR.getValue());
        body.toInternalServerError();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private HttpStatus resolveStatus(ExceptionCodeEnum code) {
        if (code == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        if (UNAUTHORIZED_CODES.contains(code)) return HttpStatus.UNAUTHORIZED;
        if (code == ExceptionCodeEnum.FORBIDDEN) return HttpStatus.FORBIDDEN;
        if (NOT_FOUND_CODES.contains(code)) return HttpStatus.NOT_FOUND;
        if (BAD_REQUEST_CODES.contains(code)) return HttpStatus.BAD_REQUEST;
        if (CONFLICT_CODES.contains(code)) return HttpStatus.CONFLICT;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

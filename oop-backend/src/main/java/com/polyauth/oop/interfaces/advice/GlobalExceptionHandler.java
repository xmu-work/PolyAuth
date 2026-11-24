package com.polyauth.oop.interfaces.advice;

import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.domain.exception.DomainErrorCode;
import com.polyauth.oop.domain.exception.DomainRuleException;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.common.IdentityErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * OOP 后端统一异常出口，保持与结构化后端相同的状态码和业务码。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> IDENTITY_PATH_PREFIXES = Set.of(
            "/auth/login", "/auth/logout", "/auth/me", "/auth/permissions/",
            "/auth/departments", "/auth/api-permissions/", "/auth/users/",
            "/auth/audit-logs");

    /**
     * 转换应用用例异常。
     */
    @ExceptionHandler(UseCaseException.class)
    public ResponseEntity<?> handleUseCase(UseCaseException exception,
                                           HttpServletRequest request) {
        ApplicationErrorCode code = exception.errorCode();
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(code.httpStatus())
                    .body(new IdentityErrorResponse(
                            identityCode(code, request),
                            identityMessage(code, exception.getMessage(), request)));
        }
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResult.fail(code.code(), exception.getMessage()));
    }

    /**
     * 转换领域不变量异常。
     */
    @ExceptionHandler(DomainRuleException.class)
    public ResponseEntity<?> handleDomain(DomainRuleException exception,
                                          HttpServletRequest request) {
        int status = switch (exception.errorCode()) {
            case ROLE_INHERIT_CYCLE, ORGANIZATION_INVALID_MOVE,
                    USER_ALREADY_SEALED, ROLE_EXCLUSION_CONFLICT -> 409;
            default -> 400;
        };
        int code = switch (exception.errorCode()) {
            case ROLE_INHERIT_CYCLE -> 3002;
            case ORGANIZATION_INVALID_MOVE -> 3003;
            case USER_ALREADY_SEALED -> 3004;
            case ROLE_EXCLUSION_CONFLICT -> 3011;
            default -> 1001;
        };
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(status)
                    .body(new IdentityErrorResponse("BAD_REQUEST", exception.getMessage()));
        }
        return ResponseEntity.status(status).body(ApiResult.fail(code, exception.getMessage()));
    }

    /**
     * 转换请求体校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleBodyValidation(MethodArgumentNotValidException exception,
                                                   HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return badRequest(message, request);
    }

    /**
     * 转换参数绑定校验异常。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<?> handleBind(BindException exception,
                                        HttpServletRequest request) {
        String message = exception.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return badRequest(message, request);
    }

    /**
     * 转换方法参数约束异常。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraint(ConstraintViolationException exception,
                                              HttpServletRequest request) {
        String message = exception.getConstraintViolations().stream()
                .map(item -> item.getPropertyPath() + ": " + item.getMessage())
                .collect(Collectors.joining("; "));
        return badRequest(message, request);
    }

    /**
     * 转换 Spring MVC 方法参数校验异常。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        String message = exception.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(item -> item != null && !item.isBlank())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = exception.getMessage();
        }
        return badRequest(message, request);
    }

    /**
     * 转换常见客户端参数错误。
     */
    @ExceptionHandler({
            IllegalArgumentException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    public ResponseEntity<?> handleBadRequest(Exception exception,
                                              HttpServletRequest request) {
        return badRequest(exception.getMessage(), request);
    }

    /**
     * 将唯一约束冲突转换为稳定 409。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleIntegrity(DataIntegrityViolationException exception,
                                             HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new IdentityErrorResponse("CONFLICT", "数据已存在或违反唯一约束"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResult.fail(3009, "数据已存在或违反唯一约束"));
    }

    /**
     * 将乐观锁冲突转换为稳定 409。
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<?> handleOptimisticLock(OptimisticLockingFailureException exception,
                                                  HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new IdentityErrorResponse("CONFLICT", "数据已被他人修改，请刷新后重试"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResult.fail(ApplicationErrorCode.LOCK_ACQUIRE_FAILED.code(),
                        "数据已被他人修改，请刷新后重试"));
    }

    /**
     * 转换不存在路由。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNotFound(NoResourceFoundException exception,
                                            HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new IdentityErrorResponse("NOT_FOUND", "资源不存在"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResult.fail(ApplicationErrorCode.RESOURCE_NOT_FOUND.code(), "资源不存在"));
    }

    /**
     * 将路径存在但 HTTP 方法不匹配的请求稳定翻译为 405，避免落入 500。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(new IdentityErrorResponse("METHOD_NOT_ALLOWED", "HTTP 方法不允许"));
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResult.fail(ApplicationErrorCode.METHOD_NOT_ALLOWED.code(),
                        "HTTP 方法不允许"));
    }

    /**
     * 隐藏未分类内部错误细节。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleInternal(Exception exception,
                                            HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new IdentityErrorResponse("INTERNAL_ERROR", "服务器内部错误"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(ApplicationErrorCode.INTERNAL_ERROR.code(),
                        "服务器内部错误"));
    }

    private ResponseEntity<?> badRequest(String message, HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.badRequest()
                    .body(new IdentityErrorResponse("BAD_REQUEST", message));
        }
        return ResponseEntity.badRequest().body(ApiResult.fail(1001, message));
    }

    private boolean isIdentityRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        int identityIndex = uri.indexOf("/auth/");
        if (identityIndex < 0) {
            return false;
        }
        String path = uri.substring(identityIndex);
        return IDENTITY_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String identityCode(ApplicationErrorCode code, HttpServletRequest request) {
        if (code == ApplicationErrorCode.RESOURCE_NOT_FOUND) {
            String path = identityPath(request);
            if (path.startsWith("/auth/api-permissions/")) {
                return "API_OPERATION_NOT_FOUND";
            }
            if (path.startsWith("/auth/departments")) {
                return "IDENTITY_DEPARTMENT_NOT_FOUND";
            }
            if (path.startsWith("/auth/users/")) {
                return "IDENTITY_USER_NOT_FOUND";
            }
        }
        return switch (code) {
            case UNAUTHENTICATED, TOKEN_INVALID, TOKEN_EXPIRED, TOKEN_REVOKED -> "UNAUTHORIZED";
            case ACCOUNT_SEALED, ACCOUNT_DISABLED, NO_FUNCTION_PERM -> "FORBIDDEN";
            case RESOURCE_NOT_FOUND -> "NOT_FOUND";
            case PARAM_INVALID -> "BAD_REQUEST";
            case SERVICE_UNAVAILABLE -> "IDENTITY_SERVICE_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
    }

    private String identityPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int identityIndex = uri == null ? -1 : uri.indexOf("/auth/");
        return identityIndex < 0 ? "" : uri.substring(identityIndex);
    }

    private String identityMessage(ApplicationErrorCode code, String fallback,
                                   HttpServletRequest request) {
        if (code == ApplicationErrorCode.UNAUTHENTICATED
                && identityPath(request).startsWith("/auth/login")) {
            return (fallback == null || fallback.isBlank()) ? "账号或密码错误。" : fallback;
        }
        return switch (code) {
            case UNAUTHENTICATED, TOKEN_INVALID, TOKEN_EXPIRED, TOKEN_REVOKED -> "请先登录";
            case SERVICE_UNAVAILABLE -> "统一身份认证暂时不可用，请稍后重试。";
            default -> fallback;
        };
    }
}

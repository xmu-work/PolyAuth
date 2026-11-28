package com.polyauth.api.shared.error;

import com.polyauth.service.common.enums.ErrorCode;
import com.polyauth.service.common.exception.BusinessException;
import com.polyauth.api.shared.Result;
import com.polyauth.api.identity.vo.IdentityErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;
import java.util.Set;

/**
 * 全局异常处理器。
 *
 * <p>结构化编程原则：统一出口。所有 Controller 抛出的异常在此集中处理，
 * 转换为统一响应包 Result，避免业务代码中散落的 try-catch。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> IDENTITY_PATH_PREFIXES = Set.of(
            "/auth/login", "/auth/logout", "/auth/me", "/auth/permissions/",
            "/auth/departments", "/auth/api-permissions/", "/auth/users/",
            "/auth/audit-logs");

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode ec = matchByCode(ex.getCode());
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(ec.getHttpStatus())
                    .body(new IdentityErrorResponse(
                            identityCode(ec, request), identityMessage(ec, ex.getMessage(), request)));
        }
        return ResponseEntity.status(ec.getHttpStatus()).body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * DDD 应用用例异常：沿用同一组外部错误码与 HTTP 状态。
     */
    @ExceptionHandler(com.polyauth.service.ddd.application.exception.UseCaseException.class)
    public ResponseEntity<?> handleUseCase(
            com.polyauth.service.ddd.application.exception.UseCaseException ex,
            HttpServletRequest request) {
        var code = ex.errorCode();
        if (isIdentityRequest(request)) {
            String message = code
                    == com.polyauth.service.ddd.application.exception.ApplicationErrorCode.SERVICE_UNAVAILABLE
                    ? "统一身份认证暂时不可用，请稍后重试。"
                    : ex.getMessage();
            return ResponseEntity.status(code.httpStatus())
                    .body(new IdentityErrorResponse(identityUseCaseCode(code, request), message));
        }
        return ResponseEntity.status(code.httpStatus())
                .body(Result.fail(code.code(), ex.getMessage()));
    }

    private String identityUseCaseCode(
            com.polyauth.service.ddd.application.exception.ApplicationErrorCode code,
            HttpServletRequest request) {
        if (code == com.polyauth.service.ddd.application.exception.ApplicationErrorCode.RESOURCE_NOT_FOUND
                && request.getRequestURI() != null
                && request.getRequestURI().contains("/auth/api-permissions/")) {
            return "API_OPERATION_NOT_FOUND";
        }
        return switch (code) {
            case PARAM_INVALID -> "BAD_REQUEST";
            case RESOURCE_NOT_FOUND -> "NOT_FOUND";
            case UNAUTHENTICATED, TOKEN_INVALID, TOKEN_EXPIRED, TOKEN_REVOKED,
                 SIGNATURE_INVALID, REPLAY_DETECTED -> "UNAUTHORIZED";
            case ACCOUNT_SEALED, ACCOUNT_DISABLED, NO_FUNCTION_PERM, APP_DISABLED -> "FORBIDDEN";
            case SERVICE_UNAVAILABLE -> "IDENTITY_SERVICE_UNAVAILABLE";
            default -> "CONFLICT";
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (isIdentityRequest(request)) {
            return ResponseEntity.badRequest().body(new IdentityErrorResponse("BAD_REQUEST", msg));
        }
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.PARAM_INVALID, msg));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<?> handleBind(BindException ex, HttpServletRequest request) {
        String msg = ex.getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (isIdentityRequest(request)) {
            return ResponseEntity.badRequest().body(new IdentityErrorResponse("BAD_REQUEST", msg));
        }
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.PARAM_INVALID, msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        if (isIdentityRequest(request)) {
            return ResponseEntity.badRequest().body(new IdentityErrorResponse("BAD_REQUEST", msg));
        }
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.PARAM_INVALID, msg));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        String msg = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(item -> item != null && !item.isBlank())
                .collect(Collectors.joining("; "));
        if (msg.isBlank()) {
            msg = ex.getMessage();
        }
        if (isIdentityRequest(request)) {
            return ResponseEntity.badRequest().body(new IdentityErrorResponse("BAD_REQUEST", msg));
        }
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.PARAM_INVALID, msg));
    }

    /** 枚举取值非法、缺少请求参数/请求头、参数类型不匹配、请求体无法解析，均为客户端错误 */
    @ExceptionHandler({IllegalArgumentException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<?> handleBadRequest(Exception ex, HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.badRequest()
                    .body(new IdentityErrorResponse("BAD_REQUEST", ex.getMessage()));
        }
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.PARAM_INVALID, ex.getMessage()));
    }

    /** 乐观锁并发冲突（实体 @Version 校验失败）→ 409/3008，前端提示“数据已被他人修改，请刷新后重试” */
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<?> handleOptimisticLock(
            org.springframework.dao.OptimisticLockingFailureException ex,
            HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new IdentityErrorResponse("CONFLICT", "数据已被他人修改，请刷新后重试"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.fail(ErrorCode.LOCK_ACQUIRE_FAILED, "数据已被他人修改，请刷新后重试"));
    }

    /** 唯一约束/完整性冲突（先查后插竞态）→ 409/3009，不再落到 500 */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(
            org.springframework.dao.DataIntegrityViolationException ex,
            HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new IdentityErrorResponse("CONFLICT", "数据已存在或违反唯一约束"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.fail(ErrorCode.RESOURCE_DUPLICATE, "数据已存在或违反唯一约束"));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<?> handleNotFound(Exception ex, HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new IdentityErrorResponse("NOT_FOUND", "资源不存在"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 将路径存在但 HTTP 方法不匹配的请求稳定翻译为 405，避免落入 500。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(new IdentityErrorResponse("METHOD_NOT_ALLOWED", "HTTP 方法不允许"));
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleInternal(Exception ex, HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new IdentityErrorResponse("INTERNAL_ERROR", "服务器内部错误"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.INTERNAL_ERROR));
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

    private String identityCode(ErrorCode errorCode, HttpServletRequest request) {
        if (errorCode == ErrorCode.RESOURCE_NOT_FOUND) {
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
        return switch (errorCode) {
            case UNAUTHENTICATED, TOKEN_INVALID, TOKEN_EXPIRED, TOKEN_REVOKED -> "UNAUTHORIZED";
            case ACCOUNT_SEALED, ACCOUNT_DISABLED, NO_FUNCTION_PERM -> "FORBIDDEN";
            case RESOURCE_NOT_FOUND -> "NOT_FOUND";
            case PARAM_INVALID, PARAM_MISSING, PARAM_FORMAT_ERROR -> "BAD_REQUEST";
            case SERVICE_UNAVAILABLE -> "IDENTITY_SERVICE_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
    }

    private String identityPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int identityIndex = uri == null ? -1 : uri.indexOf("/auth/");
        return identityIndex < 0 ? "" : uri.substring(identityIndex);
    }

    private String identityMessage(ErrorCode errorCode, String fallback, HttpServletRequest request) {
        if (errorCode == ErrorCode.UNAUTHENTICATED
                && identityPath(request).startsWith("/auth/login")) {
            return (fallback == null || fallback.isBlank()) ? "账号或密码错误。" : fallback;
        }
        return switch (errorCode) {
            case UNAUTHENTICATED, TOKEN_INVALID, TOKEN_EXPIRED, TOKEN_REVOKED -> "请先登录";
            case SERVICE_UNAVAILABLE -> "统一身份认证暂时不可用，请稍后重试。";
            default -> fallback;
        };
    }

    private ErrorCode matchByCode(int code) {
        for (ErrorCode ec : ErrorCode.values()) {
            if (ec.getCode() == code) {
                return ec;
            }
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}

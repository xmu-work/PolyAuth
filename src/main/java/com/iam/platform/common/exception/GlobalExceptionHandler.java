package com.iam.platform.common.exception;

import com.iam.platform.common.enums.ErrorCode;
import com.iam.platform.common.result.Result;
import com.iam.platform.interfaces.identity.vo.IdentityErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
            "/auth/departments", "/auth/api-permissions/", "/auth/users/");

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode ec = matchByCode(ex.getCode());
        if (isIdentityRequest(request)) {
            return ResponseEntity.status(ec.getHttpStatus())
                    .body(new IdentityErrorResponse(
                            identityCode(ec, request), identityMessage(ec, ex.getMessage())));
        }
        return ResponseEntity.status(ec.getHttpStatus()).body(Result.fail(ex.getCode(), ex.getMessage()));
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

    private String identityMessage(ErrorCode errorCode, String fallback) {
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

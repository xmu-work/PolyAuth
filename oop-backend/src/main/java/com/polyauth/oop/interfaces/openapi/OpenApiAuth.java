package com.polyauth.oop.interfaces.openapi;

import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.interfaces.security.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 开放 API 访问上下文与用户范围校验。
 */
public final class OpenApiAuth {

    public static final String ATTR_MODE = "polyauth.open.mode";
    public static final String ATTR_APP_ID = "polyauth.open.appId";
    public static final String MODE_SYSTEM = "SYSTEM";
    public static final String MODE_SELF = "SELF";

    private OpenApiAuth() {
    }

    /**
     * 签名模式可访问任意用户；普通令牌模式只能访问本人。
     */
    public static void requireUserScope(HttpServletRequest request,
                                        Long targetUserId) {
        if (MODE_SYSTEM.equals(request.getAttribute(ATTR_MODE))) {
            return;
        }
        Long current = (Long) request.getAttribute(
                AuthInterceptor.ATTR_USER_ID);
        if (current == null || !current.equals(targetUserId)) {
            throw new UseCaseException(
                    ApplicationErrorCode.NO_FUNCTION_PERM,
                    "开放 API 查询他人权限需接入方签名认证");
        }
    }
}

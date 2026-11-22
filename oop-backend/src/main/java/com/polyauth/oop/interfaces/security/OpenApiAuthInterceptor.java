package com.polyauth.oop.interfaces.security;

import com.polyauth.oop.application.result.AuthenticatedPrincipal;
import com.polyauth.oop.application.usecase.AuthenticationUseCase;
import com.polyauth.oop.application.usecase.OpenApiAuthenticator;
import com.polyauth.oop.interfaces.openapi.OpenApiAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 开放 API 双模式认证拦截器。
 */
@Component
public class OpenApiAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_APP_ID = "X-App-Id";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";

    private final OpenApiAuthenticator authenticator;
    private final AuthenticationUseCase authenticationUseCase;

    /**
     * 注入接入方认证与令牌认证用例。
     */
    public OpenApiAuthInterceptor(OpenApiAuthenticator authenticator,
                                  AuthenticationUseCase authenticationUseCase) {
        this.authenticator = authenticator;
        this.authenticationUseCase = authenticationUseCase;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String appId = request.getHeader(HEADER_APP_ID);
        if (appId == null || appId.isBlank()) {
            AuthenticatedPrincipal principal =
                    authenticationUseCase.authenticate(
                            AuthInterceptor.bearerToken(
                                    request.getHeader("Authorization")));
            request.setAttribute(AuthInterceptor.ATTR_USER_ID,
                    principal.userId());
            request.setAttribute(AuthInterceptor.ATTR_SYSTEM_CODE,
                    principal.systemCode());
            request.setAttribute(OpenApiAuth.ATTR_MODE,
                    OpenApiAuth.MODE_SELF);
            return true;
        }
        authenticator.verify(appId,
                request.getHeader(HEADER_TIMESTAMP),
                request.getHeader(HEADER_NONCE),
                request.getHeader(HEADER_SIGNATURE));
        request.setAttribute(OpenApiAuth.ATTR_MODE,
                OpenApiAuth.MODE_SYSTEM);
        request.setAttribute(OpenApiAuth.ATTR_APP_ID, appId);
        return true;
    }
}

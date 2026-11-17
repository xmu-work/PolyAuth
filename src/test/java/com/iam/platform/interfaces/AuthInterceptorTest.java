package com.iam.platform.interfaces;

import com.iam.platform.application.api.ApiService;
import com.iam.platform.application.auth.AuthService;
import com.iam.platform.application.decision.DecisionService;
import com.iam.platform.application.log.LogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 请求拦截器审计与敏感参数脱敏测试。
 */
@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock
    private AuthService authService;
    @Mock
    private ApiService apiService;
    @Mock
    private DecisionService decisionService;
    @Mock
    private LogService logService;

    /**
     * 验证只读请求也写入审计，且查询串中的密码与令牌不会落库。
     */
    @Test
    @DisplayName("R44 审计覆盖只读请求并脱敏敏感参数")
    void afterCompletionAuditsGetAndRedactsSensitiveQuery() {
        AuthInterceptor interceptor = new AuthInterceptor(
                authService, apiService, decisionService, logService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.setContextPath("/api/v1");
        request.setQueryString("password=secret&token=abc&foo=bar");
        request.setAttribute(AuthInterceptor.ATTR_USER_ID, 1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = mock(HandlerMethod.class);

        interceptor.afterCompletion(request, response, handler, null);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(logService).record(eq(1L), isNull(), eq(AuthService.ADMIN_SYSTEM_CODE),
                eq("GET /users"), eq("/users"), detail.capture(), any(), eq(true),
                isNull(), any());
        assertThat(detail.getValue())
                .contains("password=***", "token=***", "foo=bar")
                .doesNotContain("secret", "abc");
    }
}

package com.polyauth.oop.interfaces.advice;

import com.polyauth.oop.application.exception.ApplicationErrorCode;
import com.polyauth.oop.application.exception.UseCaseException;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.common.IdentityErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OOP 统一身份错误契约测试。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("FR-33 不存在的 operationId 返回 API_OPERATION_NOT_FOUND")
    void missingOperationUsesStableIdentityCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/api-permissions/missing");

        ResponseEntity<?> response = handler.handleUseCase(
                new UseCaseException(ApplicationErrorCode.RESOURCE_NOT_FOUND, "没有找到对应的 API 操作"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(IdentityErrorResponse.class);
        IdentityErrorResponse body = (IdentityErrorResponse) response.getBody();
        assertThat(body.code()).isEqualTo("API_OPERATION_NOT_FOUND");
    }

    @Test
    @DisplayName("FR-34 令牌校验失败使用管理端数字错误码")
    void validateFailureUsesManagementEnvelope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/validate");

        ResponseEntity<?> response = handler.handleUseCase(
                new UseCaseException(ApplicationErrorCode.UNAUTHENTICATED, "请先登录"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isInstanceOf(ApiResult.class);
        ApiResult<?> body = (ApiResult<?>) response.getBody();
        assertThat(body.getCode()).isEqualTo(2001);
    }
}

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
import org.springframework.web.HttpRequestMethodNotSupportedException;

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

    @Test
    @DisplayName("管理端 HTTP 方法不匹配返回 405/1006")
    void managementMethodNotAllowedUses1006() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/systems/1");

        ResponseEntity<?> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("POST"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isInstanceOf(ApiResult.class);
        ApiResult<?> body = (ApiResult<?>) response.getBody();
        assertThat(body.getCode()).isEqualTo(1006);
    }

    @Test
    @DisplayName("统一身份 HTTP 方法不匹配返回 405/METHOD_NOT_ALLOWED")
    void identityMethodNotAllowedUsesStringCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/me");

        ResponseEntity<?> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("POST"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isInstanceOf(IdentityErrorResponse.class);
        IdentityErrorResponse body = (IdentityErrorResponse) response.getBody();
        assertThat(body.code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    @DisplayName("管理端 SERVICE_UNAVAILABLE 返回 503/5001")
    void managementServiceUnavailableUses5001() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/roles");

        ResponseEntity<?> response = handler.handleUseCase(
                new UseCaseException(ApplicationErrorCode.SERVICE_UNAVAILABLE, "服务暂不可用"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isInstanceOf(ApiResult.class);
        ApiResult<?> body = (ApiResult<?>) response.getBody();
        assertThat(body.getCode()).isEqualTo(5001);
    }

    @Test
    @DisplayName("统一身份 SERVICE_UNAVAILABLE 返回 503/IDENTITY_SERVICE_UNAVAILABLE")
    void identityServiceUnavailableUsesStringCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/me");

        ResponseEntity<?> response = handler.handleUseCase(
                new UseCaseException(ApplicationErrorCode.SERVICE_UNAVAILABLE, "暂不可用"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isInstanceOf(IdentityErrorResponse.class);
        IdentityErrorResponse body = (IdentityErrorResponse) response.getBody();
        assertThat(body.code()).isEqualTo("IDENTITY_SERVICE_UNAVAILABLE");
        assertThat(body.message()).isEqualTo("统一身份认证暂时不可用，请稍后重试。");
    }
}

package com.polyauth.api.shared;

import com.polyauth.service.structured.account.AccountService;
import com.polyauth.service.structured.api.ApiService;
import com.polyauth.service.structured.auth.AuthService;
import com.polyauth.service.port.auth.AuthenticatedSession;
import com.polyauth.service.port.auth.LoginResponse;
import com.polyauth.service.structured.decision.DecisionService;
import com.polyauth.service.structured.idempotency.IdempotencyStore;
import com.polyauth.service.structured.identity.IdentityService;
import com.polyauth.service.structured.log.LogService;
import com.polyauth.service.structured.openapi.OpenApiAuthenticator;
import com.polyauth.service.structured.organization.OrganizationService;
import com.polyauth.service.structured.permission.PermissionService;
import com.polyauth.service.structured.role.RoleService;
import com.polyauth.service.structured.system.SystemService;
import com.polyauth.service.common.enums.ErrorCode;
import com.polyauth.service.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证课程项目管理系统前后端联调所需的 CORS 预检、跨域响应头与统一身份登录契约细节。
 */
class IdentityCorsAndContractTest {

    private static final String COURSE_ORIGIN = "https://course.example.edu";
    private static final String AUTH = "Bearer good-token";

    @Nested
    @WebMvcTest
    class DefaultWildcardCors {

        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private AuthService authService;
        @MockitoBean
        private AccountService accountService;
        @MockitoBean
        private RoleService roleService;
        @MockitoBean
        private SystemService systemService;
        @MockitoBean
        private PermissionService permissionService;
        @MockitoBean
        private ApiService apiService;
        @MockitoBean
        private DecisionService decisionService;
        @MockitoBean
        private OrganizationService organizationService;
        @MockitoBean
        private LogService logService;
        @MockitoBean
        private OpenApiAuthenticator openApiAuthenticator;
        @MockitoBean
        private IdempotencyStore idempotencyStore;
        @MockitoBean
        private IdentityService identityService;

        @BeforeEach
        void setUp() {
            when(authService.authenticateSession("good-token"))
                    .thenReturn(new AuthenticatedSession(1L, "course-project-management"));
        }

        @Test
        @DisplayName("CORS 预检 OPTIONS 不应被认证拦截器拒绝且允许 Authorization 头")
        void preflightAllowsCourseOriginAndAuthorizationHeader() throws Exception {
            mvc.perform(options("/auth/permissions/MCO")
                            .header(HttpHeaders.ORIGIN, COURSE_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, COURSE_ORIGIN))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Authorization")));

            verify(authService, never()).authenticateSession(any());
        }

        @Test
        @DisplayName("前端直接调用权限检查接口时返回 CORS 允许头")
        void permissionCheckReturnsCorsAllowOriginHeader() throws Exception {
            when(identityService.hasPermissionCode(1L, "course-project-management", "MCO"))
                    .thenReturn(true);

            mvc.perform(get("/auth/permissions/MCO")
                            .header(HttpHeaders.ORIGIN, COURSE_ORIGIN)
                            .header(HttpHeaders.AUTHORIZATION, AUTH))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, COURSE_ORIGIN))
                    .andExpect(jsonPath("$.allowed").value(true));
        }

        @Test
        @DisplayName("登录密码错误时保留 Apifox 契约错误文案而非覆盖为请先登录")
        void loginFailureReturnsCredentialErrorMessage() throws Exception {
            when(authService.login(any()))
                    .thenThrow(new BusinessException(ErrorCode.UNAUTHENTICATED, "账号或密码错误。"));

            mvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "SG000001",
                                      "password": "wrong",
                                      "systems": ["course-project-management"]
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("账号或密码错误。"));
        }

        @Test
        @DisplayName("登录接口接受 Apifox 契约上限内的账号（128）与密码（256）长度")
        void loginAcceptsContractMaxUsernameAndPasswordLength() throws Exception {
            LoginResponse response = new LoginResponse();
            response.setAccessToken("tok");
            response.setTokenType("Bearer");
            response.setExpiresIn(7200L);
            when(authService.login(any())).thenReturn(response);

            String longUser = "u".repeat(64);
            String longPassword = "p".repeat(128);
            mvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + longUser
                                    + "\",\"password\":\"" + longPassword
                                    + "\",\"systems\":[\"course-project-management\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("tok"));
        }
    }

    @Nested
    @WebMvcTest
    @TestPropertySource(properties = "cors.allowed-origins=https://course.example.edu/")
    class RestrictedOriginCors {

        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private AuthService authService;
        @MockitoBean
        private AccountService accountService;
        @MockitoBean
        private RoleService roleService;
        @MockitoBean
        private SystemService systemService;
        @MockitoBean
        private PermissionService permissionService;
        @MockitoBean
        private ApiService apiService;
        @MockitoBean
        private DecisionService decisionService;
        @MockitoBean
        private OrganizationService organizationService;
        @MockitoBean
        private LogService logService;
        @MockitoBean
        private OpenApiAuthenticator openApiAuthenticator;
        @MockitoBean
        private IdempotencyStore idempotencyStore;
        @MockitoBean
        private IdentityService identityService;

        @Test
        @DisplayName("白名单模式支持末尾斜杠自动规范化并拒绝未授权来源")
        void explicitAllowedOriginsNormalizeTrailingSlashAndRejectOthers() throws Exception {
            mvc.perform(options("/auth/departments")
                            .header(HttpHeaders.ORIGIN, COURSE_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, COURSE_ORIGIN));

            mvc.perform(options("/auth/departments")
                            .header(HttpHeaders.ORIGIN, "https://evil.example.edu")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden());
        }
    }
}

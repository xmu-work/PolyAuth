package com.polyauth.api.shared;

import com.polyauth.service.structured.account.AccountService;
import com.polyauth.service.port.account.UserView;
import com.polyauth.service.structured.api.ApiService;
import com.polyauth.service.structured.auth.AuthService;
import com.polyauth.service.port.auth.AuthenticatedSession;
import com.polyauth.service.port.auth.LoginResponse;
import com.polyauth.service.structured.decision.DecisionService;
import com.polyauth.service.port.identity.IdentityAuditActorData;
import com.polyauth.service.port.identity.IdentityAuditLogData;
import com.polyauth.service.port.identity.IdentityProfile;
import com.polyauth.service.port.identity.IdentityDepartmentData;
import com.polyauth.service.port.identity.IdentityDepartmentUserData;
import com.polyauth.service.port.identity.IdentityPageSlice;
import com.polyauth.service.structured.identity.IdentityService;
import com.polyauth.service.port.identity.IdentityUserData;
import com.polyauth.service.structured.idempotency.IdempotencyStore;
import com.polyauth.service.structured.log.LogService;
import com.polyauth.service.structured.openapi.OpenApiAuthenticator;
import com.polyauth.service.structured.organization.OrganizationService;
import com.polyauth.service.structured.permission.PermissionService;
import com.polyauth.service.structured.role.RoleService;
import com.polyauth.service.structured.system.SystemService;
import com.polyauth.service.common.enums.ErrorCode;
import com.polyauth.service.common.exception.BusinessException;
import com.polyauth.infrastructure.persistence.structured.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
class ApiControllerTest {

    private static final String AUTH = "Bearer good-token";

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
        when(authService.authenticateSession("bad-token"))
                .thenThrow(new BusinessException(ErrorCode.TOKEN_INVALID));
        IamApi registered = new IamApi();
        registered.setId(30000L);
        registered.setPermissionId(90000L);
        registered.setApiName("测试 API");
        lenient().when(apiService.findByRequest(any(), any())).thenReturn(Optional.of(registered));
        lenient().when(decisionService.hasPermission(anyLong(), eq(AuthService.ADMIN_SYSTEM_CODE), anyLong()))
                .thenReturn(true);
    }

    @Test
    void unregisteredApiIsDeniedByDefault() throws Exception {
        when(apiService.findByRequest("/users", "POST")).thenReturn(Optional.empty());

        mvc.perform(post("/users").header("Authorization", AUTH)
                        .param("userNo", "x").param("realName", "张三").param("orgId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_FUNCTION_PERM.getCode()));
        verify(accountService, never()).createUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void loginIsPublic() throws Exception {
        LoginResponse response = new LoginResponse();
        response.setAccessToken("t");
        when(authService.login(any())).thenReturn(response);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"x\","
                                + "\"systems\":[\"ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("t"));
    }

    @Test
    @DisplayName("FR-31 统一身份登录遵循课程系统外部契约")
    void identityLoginMatchesExternalContract() throws Exception {
        LoginResponse response = new LoginResponse();
        response.setAccessToken("external-token");
        response.setTokenType("Bearer");
        response.setExpiresIn(7200L);
        when(authService.login(any())).thenReturn(response);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": 20220101,
                                  "password": "pwd",
                                  "systems": ["course-project-management"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("external-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("FR-34 统一身份退出登录返回 204 且无响应体")
    void identityLogoutReturnsNoContent() throws Exception {
        mvc.perform(post("/auth/logout").header("Authorization", AUTH))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("FR-38 当前统一身份用户返回角色与前端权限码")
    void identityCurrentUserReturnsRolesAndPermissionCodes() throws Exception {
        when(identityService.getCurrentUser(1L, "course-project-management"))
                .thenReturn(new IdentityProfile("u1", List.of("teacher"),
                        List.of("course.view"), "张三", "u1"));

        mvc.perform(get("/auth/me").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("u1"))
                .andExpect(jsonPath("$.roles[0]").value("teacher"))
                .andExpect(jsonPath("$.permissions[0]").value("course.view"))
                .andExpect(jsonPath("$.name").value("张三"))
                .andExpect(jsonPath("$.no").value("u1"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("FR-39 当前用户前端权限码校验返回 allowed")
    void identityPermissionCheckReturnsAllowed() throws Exception {
        when(identityService.hasPermissionCode(1L, "course-project-management", "course.view"))
                .thenReturn(true);

        mvc.perform(get("/auth/permissions/course.view").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.permission").doesNotExist());
    }

    @Test
    @DisplayName("FR-09 统一身份部门按直接下级分页返回展开标记")
    void identityDepartmentsReturnDirectChildrenPage() throws Exception {
        when(identityService.pageDepartments(null, 1L, 1, 20))
                .thenReturn(new IdentityPageSlice<>(1L, List.of(
                        new IdentityDepartmentData(2L, "CS", "计算机学院", 1L, true))));

        mvc.perform(get("/auth/departments").header("Authorization", AUTH)
                        .param("parentId", "1").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].code").value("CS"))
                .andExpect(jsonPath("$.items[0].name").value("计算机学院"))
                .andExpect(jsonPath("$.items[0].parentId").value(1))
                .andExpect(jsonPath("$.items[0].hasChildren").value(true));
    }

    @Test
    @DisplayName("FR-37 当前用户按 operationId 校验 API 调用权限")
    void identityApiPermissionUsesOperationId() throws Exception {
        when(identityService.hasApiOperation(
                1L, "course-project-management", "listAdminCourses")).thenReturn(true);

        mvc.perform(get("/auth/api-permissions/listAdminCourses").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value("listAdminCourses"))
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    @DisplayName("FR-02 统一身份部门用户分页不返回联系方式")
    void identityDepartmentUsersReturnMinimalIdentityFields() throws Exception {
        when(identityService.pageDepartmentUsers(1L, "孙", 1, 20))
                .thenReturn(new IdentityPageSlice<>(1L, List.of(
                        new IdentityDepartmentUserData("T1008", "T1008", "孙晨",
                                1L, "计算机学院"))));

        mvc.perform(get("/auth/departments/1/users").header("Authorization", AUTH)
                        .param("keyword", "孙").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].userId").value("T1008"))
                .andExpect(jsonPath("$.items[0].no").value("T1008"))
                .andExpect(jsonPath("$.items[0].name").value("孙晨"))
                .andExpect(jsonPath("$.items[0].departmentId").value(1))
                .andExpect(jsonPath("$.items[0].departmentName").value("计算机学院"))
                .andExpect(jsonPath("$.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.items[0].email").doesNotExist());
    }

    @Test
    @DisplayName("FR-02 统一身份用户查询只返回标识、工号和姓名")
    void identityUserLookupReturnsMinimalFields() throws Exception {
        when(identityService.lookupUser(1L, "course-project-management", "T1008"))
                .thenReturn(new IdentityUserData("T1008", "T1008", "孙晨"));

        mvc.perform(get("/auth/users/T1008").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("T1008"))
                .andExpect(jsonPath("$.no").value("T1008"))
                .andExpect(jsonPath("$.name").value("孙晨"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    @DisplayName("FR-32 统一身份接口未登录返回字符串错误码")
    void identityEndpointsUseStringErrorCode() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    @DisplayName("FR-37 不存在的 operationId 返回契约错误码")
    void identityMissingOperationReturnsContractError() throws Exception {
        when(identityService.hasApiOperation(
                1L, "course-project-management", "missingOperation"))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "没有找到对应的 API 操作。"));

        mvc.perform(get("/auth/api-permissions/missingOperation").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_OPERATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("没有找到对应的 API 操作。"));
    }

    @Test
    @DisplayName("FR-45 统一身份审计日志查询与写入遵循外部契约且使用字符串错误码")
    void identityAuditLogsQueryAndWriteFollowContract() throws Exception {
        OffsetDateTime at = OffsetDateTime.of(2026, 11, 24, 22, 10, 0, 0, ZoneOffset.ofHours(8));
        IdentityAuditLogData entry = new IdentityAuditLogData(
                88L,
                at,
                new IdentityAuditActorData(1L, "薛峰", List.of("admin", "teacher"), false),
                "score_changed",
                "1-01 星火队",
                Map.of("score", 80),
                Map.of("score", 92),
                null,
                "cpm-course-12",
                null);
        when(identityService.pageAuditLogs(
                eq(1L), eq("course-project-management"), eq("薛"), isNull(), eq("星火"),
                any(), any(), eq(1), eq(100)))
                .thenReturn(new IdentityPageSlice<>(1L, List.of(entry)));
        when(identityService.writeAuditLog(
                eq(1L), eq("course-project-management"), eq("req-1"), eq("score_changed"),
                eq("course"), eq("cpm-course-12"), eq("1-01 星火队"), any(), any(), any()))
                .thenReturn(entry);

        mvc.perform(get("/auth/audit-logs").header("Authorization", AUTH)
                        .param("actor", "薛")
                        .param("target", "星火")
                        .param("page", "1")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(100))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(88))
                .andExpect(jsonPath("$.items[0].actor.name").value("薛峰"))
                .andExpect(jsonPath("$.items[0].courseId").value("cpm-course-12"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mvc.perform(post("/auth/audit-logs").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "req-1",
                                  "type": "score_changed",
                                  "objectType": "course",
                                  "objectId": "cpm-course-12",
                                  "target": "1-01 星火队",
                                  "before": {"score": 80},
                                  "after": {"score": 92}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(88))
                .andExpect(jsonPath("$.type").value("score_changed"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mvc.perform(get("/auth/audit-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(post("/auth/audit-logs").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"\",\"type\":\"score_changed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        when(identityService.pageAuditLogs(
                eq(1L), eq("course-project-management"), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(1), eq(20)))
                .thenThrow(new BusinessException(ErrorCode.NO_FUNCTION_PERM, "你没有查看课程日志的权限。"));

        mvc.perform(get("/auth/audit-logs").header("Authorization", AUTH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("你没有查看课程日志的权限。"));
    }

    @Test
    void tokenIsRequiredForRoleApi() throws Exception {
        mvc.perform(get("/roles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/roles").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registeredApiWithoutPermissionIs403() throws Exception {
        IamApi api = new IamApi();
        api.setId(30004L);
        api.setPermissionId(20004L);
        api.setApiName("变更账号状态");
        when(apiService.findByRequest("/users/{userId}/status", "PUT")).thenReturn(Optional.of(api));
        when(decisionService.hasPermission(1L, "ADMIN", 20004L)).thenReturn(false);

        mvc.perform(put("/users/5/status").header("Authorization", AUTH).param("targetStatus", "DISABLED"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_FUNCTION_PERM.getCode()));
        verify(accountService, never()).changeStatus(any(), any(), any(), any());
    }

    @Test
    void registeredApiWithPermissionPasses() throws Exception {
        IamApi api = new IamApi();
        api.setId(30005L);
        api.setPermissionId(20005L);
        when(apiService.findByRequest("/roles", "GET")).thenReturn(Optional.of(api));
        when(roleService.listRoles()).thenReturn(List.of(new IamRole()));

        mvc.perform(get("/roles").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void userEndpoints() throws Exception {
        IamUser user = new IamUser();
        UserView view = new UserView();
        view.setUserNo("zhang");
        when(accountService.createUser("zhang", null, "张三", null, 1L, null)).thenReturn(user);
        when(accountService.getUser(5L)).thenReturn(user);
        when(accountService.getSensitiveView(5L)).thenReturn(
                new com.polyauth.service.port.account.SensitiveUserView());
        when(accountService.changeStatus(5L, "DISABLED", null, 1L)).thenReturn(user);
        when(accountService.listUsers(any(), isNull(), isNull())).thenReturn(new PageImpl<>(List.of(user)));
        when(accountService.toView(user)).thenReturn(view);

        mvc.perform(post("/users").header("Authorization", AUTH)
                        .param("userNo", "zhang").param("realName", "张三").param("orgId", "1"))
                .andExpect(jsonPath("$.data.userNo").value("zhang"));
        mvc.perform(get("/users/5").header("Authorization", AUTH))
                .andExpect(jsonPath("$.data.userNo").value("zhang"));
        mvc.perform(get("/users/5/sensitive").header("Authorization", AUTH))
                .andExpect(status().isOk());
        mvc.perform(get("/users").header("Authorization", AUTH).param("page", "1").param("size", "20"))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(put("/users/5/status").header("Authorization", AUTH).param("targetStatus", "DISABLED"))
                .andExpect(status().isOk());
    }

    @Test
    void idempotencyFingerprintIncludesCaller() throws Exception {
        when(authService.authenticateSession("second-token"))
                .thenReturn(new AuthenticatedSession(2L, "course-project-management"));
        when(roleService.grantRole(eq(5L), eq(2L), isNull(), anyLong(), isNull()))
                .thenReturn(new IamUserRole());
        when(idempotencyStore.acquire(eq("caller-key"), anyString(), anyLong()))
                .thenReturn(IdempotencyStore.Status.ACQUIRED, IdempotencyStore.Status.CONFLICT);

        mvc.perform(post("/roles/2/assignments").header("Authorization", AUTH)
                        .header("Idempotency-Key", "caller-key").param("userId", "5"))
                .andExpect(status().isOk());
        mvc.perform(post("/roles/2/assignments").header("Authorization", "Bearer second-token")
                        .header("Idempotency-Key", "caller-key").param("userId", "5"))
                .andExpect(status().isConflict());

        ArgumentCaptor<String> fingerprints = ArgumentCaptor.forClass(String.class);
        verify(idempotencyStore, times(2)).acquire(eq("caller-key"), fingerprints.capture(), anyLong());
        Assertions.assertNotEquals(fingerprints.getAllValues().get(0), fingerprints.getAllValues().get(1));
    }

    @Test
    void roleAssignmentAndPermissionEndpoints() throws Exception {
        when(roleService.grantRole(eq(1L), eq(2L), isNull(), eq(1L), isNull())).thenReturn(new IamUserRole());
        when(roleService.getRoleUsers(2L)).thenReturn(List.of(new IamUserRole()));
        when(roleService.getRolePermissions(2L)).thenReturn(List.of(new IamRolePermission()));

        mvc.perform(post("/roles/2/assignments").header("Authorization", AUTH).param("userId", "1"))
                .andExpect(status().isOk());
        mvc.perform(delete("/roles/2/assignments/1").header("Authorization", AUTH))
                .andExpect(status().isOk());
        mvc.perform(get("/roles/2/assignments").header("Authorization", AUTH))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(post("/roles/2/permissions").header("Authorization", AUTH)
                        .param("allowedAction", "VIEW").contentType(MediaType.APPLICATION_JSON)
                        .content("[10003,20001]"))
                .andExpect(status().isOk());
        mvc.perform(get("/roles/2/permissions").header("Authorization", AUTH))
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(roleService).revokeRole(1L, 2L, 1L);
        verify(roleService).bindRolePermissions(2L, List.of(10003L, 20001L), "VIEW", 1L);
    }

    @Test
    @DisplayName("FR-13 新建角色支持逗号分隔的多个父角色")
    void roleCreateAcceptsMultipleParents() throws Exception {
        when(roleService.createRole("AUD", "审计", "FUNC", null, List.of(2L, 3L)))
                .thenReturn(new IamRole());

        mvc.perform(post("/roles")
                        .header("Authorization", AUTH)
                        .param("roleCode", "AUD")
                        .param("roleName", "审计")
                        .param("roleType", "FUNC")
                        .param("parentIds", "2,3"))
                .andExpect(status().isOk());

        verify(roleService).createRole("AUD", "审计", "FUNC", null, List.of(2L, 3L));
    }

    @Test
    @DisplayName("FR-13 角色更新使用 JSON 请求体")
    void roleUpdateUsesJsonRequestBody() throws Exception {
        IamRole role = new IamRole();
        role.setId(2L);
        when(roleService.updateRole(2L, "新任角色", "更新说明", "ACTIVE", List.of(3L), 1L))
                .thenReturn(role);
        when(roleService.getParentIds(2L)).thenReturn(List.of(3L));

        mvc.perform(put("/roles/2")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "新任角色",
                                  "description": "更新说明",
                                  "status": "ACTIVE",
                                  "parentId": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2));

        verify(roleService).updateRole(2L, "新任角色", "更新说明", "ACTIVE", List.of(3L), 1L);
    }

    @Test
    void systemPermissionAndApiEndpoints() throws Exception {
        when(systemService.registerSystem("OA", "办公", null)).thenReturn(new IamSystem());
        when(systemService.listSystems()).thenReturn(List.of());
        when(permissionService.createPermission(
                1L, 10L, "USER_EXPORT", "导出用户", "BUTTON", null, "VIEW", null))
                .thenReturn(new IamPermission());
        when(permissionService.listPermissions(1L)).thenReturn(List.of(new IamPermission()));
        when(apiService.registerApi(1L, "USER_EXPORT", "导出用户", "/users/export", "POST"))
                .thenReturn(new IamApi());
        when(apiService.listApis(1L)).thenReturn(List.of(new IamApi()));

        mvc.perform(post("/systems").header("Authorization", AUTH)
                        .param("systemCode", "OA").param("systemName", "办公"))
                .andExpect(status().isOk());
        mvc.perform(get("/systems").header("Authorization", AUTH)).andExpect(status().isOk());
        mvc.perform(post("/permissions").header("Authorization", AUTH)
                        .param("systemId", "1").param("parentId", "10")
                        .param("permissionCode", "USER_EXPORT").param("permissionName", "导出用户")
                        .param("permissionType", "BUTTON").param("action", "VIEW"))
                .andExpect(status().isOk());
        mvc.perform(get("/permissions").header("Authorization", AUTH).param("systemId", "1"))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(post("/apis").header("Authorization", AUTH)
                        .param("systemId", "1").param("apiCode", "USER_EXPORT")
                        .param("apiName", "导出用户").param("apiPath", "/users/export")
                        .param("httpMethod", "POST"))
                .andExpect(status().isOk());
        mvc.perform(get("/apis").header("Authorization", AUTH).param("systemId", "1"))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    /**
     * 验证系统列表不会序列化接入密钥或公钥字段。
     */
    @Test
    @DisplayName("R35 系统响应不泄漏 appSecret/publicKey")
    void systemResponseDoesNotLeakSecrets() throws Exception {
        IamSystem system = new IamSystem();
        system.setId(1L);
        system.setSystemCode("ADMIN");
        system.setSystemName("权限后台");
        system.setAppSecret("top-secret");
        system.setPublicKey("internal-key");
        when(systemService.listSystems()).thenReturn(List.of(system));

        mvc.perform(get("/systems").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].systemCode").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].appSecret").doesNotExist())
                .andExpect(jsonPath("$.data[0].publicKey").doesNotExist());
    }

    @Test
    void organizationEndpointsUseOrganizationResource() throws Exception {
        IamOrg hq = new IamOrg();
        hq.setOrgCode("HQ1");
        when(organizationService.getOrgTree(null)).thenReturn(List.of(hq));
        when(organizationService.getChildren(1L)).thenReturn(List.of());
        when(organizationService.getOrg(1L)).thenReturn(hq);
        when(organizationService.createOrg("BR1", "分公司", "BRANCH", 2, 1L, null))
                .thenReturn(new IamOrg());
        when(organizationService.moveOrg(3L, 12L)).thenReturn(new IamOrg());

        mvc.perform(get("/organizations").header("Authorization", AUTH))
                .andExpect(jsonPath("$.data[0].orgCode").value("HQ1"));
        mvc.perform(get("/organizations/1/children").header("Authorization", AUTH))
                .andExpect(status().isOk());
        mvc.perform(post("/organizations").header("Authorization", AUTH)
                        .param("orgCode", "BR1").param("orgName", "分公司")
                        .param("orgType", "BRANCH").param("orgLevel", "2").param("parentId", "1"))
                .andExpect(status().isOk());
        mvc.perform(put("/organizations/3/parent").header("Authorization", AUTH).param("newParentId", "12"))
                .andExpect(status().isOk());
    }

    @Test
    void unifiedLogEndpointReturnsActionAndPermissionChanges() throws Exception {
        IamLog entry = new IamLog();
        entry.setLogId(9L);
        entry.setLogType(LogService.TYPE_PERMISSION_CHANGE);
        entry.setAction("GRANT_ROLE");
        entry.setSuccess(1);
        when(logService.queryLogs(any(), isNull(), isNull(), eq(LogService.TYPE_PERMISSION_CHANGE),
                eq("admin"), eq("GRANT"), eq(1), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(entry)));
        when(logService.exportLogs(isNull(), isNull(), eq(LogService.TYPE_PERMISSION_CHANGE),
                eq("admin"), eq("GRANT"), eq(1), isNull(), isNull()))
                .thenReturn(List.of(entry));
        when(logService.toCsv(List.of(entry))).thenReturn("logId,action\r\n1,GRANT_ROLE\r\n");

        mvc.perform(get("/logs").header("Authorization", AUTH)
                        .param("logType", LogService.TYPE_PERMISSION_CHANGE)
                        .param("username", "admin").param("action", "GRANT").param("success", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].logType").value(LogService.TYPE_PERMISSION_CHANGE))
                .andExpect(jsonPath("$.data.list[0].action").value("GRANT_ROLE"));
        mvc.perform(get("/logs/export").header("Authorization", AUTH)
                        .param("logType", LogService.TYPE_PERMISSION_CHANGE)
                        .param("username", "admin").param("action", "GRANT").param("success", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("audit-logs-")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("GRANT_ROLE")));
    }

    @Test
    void openApiReturnsFrontendPermissions() throws Exception {
        when(decisionService.getUserPermissions(1L, "ADMIN")).thenReturn(Set.of(20001L));

        mvc.perform(get("/open/permissions").header("Authorization", AUTH)
                        .param("userId", "1").param("systemCode", "ADMIN"))
                .andExpect(jsonPath("$.data[0]").value(20001));
    }

    @Test
    @DisplayName("HTTP 方法不匹配返回 405 而非 500")
    void wrongHttpMethodReturnsMethodNotAllowed() throws Exception {
        mvc.perform(post("/systems/1").header("Authorization", AUTH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("项目边界 数据权限端点不得暴露")
    void dataPermissionEndpointsAreNotExposed() throws Exception {
        mvc.perform(get("/systems/data-scopes").header("Authorization", AUTH))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/systems/data-scopes").header("Authorization", AUTH))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/roles/10/data-scopes").header("Authorization", AUTH))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/roles/10/data-scopes").header("Authorization", AUTH))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/open/authorize/resource").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"systemCode\":\"ADMIN\",\"apiId\":30001}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/open/data-scopes").header("Authorization", AUTH))
                .andExpect(status().is4xxClientError());
    }

    /**
     * 验证 RBAC3 角色继承、互斥、组织停用和调岗端点均已接通。
     */
    @Test
    @DisplayName("R04/R09/R13/R14 RBAC3 管理端点")
    void rbac3ManagementEndpoints() throws Exception {
        when(roleService.createRole(eq("PROJECT_MANAGER"), eq("项目经理"), eq("FUNC"), isNull(), eq(List.of(2L))))
                .thenReturn(new IamRole());
        when(roleService.updateRole(eq(3L), eq("部门经理"), isNull(), eq("ACTIVE"), eq(List.of(2L)), eq(1L)))
                .thenReturn(new IamRole());
        when(roleService.listRoleExclusions(3L)).thenReturn(List.of(10L));
        when(organizationService.updateOrg(3L, "部门新名称", 2, null, "DISABLED", 1L))
                .thenReturn(new IamOrg());
        IamUser transferred = new IamUser();
        transferred.setOrgId(12L);
        when(accountService.transferUser(5L, 12L, "预算员", 1L)).thenReturn(transferred);
        when(accountService.toView(transferred)).thenReturn(new UserView());

        mvc.perform(post("/roles").header("Authorization", AUTH)
                        .param("roleCode", "PROJECT_MANAGER").param("roleName", "项目经理")
                        .param("roleType", "FUNC").param("parentId", "2"))
                .andExpect(status().isOk());
        mvc.perform(put("/roles/3").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "部门经理",
                                  "status": "ACTIVE",
                                  "parentId": 2
                                }
                                """))
                .andExpect(status().isOk());
        mvc.perform(get("/roles/3/exclusions").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(10));
        mvc.perform(post("/roles/3/exclusions").header("Authorization", AUTH)
                        .param("excludedRoleId", "10"))
                .andExpect(status().isOk());
        mvc.perform(delete("/roles/3/exclusions/10").header("Authorization", AUTH))
                .andExpect(status().isOk());
        mvc.perform(put("/organizations/3").header("Authorization", AUTH)
                        .param("orgName", "部门新名称").param("sortNo", "2").param("status", "DISABLED"))
                .andExpect(status().isOk());
        mvc.perform(put("/users/5/organization").header("Authorization", AUTH)
                        .param("orgId", "12").param("post", "预算员"))
                .andExpect(status().isOk());
    }
}

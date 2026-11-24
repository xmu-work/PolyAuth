package com.polyauth.oop.interfaces;

import com.polyauth.oop.application.port.AccountCrypto;
import com.polyauth.oop.infrastructure.persistence.entity.ApiEntity;
import com.polyauth.oop.infrastructure.persistence.entity.OrganizationEntity;
import com.polyauth.oop.infrastructure.persistence.entity.PermissionEntity;
import com.polyauth.oop.infrastructure.persistence.entity.RoleEntity;
import com.polyauth.oop.infrastructure.persistence.entity.RoleGrantEntity;
import com.polyauth.oop.infrastructure.persistence.entity.RolePermissionEntity;
import com.polyauth.oop.infrastructure.persistence.entity.SystemEntity;
import com.polyauth.oop.infrastructure.persistence.entity.UserAccountEntity;
import com.polyauth.oop.infrastructure.persistence.repository.ApiJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.AuditLogJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.OrganizationJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.PermissionJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.RoleGrantJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.RoleJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.RolePermissionJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.SystemJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.TokenJpaRepository;
import com.polyauth.oop.infrastructure.persistence.repository.UserAccountJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证真实 HTTP 登录、统一身份和受保护管理端 API 的完整装配。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.type=com.zaxxer.hikari.HikariDataSource",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:oop_smoke;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
class OopApiSmokeTest {

    private static final String LOGIN_URL = "/auth/login";
    private static final String CURRENT_USER_URL = "/auth/me";
    private static final String ORGANIZATIONS_URL = "/organizations";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AccountCrypto crypto;

    @Autowired
    private SystemJpaRepository systems;

    @Autowired
    private OrganizationJpaRepository organizations;

    @Autowired
    private UserAccountJpaRepository users;

    @Autowired
    private PermissionJpaRepository permissions;

    @Autowired
    private ApiJpaRepository apis;

    @Autowired
    private RoleJpaRepository roles;

    @Autowired
    private RolePermissionJpaRepository rolePermissions;

    @Autowired
    private RoleGrantJpaRepository roleGrants;

    @Autowired
    private TokenJpaRepository tokens;

    @Autowired
    private AuditLogJpaRepository auditLogs;

    private Long organizationId;
    private Long userId;
    private String token;

    @BeforeEach
    void seed() {
        auditLogs.deleteAll();
        tokens.deleteAll();
        roleGrants.deleteAll();
        rolePermissions.deleteAll();
        apis.deleteAll();
        permissions.deleteAll();
        users.deleteAll();
        organizations.deleteAll();
        roles.deleteAll();
        systems.deleteAll();
        LocalDateTime now = LocalDateTime.now();
        SystemEntity system = new SystemEntity();
        system.setSystemCode("ADMIN");
        system.setSystemName("权限后台");
        system.setStatus("ACTIVE");
        system.setIsDeleted(0);
        system.setCreatedAt(now);
        system.setUpdatedAt(now);
        system.setVersion(0L);
        system = systems.saveAndFlush(system);

        OrganizationEntity organization = new OrganizationEntity();
        organization.setAncestors("0");
        organization.setOrgCode("HQ");
        organization.setOrgName("总公司");
        organization.setOrgType("HQ");
        organization.setOrgLevel(1);
        organization.setSortNo(1);
        organization.setStatus("ACTIVE");
        organization.setIsDeleted(0);
        organization.setCreatedAt(now);
        organization.setUpdatedAt(now);
        organization.setVersion(0L);
        organization = organizations.saveAndFlush(organization);
        organizationId = organization.getId();

        UserAccountEntity user = new UserAccountEntity();
        user.setUserNo("admin");
        user.setPasswordHash(crypto.hashPassword("Admin@123"));
        user.setNameEnc(crypto.encrypt("管理员"));
        user.setNameHash(crypto.hash("管理员"));
        user.setOrgId(organization.getId());
        user.setAccountStatus("NORMAL");
        user.setIsDeleted(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setVersion(0L);
        user = users.saveAndFlush(user);
        userId = user.getId();

        PermissionEntity permission = new PermissionEntity();
        permission.setSystemId(system.getId());
        permission.setPermissionCode("ORG_TREE");
        permission.setPermissionName("组织树");
        permission.setPermissionType("BUTTON");
        permission.setAction("VIEW");
        permission.setSortNo(1);
        permission.setStatus("ACTIVE");
        permission.setIsDeleted(0);
        permission.setCreatedAt(now);
        permission.setUpdatedAt(now);
        permission = permissions.saveAndFlush(permission);

        registerApi(system.getId(), permission.getId(),
                "GET_ORGANIZATIONS", "查询组织树", "/organizations", "GET", now);
        registerApi(system.getId(), permission.getId(),
                "UPDATE_ORGANIZATION", "更新组织", "/organizations/{orgId}", "PUT", now);
        registerApi(system.getId(), permission.getId(),
                "TRANSFER_USER", "员工调岗", "/users/{userId}/organization", "PUT", now);

        RoleEntity role = new RoleEntity();
        role.setRoleCode("SUPER_ADMIN");
        role.setRoleName("超级管理员");
        role.setRoleType("FUNC");
        role.setStatus("ACTIVE");
        role.setIsDeleted(0);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setVersion(0L);
        role = roles.saveAndFlush(role);

        RolePermissionEntity rolePermission = new RolePermissionEntity();
        rolePermission.setRoleId(role.getId());
        rolePermission.setPermissionId(permission.getId());
        rolePermission.setAllowedAction("VIEW");
        rolePermission.setCreatedAt(now);
        rolePermissions.saveAndFlush(rolePermission);

        RoleGrantEntity grant = new RoleGrantEntity();
        grant.setUserId(user.getId());
        grant.setRoleId(role.getId());
        grant.setEffectiveTime(now.minusMinutes(1));
        grant.setStatus("ACTIVE");
        grant.setCreatedAt(now);
        grant.setUpdatedAt(now);
        grant.setVersion(0L);
        roleGrants.saveAndFlush(grant);

        ResponseEntity<Map> login = rest.postForEntity(
                LOGIN_URL,
                Map.of("username", "admin", "password", "Admin@123",
                        "systems", List.of("ADMIN")),
                Map.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        token = String.valueOf(login.getBody().get("accessToken"));
    }

    @Test
    @DisplayName("FR-38 真实 HTTP 登录后可查询统一身份")
    void currentUserWorksThroughRealHttp() {
        ResponseEntity<Map> response = rest.exchange(
                CURRENT_USER_URL, HttpMethod.GET,
                authorized(), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("subject")).isEqualTo("admin");
        assertThat(String.valueOf(response.getBody().get("roles")))
                .contains("admin");
    }

    @Test
    @DisplayName("FR-37 已登记且有权限的组织 API 可调用")
    void organizationApiPassesDefaultDenyGuard() {
        ResponseEntity<Map> response = rest.exchange(
                ORGANIZATIONS_URL, HttpMethod.GET,
                authorized(), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("code")).isEqualTo(0);
        assertThat((List<?>) response.getBody().get("data")).isNotEmpty();
    }

    @Test
    @DisplayName("未登记的 HTTP 方法按默认拒绝返回 403")
    void unregisteredHttpMethodIsDeniedByDefault() {
        ResponseEntity<Map> response = rest.exchange(
                ORGANIZATIONS_URL, HttpMethod.POST,
                authorized(), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().get("code")).isEqualTo(2007);
    }

    @Test
    @DisplayName("组织更新支持 Query/Form 参数绑定")
    void updateOrganizationAcceptsQueryFormParameters() {
        ResponseEntity<Map> response = rest.exchange(
                "/organizations/" + organizationId
                        + "?orgName=新总公司&sortNo=2&status=ACTIVE",
                HttpMethod.PUT, authorized(), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("code")).isEqualTo(0);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("orgName")).isEqualTo("新总公司");
        assertThat(((Number) data.get("sortNo")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("员工调岗支持 Query/Form 参数绑定")
    void transferUserAcceptsQueryFormParameters() {
        ResponseEntity<Map> response = rest.exchange(
                "/users/" + userId + "/organization?orgId=" + organizationId
                        + "&post=架构师",
                HttpMethod.PUT, authorized(), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("code")).isEqualTo(0);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(((Number) data.get("orgId")).longValue()).isEqualTo(organizationId);
        assertThat(data.get("post")).isEqualTo("架构师");
    }

    @Test
    @DisplayName("自助改密成功后会吊销旧访问令牌")
    void changePasswordRevokesExistingToken() {
        ResponseEntity<Map> changed = rest.exchange(
                "/auth/password?oldPassword=Admin@123&newPassword=NewPass123",
                HttpMethod.PUT, authorized(), Map.class);

        assertThat(changed.getStatusCode().value()).isEqualTo(200);
        assertThat(changed.getBody().get("code")).isEqualTo(0);

        ResponseEntity<Map> after = rest.exchange(
                CURRENT_USER_URL, HttpMethod.GET, authorized(), Map.class);

        assertThat(after.getStatusCode().value()).isEqualTo(401);
        assertThat(after.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("HTTP 方法不匹配返回 405/1006")
    void wrongHttpMethodReturnsMethodNotAllowed() {
        ResponseEntity<Map> response = rest.exchange(
                "/systems/1", HttpMethod.POST,
                authorized(), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().get("code")).isEqualTo(1006);
    }

    @Test
    @DisplayName("FR-45 外部统一身份审计日志写入幂等且查询不混入访问日志")
    void externalAuditLogsSupportIdempotentWriteAndFilteredQuery() {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of(
                "requestId", requestId,
                "type", "roster_changed",
                "objectType", "class",
                "objectId", "cpm-class-123",
                "target", "导入学生名单",
                "before", Map.of("imported", 0),
                "after", Map.of("imported", 12));

        ResponseEntity<Map> created = rest.exchange(
                "/auth/audit-logs",
                HttpMethod.POST,
                authorizedJson(body),
                Map.class);
        ResponseEntity<Map> replayed = rest.exchange(
                "/auth/audit-logs",
                HttpMethod.POST,
                authorizedJson(body),
                Map.class);

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(replayed.getStatusCode().value()).isEqualTo(201);
        assertThat(replayed.getBody().get("id")).isEqualTo(created.getBody().get("id"));
        assertThat(created.getBody().get("classId")).isEqualTo("cpm-class-123");

        ResponseEntity<Map> page = rest.exchange(
                "/auth/audit-logs?page=1&pageSize=100",
                HttpMethod.GET,
                authorized(),
                Map.class);

        assertThat(page.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) page.getBody().get("total")).longValue()).isEqualTo(1L);
        List<?> items = (List<?>) page.getBody().get("items");
        assertThat(items).hasSize(1);
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertThat(first.get("id")).isEqualTo(created.getBody().get("id"));
        assertThat(first.get("type")).isEqualTo("roster_changed");
        assertThat(first.get("target")).isEqualTo("导入学生名单");
        assertThat(OffsetDateTime.parse(String.valueOf(first.get("at")))).isNotNull();
        Map<?, ?> actor = (Map<?, ?>) first.get("actor");
        assertThat(((Number) actor.get("id")).longValue()).isEqualTo(userId);
        assertThat(actor.get("name")).isEqualTo("管理员");
        assertThat((List<?>) actor.get("roles")).asString().contains("admin");
        assertThat(actor.get("deleted")).isEqualTo(false);

        ResponseEntity<Map> unauthenticated = rest.getForEntity("/auth/audit-logs", Map.class);
        assertThat(unauthenticated.getStatusCode().value()).isEqualTo(401);
        assertThat(unauthenticated.getBody().get("code")).isEqualTo("UNAUTHORIZED");

        ResponseEntity<Map> invalid = rest.exchange(
                "/auth/audit-logs",
                HttpMethod.POST,
                authorizedJson(Map.of("requestId", "", "type", "roster_changed")),
                Map.class);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(invalid.getBody().get("code")).isEqualTo("BAD_REQUEST");
    }

    private HttpEntity<Map<String, Object>> authorizedJson(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(body, headers);
    }

    private void registerApi(Long systemId, Long permissionId, String code,
                             String name, String path, String method,
                             LocalDateTime now) {
        ApiEntity api = new ApiEntity();
        api.setSystemId(systemId);
        api.setPermissionId(permissionId);
        api.setApiCode(code);
        api.setApiName(name);
        api.setApiPath(path);
        api.setHttpMethod(method);
        api.setStatus("ACTIVE");
        api.setIsDeleted(0);
        api.setCreatedAt(now);
        api.setUpdatedAt(now);
        apis.saveAndFlush(api);
    }

    private HttpEntity<Void> authorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }
}

package com.polyauth.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 两套后端共享的黑盒 API 契约。
 *
 * <p>运行时通过 {@code -Dcontract.baseUrl=http://host:port/api/v1} 指定目标实现。
 * 未指定目标时跳过，不用 Mock 伪造双后端通过。
 */
class DualBackendContractTest {

    private ContractHttpClient client;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String baseUrl = System.getProperty("contract.baseUrl", "");
        Assumptions.assumeTrue(!baseUrl.isBlank(),
                "需要 -Dcontract.baseUrl 指向实际运行的后端");
        client = new ContractHttpClient(baseUrl);
        token = client.login(
                System.getProperty("contract.username", "admin"),
                System.getProperty("contract.password", "Admin@123"),
                System.getProperty("contract.system", "ADMIN"));
    }

    @Test
    @DisplayName("未认证访问角色接口返回 401/2001")
    void unauthenticatedRoleRequestIsRejected() throws Exception {
        ContractHttpClient.Response response = client.get("/roles", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(client.json(response).path("code").asInt()).isEqualTo(2001);
    }

    @Test
    @DisplayName("有效令牌可以查询角色列表")
    void authenticatedRoleListUsesUnifiedEnvelope() throws Exception {
        ContractHttpClient.Response response = client.get("/roles", token);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = client.json(response);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").isArray()).isTrue();
    }

    @Test
    @DisplayName("不存在角色返回 404/1005")
    void missingRoleUsesStableNotFoundCode() throws Exception {
        ContractHttpClient.Response response =
                client.get("/roles/9223372036854775000", token);

        assertThat(response.status()).isEqualTo(404);
        assertThat(client.json(response).path("code").asInt()).isEqualTo(1005);
    }

    @Test
    @DisplayName("重复角色编码返回 409/3009")
    void duplicateRoleCodeUsesStableConflictCode() throws Exception {
        String roleCode = uniqueCode("DUP");
        Map<String, String> form = roleForm(roleCode, "契约重复角色");
        assertThat(client.postForm("/roles", token, form).status()).isEqualTo(200);

        ContractHttpClient.Response duplicate = client.postForm("/roles", token, form);

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(client.json(duplicate).path("code").asInt()).isEqualTo(3009);
    }

    @Test
    @DisplayName("角色继承成环返回 409/3002")
    void roleInheritanceCycleUsesStableConflictCode() throws Exception {
        ContractHttpClient.Response parent = client.postForm(
                "/roles", token, roleForm(uniqueCode("PARENT"), "契约父角色"));
        assertThat(parent.status()).isEqualTo(200);
        long parentId = client.json(parent).path("data").path("id").asLong();

        Map<String, String> childForm = roleForm(uniqueCode("CHILD"), "契约子角色");
        childForm.put("parentId", String.valueOf(parentId));
        ContractHttpClient.Response child = client.postForm("/roles", token, childForm);
        assertThat(child.status()).isEqualTo(200);
        long childId = client.json(child).path("data").path("id").asLong();

        ContractHttpClient.Response cycle = client.putJson(
                "/roles/" + parentId, token,
                "{\"roleName\":\"契约父角色\",\"status\":\"ACTIVE\",\"parentId\":" + childId + "}");

        assertThat(cycle.status()).isEqualTo(409);
        assertThat(client.json(cycle).path("code").asInt()).isEqualTo(3002);
    }

    @Test
    @DisplayName("失效时间不晚于当前时间的角色授予返回 400/1001")
    void expiredTemporaryGrantIsRejected() throws Exception {
        ContractHttpClient.Response role = client.postForm(
                "/roles", token, roleForm(uniqueCode("TEMP"), "契约临时角色"));
        long roleId = client.json(role).path("data").path("id").asLong();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("userId", "1");
        form.put("expireTime", "2000-01-01T00:00:00");

        ContractHttpClient.Response response =
                client.postForm("/roles/" + roleId + "/assignments", token, form);

        assertThat(response.status()).isEqualTo(400);
        assertThat(client.json(response).path("code").asInt()).isEqualTo(1001);
    }

    @Test
    @DisplayName("FR-34 令牌校验未认证时使用管理端数字错误码")
    void validateWithoutTokenUsesManagementErrorEnvelope() throws Exception {
        ContractHttpClient.Response response = client.get("/auth/validate", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(client.json(response).path("code").asInt()).isEqualTo(2001);
    }

    @Test
    @DisplayName("FR-33 不存在的 operationId 使用统一身份稳定错误码")
    void missingOperationUsesStableIdentityErrorCode() throws Exception {
        ContractHttpClient.Response response =
                client.get("/auth/api-permissions/missingOperation", token);

        assertThat(response.status()).isEqualTo(404);
        assertThat(client.json(response).path("code").asText())
                .isEqualTo("API_OPERATION_NOT_FOUND");
    }

    @Test
    @DisplayName("FR-38 双后端均返回统一身份当前用户")
    void identityCurrentUserIsAvailable() throws Exception {
        ContractHttpClient.Response response = client.get("/auth/me", token);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = client.json(response);
        assertThat(body.path("subject").asText()).isNotBlank();
        assertThat(body.path("roles").isArray()).isTrue();
        assertThat(body.path("permissions").isArray()).isTrue();
    }

    @Test
    @DisplayName("FR-09 双后端均返回组织树")
    void organizationTreeIsAvailable() throws Exception {
        ContractHttpClient.Response response = client.get("/organizations", token);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = client.json(response);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").isArray()).isTrue();
    }

    @Test
    @DisplayName("FR-35 双后端均返回业务系统列表")
    void systemListIsAvailable() throws Exception {
        ContractHttpClient.Response response = client.get("/systems", token);

        assertThat(response.status()).isEqualTo(200);
        assertThat(client.json(response).path("data").isArray()).isTrue();
    }

    @Test
    @DisplayName("FR-17 双后端均返回前端权限树")
    void permissionTreeIsAvailable() throws Exception {
        ContractHttpClient.Response response =
                client.get("/permissions?systemId=1", token);

        assertThat(response.status()).isEqualTo(200);
        assertThat(client.json(response).path("data").isArray()).isTrue();
    }

    @Test
    @DisplayName("FR-19 双后端均返回后端 API 目录")
    void apiCatalogIsAvailable() throws Exception {
        ContractHttpClient.Response response =
                client.get("/apis?systemId=1", token);

        assertThat(response.status()).isEqualTo(200);
        assertThat(client.json(response).path("data").isArray()).isTrue();
    }

    @Test
    @DisplayName("FR-02 双后端均返回用户分页")
    void userPageIsAvailable() throws Exception {
        ContractHttpClient.Response response =
                client.get("/users?page=1&size=20", token);

        assertThat(response.status()).isEqualTo(200);
        JsonNode data = client.json(response).path("data");
        assertThat(data.path("page").asInt()).isEqualTo(1);
        assertThat(data.path("pageSize").asInt()).isEqualTo(20);
        assertThat(data.path("list").isArray()).isTrue();
    }

    @Test
    @DisplayName("FR-45 双后端均返回统一日志分页")
    void auditLogPageIsAvailable() throws Exception {
        ContractHttpClient.Response response =
                client.get("/logs?page=1&size=20", token);

        assertThat(response.status()).isEqualTo(200);
        JsonNode data = client.json(response).path("data");
        assertThat(data.path("total").isNumber()).isTrue();
        assertThat(data.path("list").isArray()).isTrue();
    }

    private static Map<String, String> roleForm(String code, String name) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("roleCode", code);
        form.put("roleName", name);
        form.put("roleType", "FUNC");
        form.put("description", "PolyAuth 双后端共享契约");
        return form;
    }

    private static String uniqueCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }
}

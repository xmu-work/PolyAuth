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

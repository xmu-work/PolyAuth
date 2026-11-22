package com.polyauth.oop.interfaces;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 OOP 后端覆盖结构化后端的全部外部 HTTP 端点。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api_parity;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ApiParityContractTest {

    private static final Set<String> STRUCTURED_ENDPOINTS = Set.of(
            "GET /apis",
            "GET /apis/{apiId}",
            "POST /apis",
            "GET /auth/api-permissions/{operationId}",
            "GET /auth/departments",
            "GET /auth/departments/{departmentId}/users",
            "POST /auth/login",
            "POST /auth/logout",
            "GET /auth/me",
            "PUT /auth/password",
            "GET /auth/permissions/{permission}",
            "GET /auth/users/{userId}",
            "GET /auth/validate",
            "GET /logs",
            "GET /logs/export",
            "GET /open/permissions",
            "GET /organizations",
            "GET /organizations/{orgId}",
            "GET /organizations/{orgId}/children",
            "POST /organizations",
            "PUT /organizations/{orgId}",
            "PUT /organizations/{orgId}/parent",
            "DELETE /permissions/{permissionId}/apis/{apiId}",
            "GET /permissions",
            "GET /permissions/{permissionId}/apis",
            "POST /permissions",
            "POST /permissions/{permissionId}/apis/{apiId}",
            "DELETE /roles/{roleId}/assignments/{userId}",
            "DELETE /roles/{roleId}/exclusions/{excludedRoleId}",
            "DELETE /roles/{roleId}/permissions",
            "GET /roles",
            "GET /roles/assignments",
            "GET /roles/{roleId}",
            "GET /roles/{roleId}/assignments",
            "GET /roles/{roleId}/exclusions",
            "GET /roles/{roleId}/permissions",
            "POST /roles",
            "POST /roles/{roleId}/assignments",
            "POST /roles/{roleId}/exclusions",
            "POST /roles/{roleId}/permissions",
            "PUT /roles/{roleId}",
            "GET /systems",
            "GET /systems/{systemId}",
            "POST /systems",
            "GET /users",
            "GET /users/{userId}",
            "GET /users/{userId}/sensitive",
            "POST /users",
            "PUT /users/{userId}/organization",
            "PUT /users/{userId}/status"
    );

    private static final Set<String> REMOVED_DATA_PERMISSION_ENDPOINTS = Set.of(
            "DELETE /roles/{roleId}/data-scopes/{bindingId}",
            "GET /open/data-scopes",
            "GET /roles/{roleId}/data-scopes",
            "GET /systems/data-scopes",
            "POST /open/authorize/resource",
            "POST /roles/{roleId}/data-scopes",
            "POST /systems/data-scopes",
            "POST /systems/role-data-scopes"
    );

    private final RequestMappingHandlerMapping handlerMapping;

    ApiParityContractTest(
            @Qualifier("requestMappingHandlerMapping")
            RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Test
    @DisplayName("FR-01~FR-49 OOP 必须覆盖结构化后端的全部外部端点")
    void oopRegistersEveryStructuredEndpoint() {
        Set<String> actual = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
            Set<String> patterns = entry.getKey().getPatternValues();
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (methods.isEmpty()) {
                    actual.add("* " + pattern);
                } else {
                    methods.forEach(method -> actual.add(method.name() + " " + pattern));
                }
            }
        }

        Set<String> missing = new TreeSet<>(STRUCTURED_ENDPOINTS);
        missing.removeAll(actual);

        assertThat(missing)
                .as("OOP 缺失的结构化后端外部端点")
                .isEmpty();
        assertThat(actual)
                .as("仅功能权限项目不得注册数据权限端点")
                .doesNotContainAnyElementsOf(REMOVED_DATA_PERMISSION_ENDPOINTS);
    }
}

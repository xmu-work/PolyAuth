package com.polyauth.oop.interfaces;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 OOP 后端对课程项目管理系统前后端联调所需的 CORS 预检与统一身份登录契约。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:oop_cors_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "cors.allowed-origins=https://course.example.edu/"
})
@AutoConfigureMockMvc
class IdentityCorsAndContractTest {

    private static final String COURSE_ORIGIN = "https://course.example.edu";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("OOP 后端放行白名单 Origin 的 CORS 预检并拒绝非法来源")
    void preflightAllowsConfiguredOriginAndRejectsOthers() throws Exception {
        mvc.perform(options("/auth/permissions/MCO")
                        .header(HttpHeaders.ORIGIN, COURSE_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, COURSE_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Authorization")));

        mvc.perform(options("/auth/departments")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.edu")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OOP 后端登录失败返回账号密码错误文案且接受契约上限内长度")
    void loginFailureReturnsCredentialMessageAndAcceptsLongCredentials() throws Exception {
        String longUser = "u".repeat(64);
        String longPassword = "p".repeat(128);

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + longUser
                                + "\",\"password\":\"" + longPassword
                                + "\",\"systems\":[\"course-project-management\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("账号或密码错误。"));
    }
}

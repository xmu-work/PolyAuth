package com.polyauth.oop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证 OOP 后端完整 Spring 容器能够独立装配。
 */
@SpringBootTest(properties = {
        "server.port=0",
        "spring.datasource.type=com.zaxxer.hikari.HikariDataSource",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:polyauth_context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ApplicationContextTest {

    @Test
    void contextLoads() {
    }
}

package com.polyauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 多系统统一权限管理平台启动入口。
 *
 * <p>同应用、同 API、同数据库、双 Service 实现：
 * 通过 {@code app.backend-strategy=structured|ddd} 选择业务服务实现。
 */
@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
public class PolyAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolyAuthApplication.class, args);
    }
}

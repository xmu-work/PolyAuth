package com.polyauth.structured;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 多系统统一权限管理平台结构化后端启动类。
 *
 * <p>对应详细设计说明书 3.1 分层结构：接口层 → 应用服务层 → 基础设施层。
 * 采用结构化编程思想：自顶向下分层、单一职责、高内聚低耦合。
 */
@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
public class PolyAuthStructuredApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolyAuthStructuredApplication.class, args);
    }
}

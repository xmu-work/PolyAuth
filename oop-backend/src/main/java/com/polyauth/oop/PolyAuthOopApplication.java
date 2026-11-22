package com.polyauth.oop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 多系统统一权限管理平台面向对象后端启动类。
 */
@SpringBootApplication
@EnableTransactionManagement
public class PolyAuthOopApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolyAuthOopApplication.class, args);
    }
}

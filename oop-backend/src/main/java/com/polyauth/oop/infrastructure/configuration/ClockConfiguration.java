package com.polyauth.oop.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 为领域与应用用例提供统一可替换时钟。
 */
@Configuration
public class ClockConfiguration {

    /**
     * 生产环境使用系统时钟。
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

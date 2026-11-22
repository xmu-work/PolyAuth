package com.polyauth.structured.infrastructure.scheduling;

import com.polyauth.structured.application.expiration.GrantExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 临时授权到期扫描调度器。
 */
@Component
@RequiredArgsConstructor
public class GrantExpirationScheduler {

    private final GrantExpirationService grantExpirationService;

    /**
     * 按配置间隔扫描到期授权；默认每分钟执行一次。
     */
    @Scheduled(fixedDelayString = "${permission.expiration-scan-ms:60000}")
    public void expireDueGrants() {
        grantExpirationService.expireDueGrants();
    }
}

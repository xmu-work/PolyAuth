package com.polyauth.infrastructure.scheduling;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.polyauth.service.structured.expiration.GrantExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 临时授权到期扫描调度器。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "structured", matchIfMissing = true)
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

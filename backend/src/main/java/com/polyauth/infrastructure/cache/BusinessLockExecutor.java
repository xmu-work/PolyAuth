package com.polyauth.infrastructure.cache;

import com.polyauth.service.common.enums.ErrorCode;
import com.polyauth.service.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 业务写操作分布式锁执行器。
 *
 * <p>锁只覆盖数据库事务内的关键写操作，获取失败时按冲突处理并 fail closed。
 */
@Component
@RequiredArgsConstructor
public class BusinessLockExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessLockExecutor.class);
    private static final long LOCK_TIMEOUT_SECONDS = 15L;

    private final PermissionCacheComponent permissionCache;

    /** 本地无 Redis 时可选无锁执行；生产保持 fail-closed。 */
    @Value("${iam.redis.fail-open:false}")
    private boolean redisFailOpen;

    /**
     * 在命名锁内执行有返回值操作，并确保只释放本线程持有的锁。
     *
     * @param lockKey 业务唯一键
     * @param action 受保护操作
     * @param <T> 返回类型
     * @return 受保护操作结果
     */
    public <T> T execute(String lockKey, Supplier<T> action) {
        String ownerToken;
        try {
            ownerToken = permissionCache.tryLock(lockKey, LOCK_TIMEOUT_SECONDS);
        } catch (DataAccessException exception) {
            if (redisFailOpen) {
                LOGGER.warn("Redis 不可用，本地模式无锁执行 {}: {}", lockKey, exception.getMessage());
                return action.get();
            }
            throw exception;
        }
        if (ownerToken == null) {
            throw new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED, "操作正在被处理，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            try {
                permissionCache.unlock(lockKey, ownerToken);
            } catch (DataAccessException exception) {
                if (!redisFailOpen) {
                    throw exception;
                }
                LOGGER.warn("Redis 不可用，跳过锁释放 {}: {}", lockKey, exception.getMessage());
            }
        }
    }
}

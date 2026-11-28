package com.polyauth.service.ddd.application.port;

/**
 * 登录与关键权限变更审计端口。
 */
public interface AuditTrail {

    /**
     * 记录登录审计，不保存密码或 JWT。
     */
    void recordLogin(Long userId, String userNo, String systemCode, boolean success,
                     String error, String clientIp, String requestId);

    /**
     * 记录管理端请求审计，敏感查询参数由接口层先行脱敏。
     */
    void recordRequest(Long userId, String userNo, String systemCode, String action,
                       String target, String clientIp, boolean success,
                       String error, String requestId);

    /**
     * 记录关键授权变更前后快照。
     */
    void recordPermissionChange(Long operatorId, String systemCode, String action,
                                String target, Object before, Object after);
}

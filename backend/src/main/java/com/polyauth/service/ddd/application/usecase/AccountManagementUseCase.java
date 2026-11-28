package com.polyauth.service.ddd.application.usecase;

import com.polyauth.service.ddd.application.exception.ApplicationErrorCode;
import com.polyauth.service.ddd.application.exception.UseCaseException;
import com.polyauth.service.ddd.application.port.AccountCrypto;
import com.polyauth.service.ddd.application.port.AuditTrail;
import com.polyauth.service.ddd.domain.exception.DomainRuleException;
import com.polyauth.service.ddd.domain.model.AccountStatus;
import com.polyauth.service.ddd.domain.model.DomainPage;
import com.polyauth.service.ddd.domain.model.Organization;
import com.polyauth.service.ddd.domain.model.RoleGrant;
import com.polyauth.service.ddd.domain.model.UserAccount;
import com.polyauth.service.ddd.domain.repository.OrganizationRepository;
import com.polyauth.service.ddd.domain.repository.RoleGrantRepository;
import com.polyauth.service.ddd.domain.repository.TokenSessionRepository;
import com.polyauth.service.ddd.domain.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户账号生命周期用例。
 */
@Service
@Transactional
public class AccountManagementUseCase {

    private final UserAccountRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final TokenSessionRepository tokenRepository;
    private final AccountCrypto crypto;
    private final AuditTrail auditTrail;
    private final Clock clock;

    @Value("${iam.default-password:Iam@123456}")
    private String defaultPassword;

    /**
     * 注入账号管理依赖。
     */
    public AccountManagementUseCase(UserAccountRepository userRepository,
                                    OrganizationRepository organizationRepository,
                                    RoleGrantRepository roleGrantRepository,
                                    TokenSessionRepository tokenRepository,
                                    AccountCrypto crypto,
                                    AuditTrail auditTrail,
                                    Clock clock) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.tokenRepository = tokenRepository;
        this.crypto = crypto;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    /**
     * 创建账号并加密敏感字段。
     */
    public UserAccount create(String userNo, String password, String realName,
                              String phone, Long orgId, String post,
                              Long operatorId) {
        if (userRepository.findByUserNo(userNo).isPresent()) {
            throw new UseCaseException(
                    ApplicationErrorCode.RESOURCE_DUPLICATE, "账号已存在: " + userNo);
        }
        Organization organization = organizationRepository.findById(orgId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "组织不存在: " + orgId));
        if (!organization.isActive()) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "停用组织不能创建用户");
        }
        String rawPassword = password == null || password.isBlank()
                ? defaultPassword : password;
        LocalDateTime now = now();
        UserAccount user = UserAccount.create(
                userNo.trim(),
                crypto.hashPassword(rawPassword),
                crypto.encrypt(realName),
                crypto.hash(realName),
                phone == null || phone.isBlank() ? null : crypto.encrypt(phone),
                phone == null || phone.isBlank() ? null : crypto.hash(phone),
                orgId, post, operatorId, now);
        return userRepository.save(user);
    }

    /**
     * 分页查询用户。
     */
    @Transactional(readOnly = true)
    public DomainPage<UserAccount> page(int page, int pageSize, Long orgId,
                                        String accountStatus) {
        return userRepository.findPage(page, pageSize, orgId, accountStatus);
    }

    /**
     * 查询用户详情。
     */
    @Transactional(readOnly = true)
    public UserAccount get(Long userId) {
        return require(userId);
    }

    /**
     * 变更账号状态；非正常状态吊销令牌，封存时回收全部角色。
     */
    public UserAccount changeStatus(Long userId, String targetStatus, String reason,
                                    Long operatorId) {
        AccountStatus target;
        try {
            target = AccountStatus.valueOf(targetStatus);
        } catch (IllegalArgumentException exception) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "未知账号状态: " + targetStatus);
        }
        UserAccount user = require(userId);
        Map<String, Object> before = snapshot(user);
        LocalDateTime now = now();
        UserAccount changed;
        try {
            changed = user.changeStatus(target, operatorId, now);
        } catch (DomainRuleException exception) {
            throw new UseCaseException(
                    ApplicationErrorCode.USER_ALREADY_SEALED, exception.getMessage());
        }
        UserAccount saved = userRepository.save(changed);
        if (target != AccountStatus.NORMAL) {
            tokenRepository.revokeAllForUser(userId, clock.instant());
        }
        if (target == AccountStatus.SEALED) {
            Instant revokeAt = clock.instant();
            roleGrantRepository.findByUserId(userId).stream()
                    .filter(grant -> grant.status().name().equals("ACTIVE"))
                    .forEach(grant -> revokeGrant(grant, revokeAt));
        }
        auditTrail.recordPermissionChange(operatorId, "ADMIN",
                "CHANGE_USER_STATUS", "user:" + userId, before, snapshot(saved));
        return saved;
    }

    /**
     * 调整用户组织和岗位。
     */
    public UserAccount transfer(Long userId, Long orgId, String post,
                                Long operatorId) {
        UserAccount user = require(userId);
        Organization organization = organizationRepository.findById(orgId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "组织不存在: " + orgId));
        if (!organization.isActive()) {
            throw new UseCaseException(
                    ApplicationErrorCode.PARAM_INVALID, "停用组织不能接收调岗用户");
        }
        Map<String, Object> before = assignmentSnapshot(user);
        UserAccount saved;
        try {
            saved = userRepository.save(user.transferTo(orgId, post, operatorId, now()));
        } catch (DomainRuleException exception) {
            throw new UseCaseException(
                    ApplicationErrorCode.USER_ALREADY_SEALED, exception.getMessage());
        }
        auditTrail.recordPermissionChange(operatorId, "ADMIN",
                "TRANSFER_USER", "user:" + userId, before, assignmentSnapshot(saved));
        return saved;
    }

    /**
     * 转换为脱敏用户视图。
     */
    @Transactional(readOnly = true)
    public com.polyauth.api.admin.vo.UserVO toView(UserAccount user) {
        String realName = crypto.maskName(crypto.decrypt(user.nameEnc()));
        String phone = user.phoneEnc() == null
                ? null : crypto.maskPhone(crypto.decrypt(user.phoneEnc()));
        com.polyauth.api.admin.vo.UserVO vo = new com.polyauth.api.admin.vo.UserVO();
        vo.setId(user.id());
        vo.setUserNo(user.userNo());
        vo.setRealName(realName);
        vo.setPhoneMasked(phone);
        vo.setOrgId(user.orgId());
        vo.setPost(user.post());
        vo.setAccountStatus(user.status().name());
        vo.setLeaveTime(user.leaveTime());
        vo.setLastLoginAt(user.lastLoginAt());
        vo.setIsDeleted(user.deleted() ? 1 : 0);
        vo.setCreatedAt(user.createdAt());
        vo.setUpdatedAt(user.updatedAt());
        return vo;
    }

    /**
     * 返回受独立权限保护的明文视图。
     */
    @Transactional(readOnly = true)
    public com.polyauth.api.admin.vo.SensitiveUserVO sensitive(Long userId) {
        UserAccount user = require(userId);
        com.polyauth.api.admin.vo.SensitiveUserVO vo =
                new com.polyauth.api.admin.vo.SensitiveUserVO();
        vo.setId(user.id());
        vo.setUserNo(user.userNo());
        vo.setRealName(crypto.decrypt(user.nameEnc()));
        vo.setPhone(user.phoneEnc() == null ? null : crypto.decrypt(user.phoneEnc()));
        return vo;
    }

    private UserAccount require(Long userId) {
        return userRepository.findById(userId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new UseCaseException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "用户不存在: " + userId));
    }

    private void revokeGrant(RoleGrant grant, Instant now) {
        grant.revoke(now);
        roleGrantRepository.save(grant);
    }

    private Map<String, Object> snapshot(UserAccount user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.id());
        result.put("accountStatus", user.status().name());
        result.put("leaveTime", user.leaveTime());
        result.put("orgId", user.orgId());
        result.put("post", user.post());
        return result;
    }

    private Map<String, Object> assignmentSnapshot(UserAccount user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.id());
        result.put("orgId", user.orgId());
        result.put("post", user.post());
        return result;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}

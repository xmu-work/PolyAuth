package com.polyauth.service.ddd.adapter.account;

import com.polyauth.infrastructure.crypto.CryptoComponent;
import com.polyauth.infrastructure.persistence.structured.entity.IamUser;
import com.polyauth.service.ddd.application.usecase.AccountManagementUseCase;
import com.polyauth.service.ddd.domain.model.DomainPage;
import com.polyauth.service.ddd.domain.model.UserAccount;
import com.polyauth.service.port.account.AccountUseCase;
import com.polyauth.service.port.account.SensitiveUserView;
import com.polyauth.service.port.account.UserView;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 用户账号用例的 DDD 实现适配器。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddAccountService implements AccountUseCase {

    private final AccountManagementUseCase useCase;
    private final CryptoComponent crypto;

    @Override
    public IamUser createUser(String userNo, String password, String realName, String phone,
                              Long orgId, String post) {
        return toEntity(useCase.create(userNo, password, realName, phone, orgId, post, null));
    }

    @Override
    public Page<IamUser> listUsers(Pageable pageable, Long orgId, String accountStatus) {
        DomainPage<UserAccount> page = useCase.page(
                pageable.getPageNumber() + 1, pageable.getPageSize(), orgId, accountStatus);
        return new PageImpl<>(
                page.items().stream().map(this::toEntity).toList(),
                pageable, page.total());
    }

    @Override
    public IamUser getUser(Long userId) {
        return toEntity(useCase.get(userId));
    }

    @Override
    public IamUser changeStatus(Long userId, String targetStatus, String reason) {
        return changeStatus(userId, targetStatus, reason, null);
    }

    @Override
    public IamUser changeStatus(Long userId, String targetStatus, String reason, Long operatorId) {
        return toEntity(useCase.changeStatus(userId, targetStatus, reason, operatorId));
    }

    @Override
    public IamUser transferUser(Long userId, Long orgId, String post, Long operatorId) {
        return toEntity(useCase.transfer(userId, orgId, post, operatorId));
    }

    @Override
    public UserView toView(IamUser user) {
        UserView view = new UserView();
        view.setId(user.getId());
        view.setUserNo(user.getUserNo());
        view.setRealName(crypto.maskName(crypto.decrypt(user.getNameEnc())));
        view.setPhoneMasked(user.getPhoneEnc() == null
                ? null : crypto.maskPhone(crypto.decrypt(user.getPhoneEnc())));
        view.setOrgId(user.getOrgId());
        view.setPost(user.getPost());
        view.setAccountStatus(user.getAccountStatus());
        view.setLeaveTime(user.getLeaveTime());
        view.setLastLoginAt(user.getLastLoginAt());
        view.setIsDeleted(user.getIsDeleted());
        view.setCreatedAt(user.getCreatedAt());
        view.setUpdatedAt(user.getUpdatedAt());
        return view;
    }

    @Override
    public SensitiveUserView getSensitiveView(Long userId) {
        com.polyauth.api.admin.vo.SensitiveUserVO sensitive = useCase.sensitive(userId);
        SensitiveUserView view = new SensitiveUserView();
        view.setId(sensitive.getId());
        view.setUserNo(sensitive.getUserNo());
        view.setRealName(sensitive.getRealName());
        view.setPhone(sensitive.getPhone());
        return view;
    }

    private IamUser toEntity(UserAccount user) {
        IamUser entity = new IamUser();
        entity.setId(user.id());
        entity.setUserNo(user.userNo());
        entity.setPasswordHash(user.passwordHash());
        entity.setNameEnc(user.nameEnc());
        entity.setNameHash(user.nameHash());
        entity.setPhoneEnc(user.phoneEnc());
        entity.setPhoneHash(user.phoneHash());
        entity.setOrgId(user.orgId());
        entity.setPost(user.post());
        entity.setAccountStatus(user.status().name());
        entity.setLeaveTime(user.leaveTime());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setIsDeleted(user.deleted() ? 1 : 0);
        entity.setCreatedAt(user.createdAt());
        entity.setUpdatedAt(user.updatedAt());
        entity.setVersion(user.version());
        entity.setCreatedBy(user.createdBy());
        entity.setUpdatedBy(user.updatedBy());
        return entity;
    }
}

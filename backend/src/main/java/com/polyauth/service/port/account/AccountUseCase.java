package com.polyauth.service.port.account;

import com.polyauth.infrastructure.persistence.structured.entity.IamUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 用户账号用例契约：结构化与 DDD 两套实现共享。
 */
public interface AccountUseCase {

    IamUser createUser(String userNo, String password, String realName, String phone, Long orgId, String post);

    Page<IamUser> listUsers(Pageable pageable, Long orgId, String accountStatus);

    IamUser getUser(Long userId);

    IamUser changeStatus(Long userId, String targetStatus, String reason);

    IamUser changeStatus(Long userId, String targetStatus, String reason, Long operatorId);

    IamUser transferUser(Long userId, Long orgId, String post, Long operatorId);

    UserView toView(IamUser user);

    SensitiveUserView getSensitiveView(Long userId);
}

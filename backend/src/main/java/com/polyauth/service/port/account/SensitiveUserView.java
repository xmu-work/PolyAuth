package com.polyauth.service.port.account;

import lombok.Data;

/**
 * 授权场景下的敏感用户信息视图。
 *
 * <p>只允许受独立功能点保护的接口返回，调用本身由统一审计留痕。
 */
@Data
public class SensitiveUserView {

    private Long id;
    private String userNo;
    private String realName;
    private String phone;
}

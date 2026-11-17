package com.iam.platform.application.datascope;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户当前生效的数据范围授权视图，供统一鉴权接口返回。
 */
@Data
@Builder
public class EffectiveDataScope {

    private Long bindingId;
    private Long roleId;
    private String scopeType;
    private Long orgId;
    private Long projectId;
    private LocalDateTime expireTime;
}

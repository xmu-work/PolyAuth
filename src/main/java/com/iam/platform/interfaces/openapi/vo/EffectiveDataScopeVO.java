package com.iam.platform.interfaces.openapi.vo;

import com.iam.platform.application.datascope.EffectiveDataScope;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放 API 数据范围响应视图。
 */
@Data
public class EffectiveDataScopeVO {

    private Long bindingId;
    private Long roleId;
    private String scopeType;
    private Long orgId;
    private Long projectId;
    private LocalDateTime expireTime;

    /**
     * 将生效数据范围转换为开放 API 视图。
     */
    public static EffectiveDataScopeVO from(EffectiveDataScope scope) {
        EffectiveDataScopeVO vo = new EffectiveDataScopeVO();
        vo.setBindingId(scope.getBindingId());
        vo.setRoleId(scope.getRoleId());
        vo.setScopeType(scope.getScopeType());
        vo.setOrgId(scope.getOrgId());
        vo.setProjectId(scope.getProjectId());
        vo.setExpireTime(scope.getExpireTime());
        return vo;
    }
}

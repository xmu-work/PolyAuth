package com.iam.platform.application.datascope;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 资源联合判定结果。
 *
 * <p>同时保留功能权限和数据权限两个子结果，便于接入方定位拒绝原因。
 */
@Data
@Builder
public class ResourceDecision {

    /** 功能权限是否通过。 */
    private boolean functionAllowed;

    /** 数据范围是否通过。 */
    private boolean dataAllowed;

    /** 功能权限和数据权限是否同时通过。 */
    private boolean allowed;

    /** 命中的角色数据范围授权 ID。 */
    private List<Long> matchedScopeIds;
}

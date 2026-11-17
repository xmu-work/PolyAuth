package com.iam.platform.interfaces.openapi.vo;

import com.iam.platform.application.datascope.ResourceDecision;
import lombok.Data;

import java.util.List;

/**
 * 资源联合鉴权响应视图。
 */
@Data
public class ResourceAuthorizationVO {

    private boolean functionAllowed;
    private boolean dataAllowed;
    private boolean allowed;
    private List<Long> matchedScopeIds;

    /**
     * 将应用层判定结果转换为开放 API 视图。
     */
    public static ResourceAuthorizationVO from(ResourceDecision decision) {
        ResourceAuthorizationVO vo = new ResourceAuthorizationVO();
        vo.setFunctionAllowed(decision.isFunctionAllowed());
        vo.setDataAllowed(decision.isDataAllowed());
        vo.setAllowed(decision.isAllowed());
        vo.setMatchedScopeIds(decision.getMatchedScopeIds());
        return vo;
    }
}

package com.iam.platform.interfaces.admin.vo;

import com.iam.platform.infrastructure.persistence.entity.IamDataScope;
import lombok.Data;

/**
 * 数据范围字典响应视图。
 */
@Data
public class DataScopeVO {

    private Long id;
    private String scopeCode;
    private String scopeName;
    private String description;
    private Integer sortNo;
    private String status;

    /**
     * 将数据范围实体转换为对外视图。
     */
    public static DataScopeVO from(IamDataScope scope) {
        DataScopeVO vo = new DataScopeVO();
        vo.setId(scope.getId());
        vo.setScopeCode(scope.getScopeCode());
        vo.setScopeName(scope.getScopeName());
        vo.setDescription(scope.getDescription());
        vo.setSortNo(scope.getSortNo());
        vo.setStatus(scope.getStatus());
        return vo;
    }
}

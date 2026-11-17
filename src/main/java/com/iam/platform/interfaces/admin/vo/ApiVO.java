package com.iam.platform.interfaces.admin.vo;

import com.iam.platform.infrastructure.persistence.entity.IamApi;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后端 API 目录响应视图。
 */
@Data
public class ApiVO {

    private Long id;
    private Long systemId;
    private Long permissionId;
    private String apiCode;
    private String apiName;
    private String apiPath;
    private String httpMethod;
    private String status;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 将 API 实体转换为 VO。
     */
    public static ApiVO from(IamApi api) {
        ApiVO vo = new ApiVO();
        vo.setId(api.getId());
        vo.setSystemId(api.getSystemId());
        vo.setPermissionId(api.getPermissionId());
        vo.setApiCode(api.getApiCode());
        vo.setApiName(api.getApiName());
        vo.setApiPath(api.getApiPath());
        vo.setHttpMethod(api.getHttpMethod());
        vo.setStatus(api.getStatus());
        vo.setIsDeleted(api.getIsDeleted());
        vo.setCreatedAt(api.getCreatedAt());
        vo.setUpdatedAt(api.getUpdatedAt());
        return vo;
    }
}

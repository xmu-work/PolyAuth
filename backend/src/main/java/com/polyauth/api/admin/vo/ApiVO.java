package com.polyauth.api.admin.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.polyauth.infrastructure.persistence.structured.entity.IamApi;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后端 API 目录响应视图。
 */
@Data
@CopyFrom(IamApi.class)
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
        return CloneFactory.copy(vo, api);
    }
}

package com.iam.platform.interfaces.admin.vo;

import com.iam.platform.infrastructure.persistence.entity.IamSystem;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接入系统安全视图，不暴露接入方密钥和内部公钥字段。
 */
@Data
public class SystemVO {

    private Long id;
    private String systemCode;
    private String systemName;
    private String callbackUrl;
    private String status;
    private String description;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 将系统实体转换为安全视图。
     */
    public static SystemVO from(IamSystem system) {
        SystemVO vo = new SystemVO();
        vo.setId(system.getId());
        vo.setSystemCode(system.getSystemCode());
        vo.setSystemName(system.getSystemName());
        vo.setCallbackUrl(system.getCallbackUrl());
        vo.setStatus(system.getStatus());
        vo.setDescription(system.getDescription());
        vo.setIsDeleted(system.getIsDeleted());
        vo.setCreatedAt(system.getCreatedAt());
        vo.setUpdatedAt(system.getUpdatedAt());
        return vo;
    }
}

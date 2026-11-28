package com.polyauth.api.admin.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.polyauth.infrastructure.persistence.structured.entity.IamSystem;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接入系统安全视图，不暴露接入方密钥和内部公钥字段。
 */
@Data
@CopyFrom(IamSystem.class)
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
        return CloneFactory.copy(vo, system);
    }
}

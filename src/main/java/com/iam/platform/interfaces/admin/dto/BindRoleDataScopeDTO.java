package com.iam.platform.interfaces.admin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 角色数据范围绑定请求。
 *
 * <p>范围类型与目标字段的跨字段约束在 API 层校验，服务层只处理组织和项目存在性等业务规则。
 */
@Data
public class BindRoleDataScopeDTO {

    @NotBlank
    @Pattern(regexp = "ALL|BRANCH|DEPT|PROJECT|SELF")
    private String scopeType;

    private Long orgId;

    private Long projectId;

    @Future
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expireTime;

    /**
     * 校验范围类型所需目标是否完整且互斥。
     */
    @AssertTrue(message = "数据范围类型与组织/项目目标不匹配")
    public boolean isTargetValid() {
        if (scopeType == null || scopeType.isBlank()) {
            return true;
        }
        return switch (scopeType.toUpperCase(Locale.ROOT)) {
            case "ALL", "SELF" -> orgId == null && projectId == null;
            case "BRANCH", "DEPT" -> orgId != null && projectId == null;
            case "PROJECT" -> projectId != null;
            default -> false;
        };
    }
}

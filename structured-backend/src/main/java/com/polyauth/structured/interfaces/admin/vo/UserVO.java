package com.polyauth.structured.interfaces.admin.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.polyauth.structured.application.account.UserView;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 API 响应视图，仅包含解密后的展示字段和脱敏电话。
 */
@Data
@CopyFrom(UserView.class)
public class UserVO {

    private Long id;
    private String userNo;
    private String realName;
    private String phoneMasked;
    private Long orgId;
    private String post;
    private String accountStatus;
    private LocalDateTime leaveTime;
    private LocalDateTime lastLoginAt;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 将应用层安全视图转换为接口响应 VO。
     */
    public static UserVO from(UserView view) {
        UserVO vo = new UserVO();
        return CloneFactory.copy(vo, view);
    }
}

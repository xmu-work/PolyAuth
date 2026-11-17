package com.iam.platform.interfaces.admin.vo;

import com.iam.platform.application.account.SensitiveUserView;
import lombok.Data;

/**
 * 敏感用户明文 API 响应视图。
 */
@Data
public class SensitiveUserVO {

    private Long id;
    private String userNo;
    private String realName;
    private String phone;

    /**
     * 将授权明文视图转换为 API VO。
     */
    public static SensitiveUserVO from(SensitiveUserView view) {
        SensitiveUserVO vo = new SensitiveUserVO();
        vo.setId(view.getId());
        vo.setUserNo(view.getUserNo());
        vo.setRealName(view.getRealName());
        vo.setPhone(view.getPhone());
        return vo;
    }
}

package com.polyauth.structured.interfaces.admin;

import com.polyauth.structured.application.account.AccountService;
import com.polyauth.structured.interfaces.admin.vo.UserVO;
import com.polyauth.structured.interfaces.admin.vo.SensitiveUserVO;
import com.polyauth.structured.common.result.PageResult;
import com.polyauth.structured.common.result.Result;
import com.polyauth.structured.infrastructure.persistence.entity.IamUser;
import com.polyauth.structured.interfaces.AuthInterceptor;
import com.polyauth.structured.interfaces.admin.dto.TransferUserDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户账号管理控制器（管理端）。
 *
 * <p>对应 API：/api/v1/users/*
 */
@Validated
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final AccountService accountService;

    /**
     * 创建用户。
     */
    @PostMapping
    public Result<UserVO> create(
            @RequestParam
            @NotBlank
            @Size(max = 32)
            String userNo,
            @RequestParam(required = false)
            @Size(max = 64)
            String password,
            @RequestParam
            @NotBlank
            @Size(max = 64)
            String realName,
            @RequestParam(required = false)
            @Size(max = 20)
            String phone,
            @RequestParam
            @NotNull
            @Positive
            Long orgId,
            @RequestParam(required = false)
            @Size(max = 64)
            String post) {
        return Result.success(UserVO.from(accountService.toView(
                accountService.createUser(userNo, password, realName, phone, orgId, post))));
    }

    /**
     * 查询用户详情。
     */
    @GetMapping("/{userId}")
    public Result<UserVO> get(
            @PathVariable
            @Positive
            Long userId) {
        return Result.success(UserVO.from(accountService.toView(accountService.getUser(userId))));
    }

    /**
     * 受独立功能点保护地查看姓名和电话明文。
     */
    @GetMapping("/{userId}/sensitive")
    public Result<SensitiveUserVO> sensitive(
            @PathVariable
            @Positive
            Long userId) {
        return Result.success(SensitiveUserVO.from(accountService.getSensitiveView(userId)));
    }

    /**
     * 分页查询用户列表。
     */
    @GetMapping
    public Result<PageResult<UserVO>> list(
            @RequestParam(defaultValue = "1")
            @Min(1)
            int page,
            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size,
            @RequestParam(required = false)
            @Positive
            Long orgId,
            @RequestParam(required = false)
            @Pattern(regexp = "NORMAL|DISABLED|SEALED|LOCKED")
            String accountStatus) {
        Page<IamUser> result = accountService.listUsers(PageRequest.of(page - 1, size), orgId, accountStatus);
        return Result.success(PageResult.of(result.getTotalElements(), page, size,
                result.getContent().stream()
                        .map(accountService::toView)
                        .map(UserVO::from)
                        .toList()));
    }

    /**
     * 变更账号状态。
     */
    @PutMapping("/{userId}/status")
    public Result<UserVO> changeStatus(
            @PathVariable
            @Positive
            Long userId,
            @RequestParam
            @NotBlank
            @Pattern(regexp = "NORMAL|DISABLED|SEALED|LOCKED")
            String targetStatus,
            @RequestParam(required = false)
            @Size(max = 255)
            String reason,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return Result.success(UserVO.from(accountService.toView(
                accountService.changeStatus(userId, targetStatus, reason, operatorId))));
    }

    /**
     * 调整用户组织归属与岗位，保留现有角色供管理员复核。
     */
    @PutMapping("/{userId}/organization")
    public Result<UserVO> transfer(
            @PathVariable
            @Positive
            Long userId,
            @Valid
            TransferUserDTO dto,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return Result.success(UserVO.from(accountService.toView(
                accountService.transferUser(userId, dto.getOrgId(), dto.getPost(), operatorId))));
    }
}

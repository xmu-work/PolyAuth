package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.usecase.AccountManagementUseCase;
import com.polyauth.oop.domain.model.DomainPage;
import com.polyauth.oop.domain.model.UserAccount;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.dto.TransferUserDTO;
import com.polyauth.oop.interfaces.security.AuthInterceptor;
import com.polyauth.oop.interfaces.vo.SensitiveUserVO;
import com.polyauth.oop.interfaces.vo.UserPageVO;
import com.polyauth.oop.interfaces.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户账号管理 HTTP 适配器。
 */
@Validated
@RestController
@RequestMapping("/users")
public class UserController {

    private final AccountManagementUseCase useCase;

    /**
     * 注入账号生命周期用例。
     */
    public UserController(AccountManagementUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ApiResult<UserVO> create(
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
            String post,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return ApiResult.success(useCase.toView(
                useCase.create(userNo, password, realName, phone, orgId, post, operatorId)));
    }

    @GetMapping("/{userId}")
    public ApiResult<UserVO> get(
            @PathVariable
            @Positive
            Long userId) {
        return ApiResult.success(useCase.toView(useCase.get(userId)));
    }

    @GetMapping("/{userId}/sensitive")
    public ApiResult<SensitiveUserVO> sensitive(
            @PathVariable
            @Positive
            Long userId) {
        return ApiResult.success(useCase.sensitive(userId));
    }

    @GetMapping
    public ApiResult<UserPageVO> list(
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
        DomainPage<UserAccount> result =
                useCase.page(page, size, orgId, accountStatus);
        List<UserVO> items = result.items().stream()
                .map(useCase::toView)
                .toList();
        return ApiResult.success(new UserPageVO(
                result.total(), result.page(), result.pageSize(), items));
    }

    @PutMapping("/{userId}/status")
    public ApiResult<UserVO> changeStatus(
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
        return ApiResult.success(useCase.toView(useCase.changeStatus(
                userId, targetStatus, reason, operatorId)));
    }

    @PutMapping("/{userId}/organization")
    public ApiResult<UserVO> transfer(
            @PathVariable
            @Positive
            Long userId,
            @Valid
            @RequestBody
            TransferUserDTO dto,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID)
            Long operatorId) {
        return ApiResult.success(useCase.toView(
                useCase.transfer(userId, dto.orgId(), dto.post(), operatorId)));
    }
}

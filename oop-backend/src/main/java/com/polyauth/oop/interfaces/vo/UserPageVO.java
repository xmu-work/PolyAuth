package com.polyauth.oop.interfaces.vo;

import java.util.List;

/**
 * 用户账号分页响应视图，避免 Controller 返回无类型 Map。
 *
 * @param total    总记录数
 * @param page     当前页码
 * @param pageSize 每页记录数
 * @param list     当前页用户视图
 */
public record UserPageVO(
        long total,
        int page,
        int pageSize,
        List<UserVO> list) {
}

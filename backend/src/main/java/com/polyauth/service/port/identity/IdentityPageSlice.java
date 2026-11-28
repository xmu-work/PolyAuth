package com.polyauth.service.port.identity;

import java.util.List;

/**
 * 统一身份分页切片。
 *
 * @param total 满足条件的总记录数
 * @param items 当前页数据
 * @param <T>   元素类型
 */
public record IdentityPageSlice<T>(long total, List<T> items) {
}

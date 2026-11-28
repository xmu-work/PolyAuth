package com.polyauth.service.ddd.domain.model;

import java.util.List;

/**
 * 领域层通用分页结果，避免仓储端口泄漏 Spring Data 类型。
 *
 * @param total    总记录数
 * @param page     当前页，从 1 开始
 * @param pageSize 每页数量
 * @param items    当前页数据
 * @param <T>      领域对象类型
 */
public record DomainPage<T>(long total, int page, int pageSize, List<T> items) {

    /**
     * 规范化分页数据，保证列表不可变。
     */
    public DomainPage {
        items = List.copyOf(items);
    }
}

package com.deng.recommend.service;

import com.deng.recommend.vo.CategoryVO;

import java.util.List;

/**
 * 商品类目服务。
 */
public interface CategoryService {

    /**
     * 查询两级类目树。
     *
     * <p>实现上采用「一次查询 + 内存分组」，避免对每个父节点各查一次库的
     * N+1 问题；本项目类目量级很小，全量加载开销可忽略。
     *
     * @return 一级类目列表，其 {@code children} 挂载二级类目
     */
    List<CategoryVO> tree();
}

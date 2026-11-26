package com.deng.recommend.service;

import com.deng.recommend.vo.CategoryVO;

import java.util.List;

/**
 * 商品类目服务。
 */
public interface CategoryService {

    /**
     * 查询类目树。
     *
     * <p>实现上采用「一次查询 + 内存分组」，避免对每个父节点各查一次库的
     * N+1 问题；本项目类目量级很小，全量加载开销可忽略。
     *
     * <p>层级深度由数据库中的 {@code parent_id} 决定，代码不假设固定两级。
     *
     * @return 一级类目列表，逐级通过 {@code children} 挂载下级
     */
    List<CategoryVO> tree();

    /**
     * 取某个类目自身 + 全部子孙类目的 ID。
     *
     * <p>商品挂在最末级类目上，所以点一级类目时必须把整棵子树的 ID 都拿来过滤，
     * 否则一级类目页会是空的。
     *
     * @param categoryId 类目 ID，可为 null
     * @return ID 集合；入参为 null 时返回空列表
     */
    List<Long> selfAndDescendantIds(Long categoryId);

    /**
     * 全量「类目 ID → 所属一级类目 ID」索引。
     *
     * <p>推荐多样性打散时，需要知道每个商品挂在哪个一级类目下。
     * 类目总量很小（几百），一次性构建远比逐条向上查便宜。
     *
     * @return 一级类目自己的 value 也是它自己
     */
    java.util.Map<Long, Long> categoryTopIndex();

    /**
     * 全量「类目 ID → 类目名称」映射（带短 TTL 缓存）。
     *
     * <p>商品列表、管理端商品分页都要把 categoryId 翻译成名称，
     * 类目是低频变更数据，没必要每次分页都全量查一遍。
     *
     * @return 不可变视图的快照，类目变更后最多一个 TTL 周期生效
     */
    java.util.Map<Long, String> categoryNameMap();

    /**
     * 主动失效类目快照缓存。
     *
     * <p>管理端新增/修改/删除类目后调用，避免名称与树结构在 TTL 内保持旧值。
     */
    void evictCaches();
}

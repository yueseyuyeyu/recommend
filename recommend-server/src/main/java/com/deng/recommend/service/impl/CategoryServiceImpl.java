package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deng.recommend.entity.Category;
import com.deng.recommend.mapper.CategoryMapper;
import com.deng.recommend.service.CategoryService;
import com.deng.recommend.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品类目服务实现。
 *
 * <p><b>层级说明</b>：类目表用 {@code parent_id} 自关联表达任意层级。
 * 当前数据是三级（一级 → 二级 → 三级），但本类不做层数假设，
 * 以后加第四级也不用改代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryVO> tree() {
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));

        // 内存中按父 ID 分组，避免逐层查库带来的 N+1 问题
        Map<Long, List<CategoryVO>> childrenByParent = new LinkedHashMap<>();
        List<CategoryVO> roots = new ArrayList<>();
        for (Category category : all) {
            CategoryVO vo = CategoryVO.from(category);
            if (category.isRoot()) {
                roots.add(vo);
            } else {
                childrenByParent
                        .computeIfAbsent(category.getParentId(), key -> new ArrayList<>())
                        .add(vo);
            }
        }

        for (CategoryVO root : roots) {
            attachChildren(root, childrenByParent);
        }
        return roots;
    }

    @Override
    public List<Long> selfAndDescendantIds(Long categoryId) {
        if (categoryId == null) {
            return new ArrayList<>();
        }

        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .select(Category::getId, Category::getParentId));

        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (Category category : all) {
            childrenByParent
                    .computeIfAbsent(category.getParentId(), key -> new ArrayList<>())
                    .add(category.getId());
        }

        // 广度优先收集自身与全部子孙，用 Set 兜住数据异常导致的环
        Set<Long> collected = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(categoryId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!collected.add(current)) {
                continue;
            }
            List<Long> children = childrenByParent.get(current);
            if (children != null) {
                queue.addAll(children);
            }
        }
        return new ArrayList<>(collected);
    }

    /**
     * 递归挂载子类目。层级深度由数据决定，代码不设上限。
     */
    private void attachChildren(CategoryVO node, Map<Long, List<CategoryVO>> childrenByParent) {
        List<CategoryVO> children = childrenByParent.get(node.getId());
        if (children == null || children.isEmpty()) {
            node.setChildren(new ArrayList<>());
            return;
        }
        node.setChildren(children);
        for (CategoryVO child : children) {
            attachChildren(child, childrenByParent);
        }
    }
}

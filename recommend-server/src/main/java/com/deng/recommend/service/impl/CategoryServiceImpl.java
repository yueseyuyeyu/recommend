package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deng.recommend.entity.Category;
import com.deng.recommend.mapper.CategoryMapper;
import com.deng.recommend.service.CategoryService;
import com.deng.recommend.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品类目服务实现。
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
            root.setChildren(childrenByParent.getOrDefault(root.getId(), new ArrayList<>()));
        }
        return roots;
    }
}

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

    /** 类目快照缓存时长：类目是低频变更数据，管理端变更后会主动失效，TTL 只是兜底 */
    private static final long SNAPSHOT_CACHE_MS = 60_000L;

    /** 类目快照：id → 实体，名称映射 / 子孙展开 / 一级类目索引都从它派生 */
    private volatile Map<Long, Category> snapshotCache;

    private volatile long snapshotCacheAt;

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

        Map<Long, Category> snapshot = snapshot();
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (Category category : snapshot.values()) {
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

    @Override
    public Map<Long, Long> categoryTopIndex() {
        Map<Long, Category> snapshot = snapshot();

        Map<Long, Long> parentByChild = new HashMap<>();
        for (Category category : snapshot.values()) {
            parentByChild.put(category.getId(), category.getParentId());
        }

        Map<Long, Long> index = new HashMap<>();
        for (Long id : parentByChild.keySet()) {
            Long current = id;
            int hops = 0;
            // 一直向上走到根；hop 上限兜住数据异常造成的环
            while (current != null && current != 0L && hops < 10) {
                Long next = parentByChild.get(current);
                if (next == null || next == 0L) {
                    break;
                }
                current = next;
                hops++;
            }
            index.put(id, current);
        }
        return index;
    }

    @Override
    public Map<Long, String> categoryNameMap() {
        Map<Long, Category> snapshot = snapshot();
        Map<Long, String> names = new HashMap<>(snapshot.size());
        for (Category category : snapshot.values()) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    @Override
    public void evictCaches() {
        snapshotCache = null;
        snapshotCacheAt = 0L;
    }

    /**
     * 取类目快照（id → 实体），带短 TTL 缓存。
     *
     * <p>推荐打散、商品筛选、名称翻译三类高频操作都建立在全量类目之上；
     * 类目表只有几十行，但查询本身每次都要打数据库，缓存收益在请求数不在行数。
     * 管理端变更类目后由 {@link #evictCaches()} 主动失效，TTL 只是兜底。
     */
    private Map<Long, Category> snapshot() {
        long now = System.currentTimeMillis();
        Map<Long, Category> cached = snapshotCache;
        if (cached != null && now - snapshotCacheAt < SNAPSHOT_CACHE_MS) {
            return cached;
        }
        Map<Long, Category> loaded = new LinkedHashMap<>();
        for (Category category : categoryMapper.selectList(null)) {
            loaded.put(category.getId(), category);
        }
        snapshotCache = loaded;
        snapshotCacheAt = now;
        return loaded;
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

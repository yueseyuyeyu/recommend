package com.deng.recommend.vo;

import com.deng.recommend.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 类目视图对象，支持两级树形结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryVO {

    private Long id;

    private String name;

    private Long parentId;

    private Integer sortOrder;

    /** 二级类目，一级类目下非空 */
    private List<CategoryVO> children;

    public static CategoryVO from(Category category) {
        return CategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParentId())
                .sortOrder(category.getSortOrder())
                .build();
    }
}

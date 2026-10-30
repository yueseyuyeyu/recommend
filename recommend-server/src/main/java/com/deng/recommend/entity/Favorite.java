package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品收藏实体，对应表 {@code favorite}。
 *
 * <p><b>设计说明</b>：收藏属于轻量关系数据，取消收藏直接物理删除，
 * 不使用其他表的 {@code is_deleted} 逻辑删除——否则反复收藏/取消会让表无限膨胀，
 * 且没有任何业务需要追溯"曾经收藏过什么"。
 *
 * <p>唯一键 {@code (user_id, product_id)} 保证同一用户对同一商品只会有一条记录，
 * 重复收藏由数据库兜底，天然幂等。
 */
@Data
@TableName("favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 商品 ID */
    private Long productId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

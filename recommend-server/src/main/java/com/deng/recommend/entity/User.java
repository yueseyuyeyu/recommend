package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deng.recommend.entity.enums.UserRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 {@code user}。
 *
 * <p><b>安全说明</b>：字段名为 {@code passwordHash} 而非 {@code password}，
 * 从命名层面杜绝明文密码落库。参考项目使用 {@code where bNo=? and bPassword=?}
 * 明文比对，本项目一律存储 BCrypt 加盐哈希。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名，唯一，4–20 位 */
    private String username;

    /** BCrypt 加盐哈希后的密码，永不明文 */
    private String passwordHash;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 默认收货地址 */
    private String address;

    /** 头像 URL */
    private String avatar;

    /** 角色码，取值见 {@link UserRole} */
    private Integer role;

    /** 状态：1-正常，0-禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 返回角色枚举，避免调用方直接操作魔法数字 */
    public UserRole getRoleEnum() {
        return UserRole.of(this.role);
    }
}

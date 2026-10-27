package com.deng.recommend.entity.enums;

import lombok.Getter;

/**
 * 用户角色枚举。
 *
 * <p>说明：参考项目使用 {@code UserType} 枚举表达角色，但构造器漏赋值 index 字段，
 * 导致 index 恒为 0。本枚举保证所有字段在构造器中完整赋值，并有单元测试覆盖。
 */
@Getter
public enum UserRole {

    /** 普通用户 */
    USER(1, "普通用户"),

    /** 管理员 */
    ADMIN(2, "管理员");

    private final int code;
    private final String name;

    UserRole(int code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按数据库存储的 code 还原枚举。
     *
     * @param code 角色码
     * @return 对应枚举，未知 code 返回 {@link #USER}
     */
    public static UserRole of(Integer code) {
        if (code == null) {
            return USER;
        }
        for (UserRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return USER;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}

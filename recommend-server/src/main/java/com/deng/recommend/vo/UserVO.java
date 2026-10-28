package com.deng.recommend.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户视图对象。
 *
 * <p><b>安全说明</b>：本类不含任何密码字段，杜绝把哈希泄露给前端。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String avatar;

    /** 角色码：1-普通用户，2-管理员 */
    private Integer role;

    /** 状态码：1-正常，0-禁用；后台用户管理需要展示与切换 */
    private Integer status;

    /** 登录成功后下发的 JWT 令牌，仅登录/注册接口返回 */
    private String token;
}

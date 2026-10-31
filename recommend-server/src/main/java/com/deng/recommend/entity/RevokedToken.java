package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已注销的令牌（黑名单），对应表 {@code revoked_token}。
 *
 * <p>JWT 是无状态的，签出去就收不回。注销时把令牌摘要记一行，
 * 鉴权过滤器命中即当作未登录，从而实现「服务端注销」。
 */
@Data
@TableName("revoked_token")
public class RevokedToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 令牌 SHA-256 摘要（十进制十六进制串），不存明文 */
    private String tokenHash;

    private Long userId;

    /** 令牌自身的过期时间；到点后这行就没有保留价值，可清理 */
    private LocalDateTime expireAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

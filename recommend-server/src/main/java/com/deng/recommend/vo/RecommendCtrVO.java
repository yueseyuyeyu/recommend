package com.deng.recommend.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推荐点击率统计。
 *
 * <p>与离线指标的分工：离线 Precision 衡量「推荐得准不准」，
 * CTR 衡量「推荐有没有被真的看见并点开」。两者背离往往说明问题在现场——
 * 比如推得准但都排在用户划不到的深位。
 */
@Data
@Builder
public class RecommendCtrVO {

    /** 统计窗口天数 */
    private int days;

    /** 窗口起点 */
    private LocalDateTime since;

    /** 曝光总数 */
    private long exposures;

    /** 曝光后被点击的次数 */
    private long clicked;

    /** 点击率：clicked / exposures，无曝光时为 0 */
    private double ctr;
}

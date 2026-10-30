package com.deng.recommend.service;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.dto.UpdateProfileRequest;
import com.deng.recommend.vo.HistoryVO;
import com.deng.recommend.vo.UserStatsVO;
import com.deng.recommend.vo.UserVO;

/**
 * 个人中心服务：资料维护、待办统计、行为足迹。
 */
public interface UserProfileService {

    /**
     * 查询当前用户资料。
     */
    UserVO getProfile(Long userId);

    /**
     * 修改当前用户资料。
     *
     * <p>只允许改昵称、邮箱、手机号、地址、头像；
     * 用户名、角色、状态不可由本人修改，防止越权提权。
     *
     * @param userId  当前用户
     * @param request 待修改字段，为 null 的字段保持原值
     * @return 修改后的资料
     */
    UserVO updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * 个人中心顶部的待办数量：购物车、待付款、待发货、待收货、待评价。
     */
    UserStatsVO stats(Long userId);

    /**
     * 行为足迹分页查询。
     *
     * @param userId   当前用户
     * @param type     {@code VIEW} 表示浏览过（含点击），{@code PURCHASE} 表示买过
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 按商品聚合后的足迹列表，按最后行为时间倒序
     */
    PageResult<HistoryVO> history(Long userId, String type, long pageNum, long pageSize);
}

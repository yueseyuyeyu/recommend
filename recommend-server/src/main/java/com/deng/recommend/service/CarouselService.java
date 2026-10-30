package com.deng.recommend.service;

import com.deng.recommend.vo.CarouselVO;

import java.util.List;

/**
 * 首页轮播图服务。
 */
public interface CarouselService {

    /**
     * 查询全部启用的轮播图，按 sortOrder 升序。
     */
    List<CarouselVO> listEnabled();
}

package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deng.recommend.entity.Carousel;
import com.deng.recommend.mapper.CarouselMapper;
import com.deng.recommend.service.CarouselService;
import com.deng.recommend.vo.CarouselVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页轮播图服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarouselServiceImpl implements CarouselService {

    private final CarouselMapper carouselMapper;

    @Override
    public List<CarouselVO> listEnabled() {
        List<Carousel> list = carouselMapper.selectList(new LambdaQueryWrapper<Carousel>()
                .eq(Carousel::getStatus, 1)
                .orderByAsc(Carousel::getSortOrder)
                .orderByAsc(Carousel::getId));

        List<CarouselVO> vos = new ArrayList<>(list.size());
        for (Carousel carousel : list) {
            vos.add(CarouselVO.from(carousel));
        }
        return vos;
    }
}

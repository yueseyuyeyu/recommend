package com.deng.recommend.controller;

import com.deng.recommend.common.Result;
import com.deng.recommend.service.CarouselService;
import com.deng.recommend.vo.CarouselVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页轮播图接口，公开访问。
 */
@RestController
@RequestMapping("/api/carousels")
@RequiredArgsConstructor
public class CarouselController {

    private final CarouselService carouselService;

    /**
     * 查询启用的轮播图列表。
     */
    @GetMapping
    public Result<List<CarouselVO>> list() {
        return Result.success(carouselService.listEnabled());
    }
}

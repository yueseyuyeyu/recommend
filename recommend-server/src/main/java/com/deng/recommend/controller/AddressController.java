package com.deng.recommend.controller;

import com.deng.recommend.common.Result;
import com.deng.recommend.config.SecurityUtils;
import com.deng.recommend.dto.AddressRequest;
import com.deng.recommend.service.AddressService;
import com.deng.recommend.vo.AddressVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址簿接口。
 *
 * <p>路径挂在 {@code /api/user/addresses} 下，与个人中心同属登录用户资源；
 * 每条操作都会校验地址归属，改别人的地址 ID 只会得到「地址不存在」。
 */
@Validated
@RestController
@RequestMapping("/api/user/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public Result<List<AddressVO>> list() {
        return Result.success(addressService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public Result<AddressVO> create(@Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public Result<AddressVO> update(@PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(SecurityUtils.currentUserId(), id);
        return Result.success();
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(SecurityUtils.currentUserId(), id);
        return Result.success();
    }
}

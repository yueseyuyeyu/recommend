package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.entity.Product;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.OrderMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.mapper.UserMapper;
import com.deng.recommend.service.AdminService;
import com.deng.recommend.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 后台管理服务实现。
 *
 * <p>商品删除走 MyBatis-Plus 逻辑删除，保留历史订单与评价的引用完整性；
 * 表数据由于用户、商品、订单、行为四张表规模有限，统计采用 count/聚合直查，
 * 避免一次拉取全表行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final BehaviorMapper behaviorMapper;

    @Override
    public ProductVO saveProduct(Product product) {
        if (product.getId() == null) {
            productMapper.insert(product);
            log.info("新增商品成功, productId={}", product.getId());
        } else {
            productMapper.updateById(product);
            log.info("更新商品成功, productId={}", product.getId());
        }
        return ProductVO.from(product);
    }

    @Override
    public void deleteProduct(Long id) {
        productMapper.deleteById(id);
        log.info("逻辑删除商品成功, productId={}", id);
    }

    @Override
    public void updateProductStatus(Long id, Integer status) {
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为 0（下架）或 1（上架）");
        }
        LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getStatus, status);
        productMapper.update(null, wrapper);
    }

    @Override
    public Map<String, Object> statistics() {
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>().select(Product::getSales));
        int totalSales = products.stream()
                .map(Product::getSales)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(null));
        data.put("productCount", productMapper.selectCount(null));
        data.put("orderCount", orderMapper.selectCount(null));
        data.put("behaviorCount", behaviorMapper.selectCount(null));
        data.put("totalSales", totalSales);
        return data;
    }
}

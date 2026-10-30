package com.deng.recommend.controller.admin;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.Result;
import com.deng.recommend.entity.Carousel;
import com.deng.recommend.entity.Category;
import com.deng.recommend.entity.Product;
import com.deng.recommend.service.AdminService;
import com.deng.recommend.service.BehaviorService;
import com.deng.recommend.vo.CarouselVO;
import com.deng.recommend.vo.OrderVO;
import com.deng.recommend.vo.ProductVO;
import com.deng.recommend.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台管理接口，全部要求 ADMIN 角色（规则见 {@code SecurityConfig}）。
 */
@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final BehaviorService behaviorService;

    /**
     * 手动刷新推荐模型缓存。
     *
     * <p>平时矩阵靠 TTL 自动过期；批量导入行为数据后想让推荐立刻反映新数据时，
     * 点一下这个接口即可，不用等过期。
     */
    @PostMapping("/recommend/refresh")
    public Result<Void> refreshRecommendModel() {
        behaviorService.evictMatrixCache();
        return Result.success();
    }

    @PostMapping("/products")
    public Result<ProductVO> createProduct(@RequestBody Product product) {
        product.setId(null);
        return Result.success(adminService.saveProduct(product));
    }    @PutMapping("/products/{id}")
    public Result<ProductVO> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        return Result.success(adminService.saveProduct(product));
    }

    @DeleteMapping("/products/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        adminService.deleteProduct(id);
        return Result.success();
    }

    @GetMapping("/products")
    public Result<PageResult<ProductVO>> pageProducts(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(adminService.pageProducts(keyword, status, pageNum, pageSize));
    }

    @PutMapping("/products/{id}/status")
    public Result<Void> updateProductStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.updateProductStatus(id, status);
        return Result.success();
    }

    @PostMapping("/categories")
    public Result<Long> saveCategory(@RequestBody Category category) {
        return Result.success(adminService.saveCategory(category));
    }

    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return Result.success();
    }

    @GetMapping("/users")
    public Result<PageResult<UserVO>> pageUsers(@RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(adminService.pageUsers(keyword, pageNum, pageSize));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.updateUserStatus(id, status);
        return Result.success();
    }

    @GetMapping("/orders")
    public Result<PageResult<OrderVO>> pageOrders(@RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(adminService.pageOrders(status, pageNum, pageSize));
    }

    @PutMapping("/orders/{orderNo}/status")
    public Result<Void> updateOrderStatus(@PathVariable String orderNo, @RequestParam Integer status) {
        adminService.updateOrderStatus(orderNo, status);
        return Result.success();
    }

    /**
     * 发货：已支付 → 已发货。
     *
     * <p>必须选配送方；物流单号留空时按配送方规则自动生成，
     * 返回值就是最终使用的单号，前端可以直接展示给管理员核对。
     */
    @PostMapping("/orders/{orderNo}/ship")
    public Result<String> ship(@PathVariable String orderNo,
                               @RequestParam(required = false) String carrier,
                               @RequestParam(required = false) String trackingNo) {
        return Result.success(adminService.ship(orderNo, carrier, trackingNo));
    }

    /**
     * 管理员确认收货：仅当发货满 N 天（默认 7 天）后才允许。
     */
    @PostMapping("/orders/{orderNo}/confirm-receipt")
    public Result<Void> confirmReceipt(@PathVariable String orderNo) {
        adminService.confirmReceipt(orderNo);
        return Result.success();
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(adminService.statistics());
    }

    /* ==================== 轮播图管理 ==================== */

    /**
     * 轮播图列表（含已停用），按 sortOrder 升序。
     */
    @GetMapping("/carousels")
    public Result<List<CarouselVO>> listCarousels() {
        return Result.success(adminService.listCarousels());
    }

    @PostMapping("/carousels")
    public Result<CarouselVO> createCarousel(@RequestBody Carousel carousel) {
        carousel.setId(null);
        return Result.success(adminService.saveCarousel(carousel));
    }

    @PutMapping("/carousels/{id}")
    public Result<CarouselVO> updateCarousel(@PathVariable Long id, @RequestBody Carousel carousel) {
        carousel.setId(id);
        return Result.success(adminService.saveCarousel(carousel));
    }

    @DeleteMapping("/carousels/{id}")
    public Result<Void> deleteCarousel(@PathVariable Long id) {
        adminService.deleteCarousel(id);
        return Result.success();
    }

    @PutMapping("/carousels/{id}/status")
    public Result<Void> updateCarouselStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.updateCarouselStatus(id, status);
        return Result.success();
    }
}

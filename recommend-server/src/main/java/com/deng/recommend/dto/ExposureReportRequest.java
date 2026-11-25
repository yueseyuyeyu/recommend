package com.deng.recommend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 推荐曝光上报请求。
 *
 * <p>前端在一次推荐列表渲染完成后调用一次，携带本次展示的商品 ID 列表。
 * 服务端据此写入曝光流水，与用户后续的 CLICK 行为关联出点击率。
 */
@Data
public class ExposureReportRequest {

    /** 场景标识，如 HOME_FEED / SIMILAR / HOT */
    @NotBlank(message = "请提供曝光场景")
    @Size(max = 32, message = "场景标识过长")
    private String scene;

    /** 本次曝光的商品 ID，按展示顺序 */
    @NotEmpty(message = "曝光列表不能为空")
    @Size(max = 50, message = "单次曝光上报不超过 50 个商品")
    private List<Long> productIds;
}

package com.deng.recommend.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deng.recommend.entity.Behavior;
import com.deng.recommend.entity.Cart;
import com.deng.recommend.entity.Category;
import com.deng.recommend.entity.Order;
import com.deng.recommend.entity.OrderItem;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.Review;
import com.deng.recommend.entity.User;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/**
 * 纯 Mockito 单测中使用 {@code LambdaQueryWrapper} 的前置初始化。
 *
 * <p>MyBatis-Plus 的 Lambda 条件构造器依赖实体的 {@code TableInfo} 缓存，
 * 没有 Spring 容器时缓存为空会抛「can not find lambda cache」。
 * 这里手工注册一次，避免每个测试类各写一遍。
 */
public final class MybatisLambdaTestSupport {

    private static boolean initialized;

    private MybatisLambdaTestSupport() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        List<Class<?>> entities = List.of(
                User.class, Category.class, Product.class, Behavior.class,
                Cart.class, Order.class, OrderItem.class, Review.class);
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
        initialized = true;
    }
}

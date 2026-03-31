package com.flashmall.seckill.service;

import com.flashmall.seckill.entity.SeckillOrder;

import java.util.List;

public interface SeckillService {

    /**
     * 执行秒杀下单。
     * 成功返回雪花订单 ID；失败抛 SeckillException。
     */
    long doSeckill(Long userId, Long productId);

    /** 按订单 ID 查询 */
    SeckillOrder getOrder(Long orderId);

    /** 按用户 ID 查询该用户所有秒杀订单 */
    List<SeckillOrder> getOrdersByUser(Long userId);

    /**
     * 预热指定商品的 Redis 库存（从 DB 加载）。
     * 启动后可调用 /api/seckill/admin/stock/warm/{productId}。
     */
    void preWarmStock(Long productId);

    /** 预热所有商品库存 */
    void preWarmAllStocks();
}

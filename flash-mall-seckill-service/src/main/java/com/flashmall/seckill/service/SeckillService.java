package com.flashmall.seckill.service;

public interface SeckillService {

    /**
     * 执行秒杀下单。
     * 成功返回雪花订单 ID；失败抛 SeckillException。
     * 同时向 outbox_messages 写入两条消息：
     *   - seckill.orders  → order-service 创建订单
     *   - seckill.deduct  → inventory-service 扣减库存
     */
    long doSeckill(Long userId, Long productId);

    /** 预热指定商品的 Redis 库存（从 DB seckill_stocks 加载） */
    void preWarmStock(Long productId);

    /** 预热所有商品库存 */
    void preWarmAllStocks();
}

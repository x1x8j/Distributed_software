package com.flashmall.product.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flashmall.product.entity.Product;
import com.flashmall.product.mapper.ProductMapper;
import com.flashmall.product.service.ProductService;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品详情服务：Redis 缓存 + 读写分离
 *
 * 读写分离：
 *   @DS("slave")  → getById  走从库（SELECT）
 *   @DS("master") → create   走主库（INSERT），写后删缓存避免读到旧数据
 *
 * 缓存穿透：不存在的 id 缓存空值标记 "__NULL__"，TTL 2 分钟。
 * 缓存击穿：分布式锁确保只有一个线程回源 DB，其余等锁后读缓存。
 * 缓存雪崩：TTL = 30 分钟 + 随机 0~10 分钟抖动，防止集中过期。
 */
@Service
public class ProductServiceImpl implements ProductService {

    private static final String CACHE_KEY_PREFIX = "product:detail:";
    private static final String LOCK_KEY_PREFIX  = "lock:product:detail:";
    private static final String NULL_SENTINEL     = "__NULL__";
    private static final long   BASE_TTL_MINUTES  = 30;
    private static final long   RAND_TTL_MINUTES  = 10;
    private static final long   NULL_TTL_MINUTES  = 2;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ProductMapper  productMapper;
    private final RedissonClient redisson;

    public ProductServiceImpl(ProductMapper productMapper, RedissonClient redisson) {
        this.productMapper = productMapper;
        this.redisson      = redisson;
    }

    // ── 读操作：路由到从库 ────────────────────────────────────────────────────
    @Override
    @DS("slave")
    public Product getById(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        RBucket<String> bucket = redisson.getBucket(cacheKey);

        // ① 查缓存
        String cached = bucket.get();
        if (cached != null) {
            return NULL_SENTINEL.equals(cached) ? null : deserialize(cached);
        }

        // ② Cache miss：加分布式锁防止缓存击穿
        RLock lock = redisson.getLock(LOCK_KEY_PREFIX + id);
        try {
            lock.lock();

            // ③ 双重检查
            cached = bucket.get();
            if (cached != null) {
                return NULL_SENTINEL.equals(cached) ? null : deserialize(cached);
            }

            // ④ 查从库
            Product product = productMapper.selectById(id);

            if (product == null) {
                // 缓存空值防穿透
                bucket.set(NULL_SENTINEL, Duration.ofMinutes(NULL_TTL_MINUTES));
                return null;
            }

            // ⑤ 写缓存，TTL 随机抖动防雪崩
            long ttl = BASE_TTL_MINUTES + ThreadLocalRandom.current().nextLong(RAND_TTL_MINUTES);
            bucket.set(serialize(product), Duration.ofMinutes(ttl));
            return product;

        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    // ── 写操作：路由到主库，写后删缓存 ─────────────────────────────────────────
    @Override
    @DS("master")
    public Product create(Product product) {
        product.setCreatedAt(LocalDateTime.now());
        productMapper.insert(product);
        // 主动删除缓存，让下次读请求从主库回源（此时主从已同步）
        redisson.getBucket(CACHE_KEY_PREFIX + product.getId()).delete();
        return product;
    }

    // ─────────────────────────────────────────────────────────────────────────
    private String serialize(Product p) {
        try { return MAPPER.writeValueAsString(p); }
        catch (Exception e) { throw new RuntimeException("序列化失败", e); }
    }

    private Product deserialize(String json) {
        try { return MAPPER.readValue(json, Product.class); }
        catch (Exception e) { throw new RuntimeException("反序列化失败", e); }
    }
}

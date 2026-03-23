package com.flashmall.product.service.impl;

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
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品详情服务，使用 Redis 缓存，处理三种缓存问题：
 *
 * 缓存穿透：对 DB 中不存在的 id，缓存空值标记 "__NULL__"，短 TTL 2 分钟，
 *           防止每次请求都打到 DB。
 *
 * 缓存击穿：热点 key 过期时，使用 Redisson 分布式锁确保只有一个线程重建缓存，
 *           其余线程等待锁释放后从缓存中读取，防止瞬间大量请求穿透到 DB。
 *
 * 缓存雪崩：每个 key 的 TTL = 基础 30 分钟 + 随机 0~10 分钟抖动，
 *           避免大量 key 同时失效，导致 DB 被瞬间压垮。
 */
@Service
public class ProductServiceImpl implements ProductService {

    // 缓存 key 前缀
    private static final String CACHE_KEY_PREFIX = "product:detail:";
    // 分布式锁 key 前缀
    private static final String LOCK_KEY_PREFIX = "lock:product:detail:";
    // 空值标记（防缓存穿透）
    private static final String NULL_SENTINEL = "__NULL__";
    // 正常缓存 TTL 基础值（分钟）
    private static final long BASE_TTL_MINUTES = 30;
    // TTL 随机抖动范围（分钟），防缓存雪崩
    private static final long RANDOM_TTL_RANGE_MINUTES = 10;
    // 空值缓存 TTL（分钟），防缓存穿透
    private static final long NULL_TTL_MINUTES = 2;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ProductMapper productMapper;
    private final RedissonClient redisson;

    public ProductServiceImpl(ProductMapper productMapper, RedissonClient redisson) {
        this.productMapper = productMapper;
        this.redisson = redisson;
    }

    @Override
    public Product getById(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        RBucket<String> bucket = redisson.getBucket(cacheKey);

        // ① 查缓存
        String cached = bucket.get();
        if (cached != null) {
            // 命中空值标记 → 缓存穿透保护，直接返回 null
            if (NULL_SENTINEL.equals(cached)) {
                return null;
            }
            return deserialize(cached);
        }

        // ② Cache miss：加分布式锁，防止缓存击穿（多线程同时重建缓存）
        RLock lock = redisson.getLock(LOCK_KEY_PREFIX + id);
        try {
            lock.lock();

            // ③ 双重检查：拿到锁后再查一次缓存，可能已被其他线程写入
            cached = bucket.get();
            if (cached != null) {
                if (NULL_SENTINEL.equals(cached)) return null;
                return deserialize(cached);
            }

            // ④ 查数据库
            Product product = productMapper.selectById(id);

            if (product == null) {
                // DB 中也不存在 → 缓存空值，防止缓存穿透
                bucket.set(NULL_SENTINEL, Duration.ofMinutes(NULL_TTL_MINUTES));
                return null;
            }

            // ⑤ 写缓存，TTL 加随机抖动，防止缓存雪崩
            long ttl = BASE_TTL_MINUTES + ThreadLocalRandom.current().nextLong(RANDOM_TTL_RANGE_MINUTES);
            bucket.set(serialize(product), Duration.ofMinutes(ttl));
            return product;

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String serialize(Product product) {
        try {
            return MAPPER.writeValueAsString(product);
        } catch (Exception e) {
            throw new RuntimeException("Product JSON序列化失败", e);
        }
    }

    private Product deserialize(String json) {
        try {
            return MAPPER.readValue(json, Product.class);
        } catch (Exception e) {
            throw new RuntimeException("Product JSON反序列化失败", e);
        }
    }
}

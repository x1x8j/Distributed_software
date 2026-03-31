package com.flashmall.seckill.util;

import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器
 *
 * 结构（63 位有效位）：
 *   41 位毫秒时间戳 | 10 位机器ID | 12 位序列号
 *
 * 理论 QPS：单机 4096 * 1000 = 409.6 万/秒
 */
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH           = 1704067200000L; // 2024-01-01 00:00:00 UTC
    private static final long WORKER_ID_BITS  = 10L;
    private static final long SEQUENCE_BITS   = 12L;

    private static final long MAX_WORKER_ID   = ~(-1L << WORKER_ID_BITS);  // 1023
    private static final long MAX_SEQUENCE    = ~(-1L << SEQUENCE_BITS);   // 4095

    private static final long WORKER_SHIFT    = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence      = 0L;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                "workerId must be 0–" + MAX_WORKER_ID + ", got: " + workerId);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long ts = System.currentTimeMillis();

        if (ts < lastTimestamp) {
            throw new RuntimeException("系统时钟回拨，拒绝生成 ID");
        }
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                ts = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = ts;
        return ((ts - EPOCH) << TIMESTAMP_SHIFT)
             | (workerId    << WORKER_SHIFT)
             | sequence;
    }

    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) ts = System.currentTimeMillis();
        return ts;
    }
}

package com.flashmall.seckill.util;

/**
 * 雪花算法 ID 生成器（支持基因算法，纯 POJO，由 SnowflakeConfig @Bean 注入）
 *
 * ── 普通模式（63 位）────────────────────────────────────────────────────────
 *   [41位时间戳 | 10位机器ID | 12位序列号]
 *   理论 QPS：4096 * 1000 = 409.6 万/秒
 *
 * ── 基因模式（63 位，分库分表专用）──────────────────────────────────────────
 *   [41位时间戳 | 10位机器ID | 11位序列号 | 1位基因]
 *   基因 = userId % 2，嵌入最低位，保证：
 *     id % 2  = userId % 2  →  路由到同一分库（ShardingSphere 按 id 路由）
 *     id % 4  ∈ {0,2}（偶数用户）或 {1,3}（奇数用户）→ 分表均匀
 *   理论 QPS：2048 * 1000 = 204.8 万/秒（序列位少 1 位）
 */
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC

    // ── 普通模式常量 ──────────────────────────────────────────────────────────
    private static final long WORKER_ID_BITS  = 10L;
    private static final long SEQUENCE_BITS   = 12L;
    private static final long MAX_WORKER_ID   = ~(-1L << WORKER_ID_BITS);  // 1023
    private static final long MAX_SEQUENCE    = ~(-1L << SEQUENCE_BITS);   // 4095
    private static final long WORKER_SHIFT    = SEQUENCE_BITS;             // 12
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS; // 22

    // ── 基因模式常量（序列号缩短 1 位让出基因位）──────────────────────────────
    private static final long GENE_BITS          = 1L;
    private static final long GENE_SEQ_BITS      = 11L;   // 11 = 12 - 1
    private static final long GENE_MAX_SEQ       = ~(-1L << GENE_SEQ_BITS); // 2047
    private static final long GENE_SEQ_SHIFT     = GENE_BITS;               // 1
    private static final long GENE_WORKER_SHIFT  = GENE_BITS + GENE_SEQ_BITS;   // 12
    private static final long GENE_TS_SHIFT      = GENE_BITS + GENE_SEQ_BITS + WORKER_ID_BITS; // 22

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

    /** 普通雪花 ID（不含基因） */
    public synchronized long nextId() {
        long ts = tick();
        lastTimestamp = ts;
        return ((ts - EPOCH) << TIMESTAMP_SHIFT)
             | (workerId    << WORKER_SHIFT)
             | sequence;
    }

    /**
     * 基因雪花 ID（含分库基因）
     * 保证：id % 2 == userId % 2，ShardingSphere 可以用 id 单键路由到正确分库分表。
     */
    public synchronized long nextId(long userId) {
        long gene = userId & 1L;  // userId % 2，嵌入最低位
        long ts   = tickGene();
        lastTimestamp = ts;
        return ((ts - EPOCH) << GENE_TS_SHIFT)
             | (workerId    << GENE_WORKER_SHIFT)
             | (sequence    << GENE_SEQ_SHIFT)
             | gene;
    }

    // ─────────────────────────────────────────────────────────────────────────
    private long tick() {
        long ts = System.currentTimeMillis();
        if (ts < lastTimestamp) throw new RuntimeException("系统时钟回拨，拒绝生成 ID");
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) ts = waitNextMillis(lastTimestamp);
        } else {
            sequence = 0L;
        }
        return ts;
    }

    private long tickGene() {
        long ts = System.currentTimeMillis();
        if (ts < lastTimestamp) throw new RuntimeException("系统时钟回拨，拒绝生成 ID");
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & GENE_MAX_SEQ;
            if (sequence == 0) ts = waitNextMillis(lastTimestamp);
        } else {
            sequence = 0L;
        }
        return ts;
    }

    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) ts = System.currentTimeMillis();
        return ts;
    }
}

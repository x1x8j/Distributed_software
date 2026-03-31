-- ============================================================
-- 分库分表初始化脚本（由 mysql-master 启动时执行）
-- 创建 2 个分库，每库 4 张分表，共 8 张物理表
--
-- 分片规则（与 shardingsphere.yaml 保持一致）：
--   分库：id % 2  → flashmall_0 / flashmall_1
--   分表：id % 4  → seckill_orders_0 ~ seckill_orders_3
-- ============================================================

-- ── 分库 0（偶数用户） ──────────────────────────────────────
CREATE DATABASE IF NOT EXISTS `flashmall_0` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `flashmall_0`.`seckill_orders_0` (
  `id`         BIGINT   NOT NULL             COMMENT '基因雪花订单ID（id%2=userId%2）',
  `user_id`    BIGINT   NOT NULL,
  `product_id` BIGINT   NOT NULL,
  `quantity`   INT      NOT NULL DEFAULT 1,
  `status`     TINYINT  NOT NULL DEFAULT 0   COMMENT '0=处理中 1=成功 2=库存不足',
  `created_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `flashmall_0`.`seckill_orders_1` LIKE `flashmall_0`.`seckill_orders_0`;
CREATE TABLE IF NOT EXISTS `flashmall_0`.`seckill_orders_2` LIKE `flashmall_0`.`seckill_orders_0`;
CREATE TABLE IF NOT EXISTS `flashmall_0`.`seckill_orders_3` LIKE `flashmall_0`.`seckill_orders_0`;

-- ── 分库 1（奇数用户） ──────────────────────────────────────
CREATE DATABASE IF NOT EXISTS `flashmall_1` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `flashmall_1`.`seckill_orders_0` LIKE `flashmall_0`.`seckill_orders_0`;
CREATE TABLE IF NOT EXISTS `flashmall_1`.`seckill_orders_1` LIKE `flashmall_0`.`seckill_orders_0`;
CREATE TABLE IF NOT EXISTS `flashmall_1`.`seckill_orders_2` LIKE `flashmall_0`.`seckill_orders_0`;
CREATE TABLE IF NOT EXISTS `flashmall_1`.`seckill_orders_3` LIKE `flashmall_0`.`seckill_orders_0`;

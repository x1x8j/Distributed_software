-- 秒杀库存表（与 products.stock 独立，专用于秒杀场景）
CREATE TABLE IF NOT EXISTS `seckill_stocks` (
  `product_id` BIGINT NOT NULL,
  `stock`      INT    NOT NULL DEFAULT 0,
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 秒杀订单表
CREATE TABLE IF NOT EXISTS `seckill_orders` (
  `id`         BIGINT   NOT NULL             COMMENT '雪花算法订单ID',
  `user_id`    BIGINT   NOT NULL,
  `product_id` BIGINT   NOT NULL,
  `quantity`   INT      NOT NULL DEFAULT 1,
  `status`     TINYINT  NOT NULL DEFAULT 0   COMMENT '0=处理中 1=成功 2=库存不足',
  `created_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`) COMMENT '幂等约束：同用户同商品只能秒杀一次',
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化秒杀库存（对应 products 表的前三条商品）
INSERT INTO `seckill_stocks` (`product_id`, `stock`) VALUES
  (1, 100),
  (2,  50),
  (3, 200)
ON DUPLICATE KEY UPDATE `stock` = VALUES(`stock`);

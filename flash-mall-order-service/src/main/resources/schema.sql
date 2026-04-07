-- 订单服务独立数据库：flashmall_order
CREATE DATABASE IF NOT EXISTS `flashmall_order` DEFAULT CHARSET utf8mb4;
USE `flashmall_order`;

-- 订单主表
CREATE TABLE IF NOT EXISTS `orders` (
  `id`         BIGINT       NOT NULL               COMMENT '雪花算法订单ID（来自 seckill-service）',
  `user_id`    BIGINT       NOT NULL,
  `product_id` BIGINT       NOT NULL,
  `quantity`   INT          NOT NULL DEFAULT 1,
  `amount`     DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '订单金额（可由商品价格计算）',
  `status`     TINYINT      NOT NULL DEFAULT 0
    COMMENT '0=待确认库存 1=待支付 2=已支付 3=库存不足已取消 4=支付失败',
  `created_at` DATETIME     DEFAULT NOW(),
  `updated_at` DATETIME     DEFAULT NOW() ON UPDATE NOW(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`) COMMENT '幂等：同用户同商品只能有一笔有效订单',
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消费幂等表：防止 Kafka 重复投递导致重复处理
CREATE TABLE IF NOT EXISTS `consumed_messages` (
  `message_id`  VARCHAR(64) NOT NULL COMMENT 'Kafka 消息唯一ID',
  `consumed_at` DATETIME    DEFAULT NOW(),
  PRIMARY KEY (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

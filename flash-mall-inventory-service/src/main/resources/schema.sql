-- 库存服务独立数据库：flashmall_inventory
CREATE DATABASE IF NOT EXISTS `flashmall_inventory` DEFAULT CHARSET utf8mb4;
USE `flashmall_inventory`;

-- 商品库存表
CREATE TABLE IF NOT EXISTS `inventory` (
  `product_id` BIGINT      NOT NULL COMMENT '商品ID，与 product-service 的 products.id 对应',
  `stock`      INT         NOT NULL DEFAULT 0 COMMENT '库存数量',
  `version`    INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `updated_at` DATETIME    DEFAULT NOW() ON UPDATE NOW(),
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消费幂等表：防止 Kafka 重复投递导致重复扣库存
CREATE TABLE IF NOT EXISTS `consumed_messages` (
  `message_id`  VARCHAR(64) NOT NULL COMMENT 'Kafka 消息唯一ID（outbox.messageId）',
  `consumed_at` DATETIME    DEFAULT NOW(),
  PRIMARY KEY (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化库存数据（与 seckill_stocks 独立，inventory-service 自己维护）
INSERT INTO `inventory` (`product_id`, `stock`) VALUES
  (1, 100),
  (2,  50),
  (3, 200)
ON DUPLICATE KEY UPDATE `stock` = VALUES(`stock`);

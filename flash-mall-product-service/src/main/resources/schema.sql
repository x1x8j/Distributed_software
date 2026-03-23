-- products table for product-service
CREATE TABLE IF NOT EXISTS `products` (
  `id`          bigint          NOT NULL AUTO_INCREMENT,
  `name`        varchar(200)    NOT NULL,
  `description` text,
  `price`       decimal(10, 2)  NOT NULL,
  `stock`       int             NOT NULL DEFAULT 0,
  `created_at`  datetime        DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化测试数据
INSERT INTO `products` (`name`, `description`, `price`, `stock`, `created_at`) VALUES
('iPhone 15 Pro',  'Apple 旗舰智能手机',      7999.00, 100, NOW()),
('MacBook Pro M3', '搭载 Apple Silicon 专业笔记本', 15999.00,  50, NOW()),
('AirPods Pro',    '主动降噪无线耳机',         1799.00, 200, NOW());

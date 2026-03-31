# Flash Mall 数据库设计文档

## 一、MySQL 数据库

系统共使用 3 个 MySQL 数据库：
- `flashmall` —— 主业务库（用户、商品、秒杀库存）
- `flashmall_0` —— 秒杀订单分库（偶数用户）
- `flashmall_1` —— 秒杀订单分库（奇数用户）

---

### 1. 用户表（`flashmall.users`）

| 字段名 | 数据类型 | 描述 |
|--------|----------|------|
| id | BIGINT AUTO_INCREMENT | 主键，用户ID |
| username | VARCHAR(64) | 用户名（唯一索引） |
| password | VARCHAR(255) | 加密密码 |
| email | VARCHAR(128) | 邮箱 |
| phone | VARCHAR(20) | 手机号 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：`UNIQUE KEY uk_username (username)`

---

### 2. 商品表（`flashmall.products`）

| 字段名 | 数据类型 | 描述 |
|--------|----------|------|
| id | BIGINT AUTO_INCREMENT | 主键，商品ID |
| name | VARCHAR(255) | 商品名称 |
| description | TEXT | 商品描述 |
| price | DECIMAL(10,2) | 商品价格 |
| stock | INT | 库存数量 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

> **读写分离**：查询走 MySQL Slave（`@DS("slave")`），写入走 MySQL Master（`@DS("master")`）

---

### 3. 秒杀库存表（`flashmall.seckill_stocks`）

| 字段名 | 数据类型 | 描述 |
|--------|----------|------|
| product_id | BIGINT | 主键，秒杀商品ID |
| stock | INT | 秒杀库存数量 |

> 此表为非分片表，由 ShardingSphere 路由到 `ds_main`（flashmall 库）

初始化数据：
```sql
INSERT INTO seckill_stocks VALUES (1001, 100), (1002, 50), (1003, 200);
```

---

### 4. 秒杀订单表（分库分表）

#### 分片策略

| 维度 | 规则 | 结果 |
|------|------|------|
| 分库（2库） | `id % 2` | `flashmall_0`（偶数） / `flashmall_1`（奇数） |
| 分表（4表） | `id % 4` | `seckill_orders_0` ~ `seckill_orders_3` |

物理表共 8 张：`flashmall_0.seckill_orders_{0..3}` + `flashmall_1.seckill_orders_{0..3}`

#### 基因算法

雪花 ID 最低位嵌入 `userId % 2`：
- `id % 2 = userId % 2`
- 保证所有 `WHERE id=?` 查询（含订单状态更新）都能精准路由，无需广播查询

#### 表结构（每张物理表相同）

| 字段名 | 数据类型 | 描述 |
|--------|----------|------|
| id | BIGINT | 主键，基因雪花订单ID |
| user_id | BIGINT | 用户ID |
| product_id | BIGINT | 秒杀商品ID |
| quantity | INT DEFAULT 1 | 购买数量 |
| status | TINYINT DEFAULT 0 | 0=处理中 1=成功 2=库存不足 |
| created_at | DATETIME | 下单时间 |

索引：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_user_product (user_id, product_id)` —— DB 层幂等兜底
- `KEY idx_user_id (user_id)`

---

## 二、Redis 数据结构

Redis 地址：`redis:6379`（无密码）

| Key 模式 | 类型 | 含义 | TTL |
|----------|------|------|-----|
| `product:cache:{id}` | String (JSON) | 商品详情缓存 | 30~40min（随机抖动防雪崩） |
| `product:lock:{id}` | String | 商品缓存重建分布式锁（Redisson） | 自动释放 |
| `seckill:stock:{productId}` | String (Int) | 秒杀商品 Redis 库存 | 永久（预热写入） |
| `seckill:done:{userId}:{productId}` | String | 幂等标记，防重复秒杀 | 1h |

**缓存特殊值**：
- `"__NULL__"` —— 缓存穿透占位符（商品不存在时写入，TTL 60s）

---

## 三、Kafka Topics

| Topic | 生产者 | 消费者 | 描述 |
|-------|--------|--------|------|
| `seckill-orders` | SeckillController | OrderConsumer | 秒杀订单消息，削峰填谷 |

**消息格式**（JSON）：
```json
{
  "orderId": 4611686018427387904,
  "userId": 42,
  "productId": 1001,
  "quantity": 1,
  "timestamp": 1717228801000
}
```

**消费者行为**：
1. INSERT `seckill_orders`（DuplicateKeyException 视为幂等，跳过）
2. `UPDATE seckill_stocks SET stock=stock-qty WHERE product_id=? AND stock>=qty`（DB 层防超卖）
3. 成功则更新 status=1，库存不足则 status=2

---

## 四、数据库与服务映射

| 服务 | 数据库 | 访问方式 |
|------|--------|----------|
| user-service | flashmall | 直连 mysql-master:3306 |
| product-service | flashmall | 读写分离（master写/slave读） |
| seckill-service | flashmall（seckill_stocks）| ShardingSphere ds_main |
| seckill-service | flashmall_0 / flashmall_1（seckill_orders）| ShardingSphere 分库分表 |

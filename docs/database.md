# Flash Mall 数据库设计文档

## 一、数据库总览

当前项目使用 5 个 MySQL 数据库：

| 数据库 | 用途 |
|--------|------|
| `flashmall` | 用户、商品、秒杀库存、Outbox 等主业务表 |
| `flashmall_0` | ShardingSphere 分片库（秒杀订单分片） |
| `flashmall_1` | ShardingSphere 分片库（秒杀订单分片） |
| `flashmall_order` | order-service 独立库（订单聚合状态） |
| `flashmall_inventory` | inventory-service 独立库（真实库存） |

---

## 二、各服务核心表

### 1) user-service（`flashmall`）

#### `users`
- 用户注册与登录数据。
- 主键：`id`
- 关键字段：`username/password/email/phone`

### 2) product-service（`flashmall`）

#### `products`
- 商品基础信息与库存（商品服务视角）。
- 主键：`id`

> product-service 读写分离：读从库、写主库。

### 3) seckill-service（`flashmall` + `flashmall_0/1`）

#### `seckill_stocks`（`flashmall`）
- 秒杀活动库存源数据，预热到 Redis。
- 主键：`product_id`

#### `outbox_messages`（`flashmall`）
- 本地消息表（Outbox Pattern）。
- 同事务保存业务意图，后续异步投递 Kafka。
- 关键字段：
  - `message_id`（唯一）
  - `topic`
  - `payload`
  - `status`：`0=PENDING` / `1=SENT` / `2=FAILED`
  - `retry_count`

#### `consumed_messages`（`flashmall`）
- seckill 侧消费幂等表（历史兼容，当前跨服务主链路主要在 order/inventory 使用该模式）。

#### `seckill_orders_*`（`flashmall_0/1`）
- 分库分表秒杀订单（ShardingSphere）。
- 分库：`id % 2`；分表：`id % 4`
- 物理表：`flashmall_0.seckill_orders_0..3` + `flashmall_1.seckill_orders_0..3`

---

### 4) order-service（`flashmall_order`）

#### `orders`
- 订单主状态表（评审重点）。
- 主键：`id`（雪花订单号）
- 关键字段：`user_id/product_id/quantity/amount/status`
- 状态枚举：
  - `0` 待确认库存
  - `1` 待支付
  - `2` 已支付
  - `3` 库存不足已取消
  - `4` 支付失败

#### `consumed_messages`
- Kafka 消费幂等表，防止重复消费导致重复更新状态。

---

### 5) inventory-service（`flashmall_inventory`）

#### `inventory`
- 真实库存表（库存服务权威数据）。
- 主键：`product_id`
- 关键字段：`stock/version/updated_at`

#### `consumed_messages`
- Kafka 消费幂等表，防止重复扣库存。

---

## 三、Redis 关键数据结构

Redis 地址：`redis:6379`

| Key | 含义 | TTL |
|-----|------|-----|
| `product:detail:{id}` | 商品详情缓存 | 30min + 0~10min 随机抖动 |
| `lock:product:detail:{id}` | 商品缓存重建锁 | 锁自动释放 |
| `seckill:stock:{productId}` | 秒杀 Redis 预扣库存 | 持久（预热写入） |
| `seckill:done:{userId}:{productId}` | 秒杀幂等标记 | 1h |
| `__NULL__` | 穿透空值占位 | 2min |

---

## 四、Kafka Topics 与一致性流转

| Topic | 生产者 | 消费者 | 作用 |
|-------|--------|--------|------|
| `seckill.orders` | seckill-service OutboxPoller | order-service OrderCreateConsumer | 创建订单（status=0） |
| `seckill.deduct` | seckill-service OutboxPoller | inventory-service InventoryConsumer | 扣减真实库存 |
| `inventory.result` | inventory-service | order-service InventoryResultConsumer | 回传库存扣减结果，更新订单状态为1或3 |
| `order.payment` | order-service `/pay` 接口 | order-service PaymentConsumer | 支付后更新订单状态为2 |

---

## 五、中期检查可讲的一致性要点

1. **下单+扣库存一致性**：
   - Redis 预扣提升并发能力
   - Outbox 保证消息不丢
   - inventory/result 回传驱动订单状态闭环

2. **支付+订单状态一致性**：
   - `/pay` 不直接改库，只发消息
   - PaymentConsumer 幂等消费后改状态

3. **幂等保障**：
   - Redis `SETNX`（秒杀限购）
   - MySQL 唯一索引（业务幂等）
   - `consumed_messages`（消息幂等）

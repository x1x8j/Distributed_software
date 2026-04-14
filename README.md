# Flash Mall - 高并发秒杀电商平台

基于 Spring Boot 3 的分布式秒杀系统：
- 容器化部署（Docker Compose）
- Nginx 负载均衡与动静分离
- Redis 高并发缓存与预扣库存
- Kafka 异步削峰
- MySQL 主从读写分离
- 分布式事务一致性（消息最终一致性）
- 分库分表（ShardingSphere-JDBC + 基因雪花 ID）

## 当前架构

```
Nginx(80)
  ├─ /api/users/**      -> user-service (8081/8082)
  ├─ /api/products/**   -> product-service (8083/8084)
  ├─ /api/seckill/**    -> seckill-service (8085/8086)
  ├─ /api/orders/**     -> order-service (8088)
  └─ /api/inventory/**  -> inventory-service (8087)

基础设施
  - MySQL Master:3306 / Slave:3307
  - Redis:6379
  - Kafka(KRaft):9092
```

## 微服务职责

| 服务 | 端口 | 职责 |
|------|------|------|
| user-service | 8081/8082 | 用户注册、登录、健康检查 |
| product-service | 8083/8084 | 商品查询/创建、读写分离、Redis 缓存 |
| seckill-service | 8085/8086 | 秒杀入口、Redis 预扣、Outbox 双消息投递 |
| order-service | 8088 | 建单、订单查询、`/pay` 模拟支付 |
| inventory-service | 8087 | 消费扣库存消息、库存查询 |

## 一致性方案（本项目实现）

### 1) 下单 + 库存扣减一致性
采用**消息最终一致性**：
1. seckill-service 秒杀成功后，同事务写入 Outbox 两条消息：
   - `seckill.orders`（给 order-service 建单）
   - `seckill.deduct`（给 inventory-service 扣库存）
2. OutboxPoller 定时扫描 PENDING 消息并投递 Kafka
3. inventory-service 扣减库存后发送 `inventory.result`
4. order-service 消费结果更新订单状态：
   - 成功 -> `status=1`（待支付）
   - 失败 -> `status=3`（库存不足取消）

### 2) 订单支付 + 订单状态更新一致性
1. 调用 `POST /api/orders/{orderId}/pay`
2. order-service 发送 `order.payment` 消息
3. PaymentConsumer 幂等消费后更新订单状态为 `status=2`（已支付）

## 秒杀防超卖与限购

- **限购/幂等**：`SETNX seckill:done:{userId}:{productId}`（TTL 1h）
- **防超卖（Redis 层）**：Lua 原子 `GET + DECR`
- **防超卖（DB 层）**：`UPDATE ... WHERE stock >= qty`
- **消费幂等**：`consumed_messages` 表

## 快速启动

```bash
# 1) 构建
mvn clean package -DskipTests

# 2) 启动
docker-compose up -d --build

# 3) 查看状态
docker-compose ps
```

## 中期检查演示脚本（可直接复制）

### Step 1: 健康检查
```bash
curl http://localhost/api/seckill/health
curl http://localhost/api/orders/health
curl http://localhost/api/inventory/health
```
预期：均返回 OK 文本。

### Step 2: 预热秒杀库存
```bash
curl -X POST http://localhost/api/seckill/admin/stock/warm/all
```
预期：返回“所有商品库存预热完成”。

### Step 3: 发起秒杀
```bash
curl -X POST http://localhost/api/seckill/1 -H "X-User-Id: 42"
```
预期：HTTP 202，返回 `orderId` 和 `queryUrl`。

### Step 4: 查询订单状态（异步）
```bash
curl http://localhost/api/orders/{orderId}
```
预期状态：
- 初始 `0`（待确认库存）
- 随后 `1`（待支付）或 `3`（库存不足取消）

### Step 5: 查询库存
```bash
curl http://localhost/api/inventory/1
```
预期：库存已减少。

### Step 6: 模拟支付
```bash
curl -X POST http://localhost/api/orders/{orderId}/pay -H "X-User-Id: 42"
curl http://localhost/api/orders/{orderId}
```
预期：最终 `status=2`（已支付）。

## 目录结构（核心）

```
flash-mall/
├── flash-mall-user-service/
├── flash-mall-product-service/
├── flash-mall-seckill-service/
├── flash-mall-order-service/
├── flash-mall-inventory-service/
├── nginx/
├── docs/
└── docker-compose.yml
```

详细接口见 [docs/API.md](docs/API.md)；数据库见 [docs/database.md](docs/database.md)。
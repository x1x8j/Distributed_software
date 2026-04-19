# Flash Mall 技术栈文档

## 1. 语言与运行时

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 (LTS) | 所有微服务统一语言 |
| Eclipse Temurin JRE | 17-alpine | 服务容器运行时镜像 |

---

## 2. 应用框架

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.4 | 微服务基础框架 |
| Spring Cloud Gateway | 4.1.x | 统一网关路由、过滤、流量治理入口 |
| Spring Cloud Alibaba Nacos | 2023.0.1.0 | 服务注册发现、配置中心、动态刷新 |
| Sentinel | 1.8.6（Dashboard） | 限流、熔断、降级治理 |

---

## 3. 数据库技术

| 技术 | 版本 | 说明 |
|------|------|------|
| MySQL | 8.0 (bitnami) | 关系型数据库 |
| MySQL 主从 | bitnami 自动配置 | 主写从读（product-service） |
| ShardingSphere-JDBC | 5.4.1 | 秒杀订单分库分表 |

**分片规则（seckill_orders）**：
- 分库：`id % 2` → `flashmall_0 / flashmall_1`
- 分表：`id % 4` → `seckill_orders_0..3`

---

## 4. 缓存与高并发控制

| 技术 | 版本 | 说明 |
|------|------|------|
| Redis | 7-alpine | 缓存、幂等、秒杀预扣库存 |
| Redisson | 3.27.0 | 分布式锁、Lua 执行、RBucket 等 |

**缓存策略（实际实现）**：
- 缓存穿透：`__NULL__` 占位（TTL 2min）
- 缓存击穿：分布式锁 + double-check
- 缓存雪崩：`30min + 0~10min` 抖动

**秒杀关键键**：
- `seckill:stock:{productId}`
- `seckill:done:{userId}:{productId}`

---

## 5. 消息队列与一致性

| 技术 | 版本 | 说明 |
|------|------|------|
| Kafka | 3.7 (KRaft) | 异步削峰与服务间解耦 |

### Topic 设计
- `seckill.orders`：seckill → order（建单）
- `seckill.deduct`：seckill → inventory（扣库存）
- `inventory.result`：inventory → order（库存结果回传）
- `order.payment`：order `/pay` → order PaymentConsumer（支付状态更新）

### 一致性方案
- **下单 + 扣库存**：Outbox + Kafka 最终一致性
- **支付 + 状态更新**：消息驱动 + 幂等消费

---

## 6. 负载均衡与网关

| 技术 | 说明 |
|------|------|
| Nginx | 外层入口、静态资源与反向代理（转发到 Gateway） |
| Spring Cloud Gateway | 内层网关，基于服务发现路由到各微服务 |

### 负载策略文件
- `nginx/upstreams_rr.conf`：轮询
- `nginx/upstreams_least.conf`：最少连接
- `nginx/upstreams_iphash.conf`：IP Hash

### 路由规则（当前）
- `/api/**` -> `gateway_pool`（Nginx）
- Gateway 再按服务路由：
  - `/api/users/**` -> `flash-mall-user-service`
  - `/api/products/**` -> `flash-mall-product-service`
  - `/api/seckill/**` -> `flash-mall-seckill-service`
  - `/api/orders/**` -> `flash-mall-order-service`
  - `/api/inventory/**` -> `flash-mall-inventory-service`

---

## 7. 容器化与编排

| 技术 | 说明 |
|------|------|
| Docker | 单服务容器化 |
| Docker Compose | 全链路一键编排 |

编排服务包含：
- mysql-master / mysql-slave
- redis
- kafka
- nacos
- sentinel-dashboard
- user-service x2
- product-service x2
- seckill-service x2
- order-service x1
- inventory-service x1
- gateway
- nginx

---

## 8. 分布式 ID

| 技术 | 说明 |
|------|------|
| 自研 SnowflakeIdGenerator | 全局唯一订单号 |

基因雪花模式：
```
[41位时间戳 | 10位workerId | 11位序列号 | 1位基因]
```
最低位基因来自 `userId & 1`，保证 `orderId % 2 = userId % 2`，用于分片精准路由。

---

## 9. 压测与构建

| 技术 | 说明 |
|------|------|
| JMeter 5.5 | 高并发接口压测（`load/flash_mall_test.jmx`） |
| Maven | 多模块构建（`mvn clean package -DskipTests`） |
| spring-boot-maven-plugin | 打包可执行 Fat JAR |

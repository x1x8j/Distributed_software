# Flash Mall - 高并发秒杀电商平台

基于 Spring Boot 3 的分布式高并发秒杀系统，集成了完整的微服务架构、缓存、消息队列、分库分表等核心技术。

## 系统架构

```
         ┌─────────────────────────────────────────────────┐
         │                    Nginx (80)                    │
         │  /api/users/ → backend_pool                      │
         │  /api/products/ → product_pool                   │
         │  /api/seckill/ → seckill_pool                    │
         │  /static/ → 静态资源（7d 缓存）                    │
         └──────┬──────────────┬───────────────┬────────────┘
                │              │               │
        ┌───────┴──┐   ┌───────┴──┐   ┌───────┴──┐
        │backend1/2│   │product1/2│   │seckill1/2│
        │8081/8082 │   │8083/8084 │   │8085/8086 │
        └───────┬──┘   └───┬──┬───┘   └───┬──┬───┘
                │          │  │           │  │
         ┌──────▼──────────▼──┼───────────▼──┤
         │    MySQL Master    │              │
         │    :3306 (写)      │          Kafka:9092
         └──────┬─────────────┘        (KRaft 模式)
                │ binlog
         ┌──────▼──────────────┐
         │    MySQL Slave       │
         │    :3307 (读)        │
         └─────────────────────┘

         ┌─────────────────────────────┐
         │     Redis :6379              │  ← 缓存 / 库存 / 幂等
         └─────────────────────────────┘
```

## 微服务说明

| 服务 | 端口 | 描述 |
|------|------|------|
| user-service (backend1) | 8081 | 用户注册、登录、JWT 鉴权 |
| user-service (backend2) | 8082 | 用户服务副本（负载均衡） |
| product-service (product1) | 8083 | 商品 CRUD，读写分离，Redis 缓存 |
| product-service (product2) | 8084 | 商品服务副本（负载均衡） |
| seckill-service (seckill1) | 8085 | 秒杀核心（WORKER_ID=0） |
| seckill-service (seckill2) | 8086 | 秒杀服务副本（WORKER_ID=1） |
| Nginx | 80 | 反向代理 + 负载均衡 + 静态资源 |
| MySQL Master | 3306 | 主库（写） |
| MySQL Slave | 3307 | 从库（读） |
| Redis | 6379 | 缓存 / 分布式锁 / 库存 |
| Kafka | 9092 | 消息队列（KRaft 模式，无 ZooKeeper） |

## 核心特性

### 秒杀防超卖链路
1. **Redis 幂等检查**：`SETNX seckill:done:{userId}:{productId}` 防止重复下单
2. **Lua 原子扣库存**：`GET + DECR` 原子操作，彻底防止 Redis 层超卖
3. **Kafka 削峰**：接口立即返回 202，消费者异步落库
4. **DB 兜底**：`UPDATE seckill_stocks SET stock=stock-1 WHERE stock>=1` 防止 Redis 与 DB 不一致导致超卖
5. **DB 幂等兜底**：`UNIQUE KEY uk_user_product(user_id, product_id)`

### 分库分表（ShardingSphere-JDBC 5.4.1）
- 分库规则：`id % 2` → `flashmall_0`（偶数）/ `flashmall_1`（奇数）
- 分表规则：`id % 4` → `seckill_orders_0` ~ `seckill_orders_3`
- **基因算法**：雪花 ID 最低位嵌入 `userId % 2`，保证 `id % 2 = userId % 2`，精准路由无广播

### Redis 缓存防护
- **缓存穿透**：null sentinel `"__NULL__"` TTL 60s
- **缓存击穿**：Redisson 分布式锁 + double-check
- **缓存雪崩**：基础 TTL 30min + 随机抖动 0~10min

### MySQL 读写分离
- 写操作：`@DS("master")` → mysql-master:3306
- 读操作：`@DS("slave")` → mysql-slave:3306
- 实现框架：dynamic-datasource-spring-boot3-starter 4.3.1

## 快速启动

### 前置要求
- Docker Desktop
- Java 17 + Maven 3.8+

### 构建并启动
```bash
# 构建所有服务
mvn clean package -DskipTests

# 启动所有容器
docker-compose up -d --build

# 查看服务状态
docker-compose ps
```

### 库存预热（秒杀前必须执行）
```bash
curl -X POST http://localhost/api/seckill/admin/stock/warm/all
```

## Nginx 负载均衡策略

在 `docker-compose.yml` 中修改 nginx volumes 挂载的配置文件：

| 文件 | 策略 |
|------|------|
| `nginx/upstreams_rr.conf` | 轮询（默认） |
| `nginx/upstreams_least.conf` | 最少连接 |
| `nginx/upstreams_iphash.conf` | IP Hash（会话保持） |

## JMeter 压测

```powershell
# Windows PowerShell
.\load\run_jmeter.ps1
```

测试计划位置：`load/flash_mall_test.jmx`

## 目录结构

```
flash-mall/
├── flash-mall-user-service/     # 用户服务
├── flash-mall-product-service/  # 商品服务（读写分离 + Redis 缓存）
├── flash-mall-seckill-service/  # 秒杀服务（Kafka + ShardingSphere）
│   └── src/main/resources/
│       ├── application.yml
│       ├── shardingsphere.yaml  # 分库分表配置
│       ├── schema.sql           # flashmall 库表结构
│       └── schema_shard.sql     # flashmall_0/1 分片表结构
├── nginx/                       # Nginx 配置 + 负载均衡策略
│   ├── nginx.conf
│   ├── upstreams_rr.conf
│   ├── upstreams_least.conf
│   └── upstreams_iphash.conf
├── load/                        # JMeter 压测脚本
│   ├── flash_mall_test.jmx
│   └── run_jmeter.ps1
├── docs/                        # 文档
│   ├── API.md
│   ├── database.md
│   └── technology_stack.md
└── docker-compose.yml
```

## API 快速参考

| 功能 | 方法 | 路径 |
|------|------|------|
| 用户注册 | POST | `/api/users/register` |
| 用户登录 | POST | `/api/users/login` |
| 商品列表 | GET | `/api/products` |
| 商品详情 | GET | `/api/products/{id}` |
| 创建商品 | POST | `/api/products` |
| 秒杀下单 | POST | `/api/seckill/{productId}` |
| 查询订单 | GET | `/api/seckill/orders/{orderId}` |
| 用户订单列表 | GET | `/api/seckill/orders/user/{userId}` |
| 库存预热（单个） | POST | `/api/seckill/admin/stock/warm/{productId}` |
| 库存预热（全部） | POST | `/api/seckill/admin/stock/warm/all` |

详细 API 文档见 [docs/API.md](docs/API.md)

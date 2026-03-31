# Flash Mall 技术栈文档

## 1. 编程语言与运行时

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 (LTS) | 主要开发语言 |
| Eclipse Temurin JRE | 17-alpine | Docker 容器运行时镜像 |

---

## 2. 核心框架

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.4 | 微服务基础框架，自动配置、内嵌 Tomcat |
| Spring Web MVC | 随 Boot | REST API 层 |
| Spring Kafka | 随 Boot | Kafka 生产者/消费者集成 |
| MyBatis Plus | 3.5.5 | ORM 框架，简化 CRUD，支持动态 SQL |
| dynamic-datasource-spring-boot3-starter | 4.3.1 | 多数据源路由（@DS 注解读写分离） |
| Lombok | 1.18.x | 消除 Java 样板代码 |

---

## 3. 数据库

| 技术 | 版本 | 用途 |
|------|------|------|
| MySQL | 8.0 (bitnami) | 关系型数据库，存储用户、商品、秒杀数据 |
| MySQL 主从复制 | bitnami 自动配置 | Master(3306) 写，Slave(3307) 读，基于 binlog |
| ShardingSphere-JDBC | 5.4.1 | 秒杀订单分库分表（2库 × 4表 = 8张物理表） |

**ShardingSphere 分片规则**：
- 分库：`id % 2` → `flashmall_0` / `flashmall_1`
- 分表：`id % 4` → `seckill_orders_{0..3}`
- 驱动：`org.apache.shardingsphere.driver.ShardingSphereDriver`

---

## 4. 缓存

| 技术 | 版本 | 用途 |
|------|------|------|
| Redis | 7-alpine | 商品缓存、秒杀库存、分布式锁、幂等标记 |
| Redisson | 3.27.0 | Redis 高级客户端：分布式锁、RBucket、Lua 脚本执行 |

**Redis 使用场景**：
- 商品缓存（TTL 30~40min，随机抖动防雪崩）
- 缓存穿透：null sentinel `"__NULL__"`（TTL 60s）
- 缓存击穿：Redisson 分布式锁 + double-check
- 秒杀库存：Lua 原子 `GET+DECR`，防超卖
- 幂等标记：`SETNX seckill:done:{userId}:{productId}`（TTL 1h）

---

## 5. 消息队列

| 技术 | 版本 | 用途 |
|------|------|------|
| Apache Kafka | 3.7 (bitnami, KRaft) | 秒杀订单削峰填谷 |
| Spring Kafka | 随 Boot 3.2.4 | @KafkaListener 消费、KafkaTemplate 生产 |

**特点**：
- KRaft 模式（无 ZooKeeper），简化部署
- 生产者：key=userId，保证同一用户消息有序
- 消费者：@Transactional + DuplicateKeyException 幂等处理

---

## 6. 负载均衡与反向代理

| 技术 | 版本 | 用途 |
|------|------|------|
| Nginx | latest (Docker) | 反向代理、负载均衡、静态资源服务 |

**支持的负载均衡策略**（切换配置文件）：
- `upstreams_rr.conf` — 轮询（Round Robin，默认）
- `upstreams_least.conf` — 最少连接（Least Connections）
- `upstreams_iphash.conf` — IP Hash（会话保持）

**路由规则**：
- `/api/seckill/` → seckill1:8085 / seckill2:8086
- `/api/products/` → product1:8083 / product2:8084
- `/api/` → backend1:8081 / backend2:8082
- `/static/` → 静态文件（Cache-Control: max-age=604800）

---

## 7. 容器化与编排

| 技术 | 版本 | 用途 |
|------|------|------|
| Docker | 最新版 | 容器化各微服务 |
| Docker Compose | v2 | 多容器编排、服务依赖管理 |

**服务依赖链**：
```
mysql-slave → mysql-master (healthy)
product1/2 → mysql-master (healthy) + mysql-slave (healthy) + redis (started)
seckill1/2 → mysql-master (healthy) + redis (started) + kafka (healthy)
nginx → backend1/2 + product1/2 + seckill1/2
```

---

## 8. 分布式 ID 生成

| 技术 | 用途 |
|------|------|
| 雪花算法（自实现 SnowflakeIdGenerator） | 生成全局唯一订单 ID |

**基因算法**：最低 1 位嵌入 `userId & 1`：
```
[41位时间戳 | 10位workerId | 11位序列号 | 1位基因]
```
保证 `orderId % 2 = userId % 2`，ShardingSphere 可按 id 精准路由到正确分库，无需广播查询。

---

## 9. 压测工具

| 技术 | 版本 | 用途 |
|------|------|------|
| Apache JMeter | 5.5 | 接口压力测试、高并发场景模拟 |

测试计划：`load/flash_mall_test.jmx`，PowerShell 启动脚本：`load/run_jmeter.ps1`

---

## 10. 构建工具

| 技术 | 版本 | 用途 |
|------|------|------|
| Maven | 3.8+ | 多模块项目构建、依赖管理 |
| maven-compiler-plugin | 3.x | 指定 Java 17 编译目标 |
| spring-boot-maven-plugin | 随 Boot | 打包可执行 Fat JAR |

---

## 技术选型对比

| 场景 | 选型 | 原因 |
|------|------|------|
| 主从复制 | bitnami/mysql | 通过环境变量自动配置主从，无需手动执行 CHANGE MASTER TO |
| 分布式锁 | Redisson | 比 Jedis 封装更高层，内置 Lua 脚本保证原子性 |
| 消息队列 | Kafka KRaft | 无需 ZooKeeper，简化 Docker Compose 部署 |
| 分库分表 | ShardingSphere-JDBC | 应用层透明路由，无需修改 SQL，支持基因算法精准路由 |
| 多数据源 | dynamic-datasource | Spring Boot 3 兼容，@DS 注解简洁，支持事务 |

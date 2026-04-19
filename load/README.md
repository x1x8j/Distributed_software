# Load testing with JMeter

本目录用于验证 Gateway 接入后的流量治理：限流、熔断、降级。

文件说明：
- `jmeter_test_plan.jmx`：网关健康接口基础压测脚本（`GET /api/users/health`）
- `run_jmeter.ps1`：Docker 方式执行 JMeter 脚本
- `results.jtl`：压测结果输出

## 快速开始

```powershell
# 1) 构建
mvn clean package -DskipTests

# 2) 启动核心链路（含 nacos + gateway + sentinel）
docker compose up -d --build mysql-master mysql-slave redis kafka nacos sentinel-dashboard backend1 backend2 product1 product2 seckill1 seckill2 order inventory gateway nginx

# 3) 基线压测（走 Nginx/Gateway）
.\load\run_jmeter.ps1 -Target http://host.docker.internal -TargetPort 80 -Threads 80 -Ramp 15 -Loops 200
```

## 限流验证（秒杀接口）

可在 JMeter 中新增 `POST /api/seckill/1` + Header `X-User-Id`，并发拉高后观察：
- 部分请求返回 `429`（Gateway `RequestRateLimiter` 生效）
- 正常请求仍可返回 `202`

## 熔断降级验证（秒杀链路）

人工停掉秒杀服务后访问：
```bash
docker compose stop seckill1 seckill2
curl -i http://localhost/api/seckill/health
```
预期：
- 返回 `503`
- 响应体包含 `秒杀服务繁忙，请稍后重试`

## Sentinel 验证（支付接口）

- Sentinel Dashboard: `http://localhost:8858`（默认账号密码：`sentinel/sentinel`）
- 资源名：`orderPayResource`
- 对 `orderPayResource` 下发 QPS 规则后高频调用 `/api/orders/{orderId}/pay`
- 预期：超限请求返回 `429` + `支付请求过于频繁，请稍后重试`

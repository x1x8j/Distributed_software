# Flash Mall API 文档

统一入口：`http://localhost`（Nginx 80 端口）

---

## 1) 用户服务 API

Base URL: `/api/users`

### 注册
- `POST /api/users/register`
- Body:
```json
{
  "username": "alice",
  "password": "123456",
  "email": "alice@example.com",
  "phone": "13800138000"
}
```
- 成功：`200`，返回创建后的 `User` 对象。

### 登录
- `POST /api/users/login`
- Body:
```json
{
  "username": "alice",
  "password": "123456"
}
```
- 成功：`200`，返回 `User` 对象（当前实现未发 JWT）。
- 失败：`401`。

### 健康检查
- `GET /api/users/health`

---

## 2) 商品服务 API

Base URL: `/api/products`

> 当前实现仅提供“按ID查询 + 创建商品”。

### 查询商品详情
- `GET /api/products/{id}`
- 成功：`200`，返回 `Product`
- 不存在：`404`

### 创建商品
- `POST /api/products`
- Body:
```json
{
  "name": "Test Product",
  "description": "demo",
  "price": 99.9,
  "stock": 10
}
```
- 成功：`200`，返回新商品。

### 健康检查
- `GET /api/products/health`

---

## 3) 秒杀服务 API

Base URL: `/api/seckill`

### 秒杀下单
- `POST /api/seckill/{productId}`
- Header: `X-User-Id: {userId}`

示例：
```bash
curl -X POST http://localhost/api/seckill/1 -H "X-User-Id: 42"
```

成功：`202 Accepted`
```json
{
  "orderId": 123456789012345678,
  "message": "秒杀成功，订单处理中",
  "queryUrl": "/api/orders/123456789012345678"
}
```

业务冲突（重复秒杀/售罄/未预热）：`409`
```json
{
  "error": "您已参与过该商品的秒杀"
}
```

### 预热单个商品库存
- `POST /api/seckill/admin/stock/warm/{productId}`

### 预热全部库存
- `POST /api/seckill/admin/stock/warm/all`

### 健康检查
- `GET /api/seckill/health`

---

## 4) 订单服务 API

Base URL: `/api/orders`

### 查询订单
- `GET /api/orders/{orderId}`
- 成功：`200`，返回 `Order`
- 不存在：`404`

订单状态枚举（`status`）：
- `0` = 待确认库存
- `1` = 待支付
- `2` = 已支付
- `3` = 库存不足已取消
- `4` = 支付失败

### 查询用户订单列表
- `GET /api/orders/user/{userId}`

### 模拟支付
- `POST /api/orders/{orderId}/pay`
- Header: `X-User-Id: {userId}`

成功：`202`
```json
{
  "message": "支付请求已提交，正在处理中",
  "orderId": 123456789012345678
}
```

失败：`400`
```json
{
  "message": "订单当前状态不允许支付，status=3"
}
```

### 健康检查
- `GET /api/orders/health`

---

## 5) 库存服务 API

Base URL: `/api/inventory`

### 查询库存
- `GET /api/inventory/{productId}`

成功：`200`
```json
{
  "productId": 1,
  "stock": 99
}
```

不存在：`404`

### 健康检查
- `GET /api/inventory/health`

---

## 6) 演示链路

```bash
# 1. 健康检查
curl http://localhost/api/seckill/health
curl http://localhost/api/orders/health
curl http://localhost/api/inventory/health

# 2. 预热库存
curl -X POST http://localhost/api/seckill/admin/stock/warm/all

# 3. 秒杀
curl -X POST http://localhost/api/seckill/1 -H "X-User-Id: 42"

# 4. 查订单
curl http://localhost/api/orders/{orderId}

# 5. 查库存
curl http://localhost/api/inventory/1

# 6. 支付并再次查订单
curl -X POST http://localhost/api/orders/{orderId}/pay -H "X-User-Id: 42"
curl http://localhost/api/orders/{orderId}
```

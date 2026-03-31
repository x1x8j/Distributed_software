# Flash Mall API 文档

所有接口均通过 Nginx (port 80) 统一入口访问。

---

## 1. 用户服务 API

Base URL: `/api/users`（路由到 backend1:8081 / backend2:8082）

### 注册用户

- **方法**: `POST`
- **路径**: `/api/users/register`
- **请求体**:

  ```json
  {
    "username": "alice",
    "password": "password123",
    "email": "alice@example.com",
    "phone": "13800138000"
  }
  ```

- **响应**:

  成功 (200):
  ```json
  {
    "message": "User registered successfully",
    "userId": 1
  }
  ```

  失败 (400):
  ```json
  {
    "message": "Error: Username already exists"
  }
  ```

### 用户登录

- **方法**: `POST`
- **路径**: `/api/users/login`
- **请求体**:

  ```json
  {
    "username": "alice",
    "password": "password123"
  }
  ```

- **响应**:

  成功 (200):
  ```json
  {
    "token": "<jwt_token>"
  }
  ```

  失败 (401):
  ```json
  {
    "message": "Invalid username or password"
  }
  ```

### 获取用户信息

- **方法**: `GET`
- **路径**: `/api/users/{userId}`
- **响应**:

  成功 (200):
  ```json
  {
    "userId": 1,
    "username": "alice",
    "email": "alice@example.com",
    "phone": "13800138000",
    "createdAt": "2024-01-01T00:00:00"
  }
  ```

---

## 2. 商品服务 API

Base URL: `/api/products`（路由到 product1:8083 / product2:8084）

商品数据由 Redis 缓存（TTL 30~40min），读操作走 MySQL Slave，写操作走 MySQL Master。

### 获取商品列表

- **方法**: `GET`
- **路径**: `/api/products`
- **响应**:

  ```json
  [
    {
      "id": 1,
      "name": "秒杀商品A",
      "description": "高并发秒杀测试商品",
      "price": 9.90,
      "stock": 100,
      "createdAt": "2024-01-01T00:00:00"
    }
  ]
  ```

### 获取单个商品

- **方法**: `GET`
- **路径**: `/api/products/{id}`
- **说明**: 优先读 Redis 缓存；缓存未命中时加分布式锁回源 MySQL Slave
- **响应**:

  ```json
  {
    "id": 1,
    "name": "秒杀商品A",
    "description": "高并发秒杀测试商品",
    "price": 9.90,
    "stock": 100,
    "createdAt": "2024-01-01T00:00:00"
  }
  ```

  商品不存在 (404):
  ```json
  {
    "message": "Product not found"
  }
  ```

### 创建商品

- **方法**: `POST`
- **路径**: `/api/products`
- **请求体**:

  ```json
  {
    "name": "新商品",
    "description": "商品描述",
    "price": 99.00,
    "stock": 500
  }
  ```

- **响应**:

  ```json
  {
    "id": 10,
    "name": "新商品",
    "description": "商品描述",
    "price": 99.00,
    "stock": 500,
    "createdAt": "2024-06-01T12:00:00"
  }
  ```

### 更新商品

- **方法**: `PUT`
- **路径**: `/api/products/{id}`
- **请求体**:

  ```json
  {
    "name": "Updated Name",
    "description": "Updated description",
    "price": 150.00,
    "stock": 45
  }
  ```

- **响应**: `200 OK`

### 删除商品

- **方法**: `DELETE`
- **路径**: `/api/products/{id}`
- **响应**: `200 OK`

---

## 3. 秒杀服务 API

Base URL: `/api/seckill`（路由到 seckill1:8085 / seckill2:8086）

秒杀流程：Redis 幂等检查 → Lua 原子扣库存 → Kafka 异步落库

### 秒杀下单

- **方法**: `POST`
- **路径**: `/api/seckill/{productId}`
- **请求头**: `X-User-Id: {userId}`

  ```
  POST /api/seckill/1001
  X-User-Id: 42
  ```

- **响应**:

  成功 (202 Accepted):
  ```json
  {
    "orderId": 4611686018427387904,
    "message": "秒杀成功，订单处理中"
  }
  ```

  已参与过 (400):
  ```json
  {
    "message": "您已参与过该商品的秒杀"
  }
  ```

  售罄 (400):
  ```json
  {
    "message": "很遗憾，商品已售罄"
  }
  ```

  未预热 (400):
  ```json
  {
    "message": "秒杀活动尚未开始，库存未预热"
  }
  ```

> **说明**: orderId 为基因雪花 ID，满足 `orderId % 2 = userId % 2`，由 ShardingSphere 精准路由到对应分库。

### 查询订单

- **方法**: `GET`
- **路径**: `/api/seckill/orders/{orderId}`
- **响应**:

  ```json
  {
    "id": 4611686018427387904,
    "userId": 42,
    "productId": 1001,
    "quantity": 1,
    "status": 1,
    "createdAt": "2024-06-01T12:00:01"
  }
  ```

  status 含义：`0` = 处理中，`1` = 成功，`2` = 库存不足

### 查询用户所有订单

- **方法**: `GET`
- **路径**: `/api/seckill/orders/user/{userId}`
- **响应**:

  ```json
  [
    {
      "id": 4611686018427387904,
      "userId": 42,
      "productId": 1001,
      "quantity": 1,
      "status": 1,
      "createdAt": "2024-06-01T12:00:01"
    }
  ]
  ```

### 预热指定商品库存

- **方法**: `POST`
- **路径**: `/api/seckill/admin/stock/warm/{productId}`
- **说明**: 将 seckill_stocks 表中的库存值写入 Redis key `seckill:stock:{productId}`
- **响应**: `200 OK`

### 预热全部商品库存

- **方法**: `POST`
- **路径**: `/api/seckill/admin/stock/warm/all`
- **说明**: 批量预热所有秒杀商品，服务启动时也会自动执行
- **响应**: `200 OK`

### 健康检查

- **方法**: `GET`
- **路径**: `/api/seckill/health`
- **响应**: `"OK"`

---

## 错误响应格式

```json
{
  "message": "错误描述信息"
}
```

| HTTP 状态码 | 含义 |
|------------|------|
| 200 | 成功 |
| 202 | 已接受（秒杀异步处理中） |
| 400 | 业务错误（参数错误、售罄、重复秒杀等） |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

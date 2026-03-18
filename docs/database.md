### 1. **用户表（user）**

| 字段名        | 数据类型         | 描述           |
| ---------- | ------------ | ------------ |
| user_id    | INT          | 主键，自动增长，用户ID |
| username   | VARCHAR(255) | 用户名（唯一）      |
| password   | VARCHAR(255) | 密码           |
| email      | VARCHAR(255) | 邮箱           |
| phone      | VARCHAR(20)  | 手机号          |
| created_at | DATETIME     | 用户创建时间       |
| updated_at | DATETIME     | 用户更新时间       |

### 2. **商品表（product）**

| 字段名         | 数据类型          | 描述           |
| ----------- | ------------- | ------------ |
| product_id  | INT           | 主键，自动增长，商品ID |
| name        | VARCHAR(255)  | 商品名称         |
| description | TEXT          | 商品描述         |
| price       | DECIMAL(10,2) | 商品价格         |
| created_at  | DATETIME      | 商品创建时间       |
| updated_at  | DATETIME      | 商品更新时间       |

### 3. **库存表（inventory）**

| 字段名          | 数据类型     | 描述            |
| ------------ | -------- | ------------- |
| inventory_id | INT      | 主键，自动增长，库存ID  |
| product_id   | INT      | 外键，关联商品表的商品ID |
| stock        | INT      | 商品库存数量        |
| updated_at   | DATETIME | 库存更新时间        |

### 4. **订单表（order）**

| 字段名          | 数据类型          | 描述                                  |
| ------------ | ------------- | ----------------------------------- |
| order_id     | INT           | 主键，自动增长，订单ID                        |
| user_id      | INT           | 外键，关联用户表的用户ID                       |
| total_amount | DECIMAL(10,2) | 订单总金额                               |
| status       | VARCHAR(50)   | 订单状态（如：pending, shipped, delivered） |
| created_at   | DATETIME      | 订单创建时间                              |
| updated_at   | DATETIME      | 订单更新时间                              |

### 表之间的关系：

1. **用户表（user）与订单表（order）**：

    * 一个用户可以有多个订单，`user_id` 在订单表中作为外键，表示每个订单属于某个用户。

2. **商品表（product）与库存表（inventory）**：

    * 每个商品有对应的库存记录，`product_id` 在库存表中作为外键，表示每个库存记录对应一个商品。

3. **订单表（order）与商品表（product）**：

    * 订单和商品之间的关系是多对多的，通过订单明细表（`order_item`）来连接订单和商品。

### 5. **订单明细表（order_item）**

| 字段名           | 数据类型          | 描述             |
| ------------- | ------------- | -------------- |
| order_item_id | INT           | 主键，自动增长，订单明细ID |
| order_id      | INT           | 外键，关联订单表的订单ID  |
| product_id    | INT           | 外键，关联商品表的商品ID  |
| quantity      | INT           | 购买数量           |
| price         | DECIMAL(10,2) | 商品购买单价         |


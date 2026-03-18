### 1. **用户服务 API**

#### 注册用户

* **请求方法**: `POST`
* **URL**zhge: `/users/register`
* **请求体**:

  ```json
  {
    "username": "string",
    "password": "string",
    "email": "string",
    "phone": "string"
  }
  ```
* **响应**:

    * 成功:

      ```json
      {
        "message": "User registered successfully",
        "userId": "123"
      }
      ```
    * 失败:

      ```json
      {
        "message": "Error: Username already exists"
      }
      ```

#### 用户登录

* **请求方法**: `POST`
* **URL**: `/users/login`
* **请求体**:

  ```json
  {
    "username": "string",
    "password": "string"
  }
  ```
* **响应**:

    * 成功:

      ```json
      {
        "token": "jwt_token"
      }
      ```
    * 失败:

      ```json
      {
        "message": "Invalid username or password"
      }
      ```

#### 获取用户信息

* **请求方法**: `GET`
* **URL**: `/users/{userId}`
* **响应**:

    * 成功:

      ```json
      {
        "userId": "123",
        "username": "string",
        "email": "string",
        "phone": "string"
      }
      ```

### 2. **商品服务 API**

#### 获取商品列表

* **请求方法**: `GET`
* **URL**: `/products`
* **请求参数**:

    * `page`: 页码
    * `size`: 每页显示的商品数量
* **响应**:

  ```json
  {
    "products": [
      {
        "productId": "1",
        "name": "Product Name",
        "price": "100.00",
        "description": "Product description",
        "stock": 50
      },
      {
        "productId": "2",
        "name": "Product Name 2",
        "price": "200.00",
        "description": "Another product description",
        "stock": 30
      }
    ],
    "total": 100
  }
  ```

#### 获取单个商品

* **请求方法**: `GET`
* **URL**: `/products/{productId}`
* **响应**:

  ```json
  {
    "productId": "1",
    "name": "Product Name",
    "price": "100.00",
    "description": "Product description",
    "stock": 50
  }
  ```

#### 添加商品

* **请求方法**: `POST`
* **URL**: `/products`
* **请求体**:

  ```json
  {
    "name": "string",
    "price": "decimal",
    "description": "string",
    "stock": "integer"
  }
  ```
* **响应**:

    * 成功:

      ```json
      {
        "message": "Product added successfully",
        "productId": "1"
      }
      ```

#### 更新商品

* **请求方法**: `PUT`
* **URL**: `/products/{productId}`
* **请求体**:

  ```json
  {
    "name": "Updated product name",
    "price": "150.00",
    "description": "Updated description",
    "stock": 45
  }
  ```
* **响应**:

    * 成功:

      ```json
      {
        "message": "Product updated successfully"
      }
      ```

#### 删除商品

* **请求方法**: `DELETE`
* **URL**: `/products/{productId}`
* **响应**:

    * 成功:

      ```json
      {
        "message": "Product deleted successfully"
      }
      ```

### 3. **订单服务 API**

#### 创建订单

* **请求方法**: `POST`
* **URL**: `/orders`
* **请求体**:

  ```json
  {
    "userId": "123",
    "productId": "1",
    "quantity": 2,
    "totalAmount": 200.00
  }
  ```
* **响应**:

    * 成功:

      ```json
      {
        "message": "Order created successfully",
        "orderId": "1001"
      }
      ```

#### 获取订单详情

* **请求方法**: `GET`
* **URL**: `/orders/{orderId}`
* **响应**:

  ```json
  {
    "orderId": "1001",
    "userId": "123",
    "productId": "1",
    "quantity": 2,
    "totalAmount": 200.00,
    "status": "Pending"
  }
  ```

#### 更新订单状态

* **请求方法**: `PUT`
* **URL**: `/orders/{orderId}/status`
* **请求体**:

  ```json
  {
    "status": "Shipped"
  }
  ```
* **响应**:

    * 成功:

      ```json
      {
        "message": "Order status updated to Shipped"
      }
      ```

### 4. **库存服务 API**

#### 获取商品库存

* **请求方法**: `GET`
* **URL**: `/inventory/{productId}`
* **响应**:

  ```json
  {
    "productId": "1",
    "stock": 50
  }
  ```

#### 更新商品库存

* **请求方法**: `POST`
* **URL**: `/inventory/update`
* **请求体**:

  ```json
  {
    "productId": "1",
    "quantity": 10
  }
  ```
* **响应**:

    * 成功:

      ```json
      {
        "message": "Inventory updated successfully"
      }
      ```

#### 扣减库存

* **请求方法**: `POST`
* **URL**: `/inventory/deduct`
* **请求体**:

  ```json
  {
    "productId": "1",
    "quantity": 2
  }
  ```
* **响应**:

    * 成功:

      ```json
      {
        "message": "Inventory deducted successfully"
      }
      ```

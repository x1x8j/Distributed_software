package com.flashmall.product.service;

import com.flashmall.product.entity.Product;

public interface ProductService {
    /** 读操作：走从库 */
    Product getById(Long id);

    /** 写操作：走主库，并清除对应缓存 */
    Product create(Product product);
}

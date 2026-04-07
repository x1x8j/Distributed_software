package com.flashmall.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Select("SELECT stock FROM inventory WHERE product_id = #{productId}")
    Integer selectStock(@Param("productId") Long productId);

    /**
     * 扣减库存，stock >= qty 保证不超卖
     * MyBatis-Plus @Version 乐观锁会自动在 UPDATE 里加 version 条件
     * 这里用原生 SQL 直接带 WHERE stock >= qty，效果等价且更直观
     */
    @Update("UPDATE inventory SET stock = stock - #{qty} " +
            "WHERE product_id = #{productId} AND stock >= #{qty}")
    int deductStock(@Param("productId") Long productId, @Param("qty") int qty);
}

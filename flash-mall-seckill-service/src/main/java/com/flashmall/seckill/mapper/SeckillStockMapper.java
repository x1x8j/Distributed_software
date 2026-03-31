package com.flashmall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.seckill.entity.SeckillStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillStockMapper extends BaseMapper<SeckillStock> {

    /**
     * 原子扣减 DB 库存（stock > 0 才扣），返回影响行数。
     * 影响行数 = 0 说明库存已耗尽（超卖保护）。
     */
    @Update("UPDATE seckill_stocks SET stock = stock - #{qty} " +
            "WHERE product_id = #{productId} AND stock >= #{qty}")
    int deductStock(@Param("productId") Long productId, @Param("qty") int qty);
}

package com.flashmall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    /**
     * 更新订单状态（Kafka 消费后调用）
     */
    @Update("UPDATE seckill_orders SET status = #{status} WHERE id = #{orderId}")
    int updateStatus(@Param("orderId") Long orderId, @Param("status") byte status);
}

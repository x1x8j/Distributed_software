package com.flashmall.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("seckill_orders")
public class SeckillOrder implements Serializable {

    /** 雪花算法生成的订单 ID，不使用 AUTO_INCREMENT */
    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;
    private Long productId;
    private Integer quantity;

    /** 0=处理中  1=成功  2=库存不足 */
    private Byte status;

    private LocalDateTime createdAt;
}

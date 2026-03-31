package com.flashmall.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("seckill_stocks")
public class SeckillStock implements Serializable {

    @TableId
    private Long productId;
    private Integer stock;
}

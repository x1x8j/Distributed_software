package com.flashmall.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inventory")
public class Inventory {

    @TableId
    private Long productId;

    private Integer stock;

    /** MyBatis-Plus 乐观锁，防并发超卖兜底 */
    @Version
    private Integer version;

    private LocalDateTime updatedAt;
}

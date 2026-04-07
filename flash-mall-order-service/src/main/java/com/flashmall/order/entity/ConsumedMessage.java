package com.flashmall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("consumed_messages")
public class ConsumedMessage {

    @TableId
    private String messageId;

    private LocalDateTime consumedAt;
}

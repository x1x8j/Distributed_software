package com.flashmall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.seckill.entity.ConsumedMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConsumedMessageMapper extends BaseMapper<ConsumedMessage> {

    /**
     * 检查消息是否已被消费过（幂等查询）
     */
    @Select("SELECT COUNT(1) FROM consumed_messages WHERE message_id = #{messageId}")
    int existsByMessageId(String messageId);
}

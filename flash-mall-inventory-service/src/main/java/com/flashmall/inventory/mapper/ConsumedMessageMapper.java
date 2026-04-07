package com.flashmall.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.inventory.entity.ConsumedMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConsumedMessageMapper extends BaseMapper<ConsumedMessage> {

    @Select("SELECT COUNT(1) FROM consumed_messages WHERE message_id = #{messageId}")
    int existsByMessageId(String messageId);
}

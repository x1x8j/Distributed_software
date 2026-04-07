package com.flashmall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.seckill.entity.OutboxMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessage> {

    /**
     * 查询待投递的消息（PENDING），按创建时间升序，限制批量大小防止一次拉取过多
     */
    @Select("SELECT * FROM outbox_messages WHERE status = 0 ORDER BY created_at LIMIT #{limit}")
    List<OutboxMessage> selectPending(@Param("limit") int limit);

    /**
     * 标记为已发送
     */
    @Update("UPDATE outbox_messages SET status = 1, sent_at = NOW() WHERE id = #{id}")
    int markSent(@Param("id") Long id);

    /**
     * 重试次数 +1；若超过最大重试次数则标记 FAILED
     */
    @Update("UPDATE outbox_messages SET retry_count = retry_count + 1, " +
            "status = IF(retry_count + 1 >= #{maxRetry}, 2, 0) WHERE id = #{id}")
    int incrementRetry(@Param("id") Long id, @Param("maxRetry") int maxRetry);
}

package org.mingharness.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
    List<Conversation> findTop50ByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, String userId);
    Optional<Conversation> findByIdAndTenantIdAndUserId(String id, String tenantId, String userId);

    /** 同一会话发送消息时串行化序号分配和上下文快照，避免并发轮次交叉。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from Conversation conversation where conversation.id = :id "
            + "and conversation.tenantId = :tenantId and conversation.userId = :userId")
    Optional<Conversation> findByIdAndTenantIdAndUserIdForUpdate(
            @Param("id") String id, @Param("tenantId") String tenantId, @Param("userId") String userId);
}
